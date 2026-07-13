package cn.alini.trueuuid.protocol;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** HTTPS client which pins the reviewed DNS result, refuses redirects and bounds bodies. */
public final class SafeSessionHttpClient {
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(5).toMillis();
    private static final int READ_TIMEOUT_MS = (int) Duration.ofSeconds(5).toMillis();

    public record Response(int status, String body) {}

    public Response getTrusted(URI uri) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0 || addresses.length > EndpointPolicy.MAX_RESOLVED_ADDRESSES
                || Arrays.stream(addresses).anyMatch(address -> !EndpointPolicy.isPublic(address))) {
            throw new IOException("trusted session endpoint did not resolve only to public addresses");
        }
        return get(uri, List.of(addresses));
    }

    public Response get(URI uri, List<InetAddress> approvedAddresses) throws IOException {
        IOException last = null;
        for (InetAddress address : approvedAddresses) {
            try { return getPinned(uri, address); }
            catch (IOException ex) { last = ex; }
        }
        throw last == null ? new IOException("session endpoint has no approved address") : last;
    }

    private Response getPinned(URI uri, InetAddress address) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("session endpoint must use HTTPS with a hostname");
        }
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        if (port != 443) throw new IOException("session endpoint must use HTTPS port 443");

        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(plain, uri.getHost(), port, true)) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();

            String rawPath = uri.getRawPath();
            String target = (rawPath == null || rawPath.isEmpty() ? "/" : rawPath)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            OutputStream output = socket.getOutputStream();
            output.write(("GET " + target + " HTTP/1.1\r\n"
                    + "Host: " + uri.getHost() + "\r\n"
                    + "Accept: application/json\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "User-Agent: TrueUUID/1.1\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return readResponse(socket.getInputStream());
        } catch (IOException failure) {
            try { plain.close(); } catch (IOException ignored) {}
            throw failure;
        }
    }

    private static Response readResponse(InputStream input) throws IOException {
        BufferedInputStream stream = new BufferedInputStream(input);
        String statusLine = readHeaderLine(stream);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) throw new IOException("invalid session HTTP response");
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) throw new IOException("invalid session HTTP status");
        int status;
        try { status = Integer.parseInt(parts[1]); }
        catch (NumberFormatException ex) { throw new IOException("invalid session HTTP status", ex); }
        if (status >= 300 && status < 400) throw new IOException("session endpoint redirects are forbidden");

        Map<String, String> headers = new HashMap<>();
        for (String line; (line = readHeaderLine(stream)) != null && !line.isEmpty();) {
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("invalid session HTTP header");
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        String contentLength = headers.get("content-length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > MAX_RESPONSE_BYTES) throw new IOException("session response is too large");
            } catch (NumberFormatException ex) { throw new IOException("invalid session content length", ex); }
        }
        String body = "chunked".equalsIgnoreCase(headers.get("transfer-encoding"))
                ? readChunked(stream) : readBounded(stream);
        return new Response(status, body);
    }

    private static String readChunked(InputStream stream) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (;;) {
                String line = readHeaderLine(stream);
                if (line == null) throw new IOException("truncated chunked session response");
                int semicolon = line.indexOf(';');
                long chunkSize;
                try { chunkSize = Long.parseLong((semicolon < 0 ? line : line.substring(0, semicolon)).trim(), 16); }
                catch (NumberFormatException ex) { throw new IOException("invalid session response chunk", ex); }
                if (chunkSize < 0 || chunkSize > MAX_RESPONSE_BYTES - output.size()) throw new IOException("session response is too large");
                if (chunkSize == 0) {
                    while ((line = readHeaderLine(stream)) != null && !line.isEmpty()) {}
                    return output.toString(StandardCharsets.UTF_8);
                }
                byte[] chunk = stream.readNBytes((int) chunkSize);
                if (chunk.length != chunkSize || stream.read() != '\r' || stream.read() != '\n') {
                    throw new IOException("truncated chunked session response");
                }
                output.write(chunk);
            }
        }
    }

    private static String readHeaderLine(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int next; (next = stream.read()) != -1;) {
            if (next == '\n') return output.toString(StandardCharsets.ISO_8859_1).replaceFirst("\\r$", "");
            if (output.size() >= 8192) throw new IOException("session HTTP header is too large");
            output.write(next);
        }
        return output.size() == 0 ? null : output.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readBounded(InputStream stream) throws IOException {
        try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = stream.read(buffer)) != -1;) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("session response is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

}
