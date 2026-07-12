package cn.alini.trueuuid.server;

import cn.alini.trueuuid.protocol.EndpointPolicy;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

final class SafeSessionHttpClient {
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(5).toMillis();
    private static final int READ_TIMEOUT_MS = (int) Duration.ofSeconds(5).toMillis();

    record Response(int status, String body) {}

    Response getTrusted(URI uri) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0 || addresses.length > EndpointPolicy.MAX_RESOLVED_ADDRESSES
                || Arrays.stream(addresses).anyMatch(a -> !EndpointPolicy.isPublic(a))) {
            throw new IOException("trusted session endpoint did not resolve only to public addresses");
        }
        return get(uri, List.of(addresses));
    }

    Response get(URI uri, List<InetAddress> approvedAddresses) throws IOException {
        IOException last = null;
        for (InetAddress address : approvedAddresses) {
            try { return getPinned(uri, address); }
            catch (IOException ex) { last = ex; }
        }
        throw last == null ? new IOException("session endpoint has no approved address") : last;
    }

    private Response getPinned(URI uri, InetAddress address) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(new PinnedSslSocketFactory(address, CONNECT_TIMEOUT_MS));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TrueUUID/1.1");
        try {
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new IOException("session endpoint redirects are forbidden");
            long declared = connection.getContentLengthLong();
            if (declared > MAX_RESPONSE_BYTES) throw new IOException("session response is too large");
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(status, stream == null ? "" : readBounded(stream));
        } finally {
            connection.disconnect();
        }
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

    private static final class PinnedSslSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        private final InetAddress address;
        private final int timeoutMs;

        private PinnedSslSocketFactory(InetAddress address, int timeoutMs) {
            this.address = address;
            this.timeoutMs = timeoutMs;
        }

        private Socket pinned(String tlsHost, int port, InetAddress local, int localPort) throws IOException {
            Socket plain = new Socket();
            if (local != null) plain.bind(new InetSocketAddress(local, localPort));
            plain.connect(new InetSocketAddress(address, port), timeoutMs);
            return delegate.createSocket(plain, tlsHost, port, true);
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { throw new IOException("unpinned TLS sockets are forbidden"); }
        @Override public Socket createSocket(String host, int port) throws IOException { return pinned(host, port, null, 0); }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return pinned(host, port, local, localPort); }
        @Override public Socket createSocket(InetAddress ignored, int port) throws IOException { throw new IOException("TLS hostname required"); }
        @Override public Socket createSocket(InetAddress ignored, int port, InetAddress local, int localPort) throws IOException { throw new IOException("TLS hostname required"); }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            if (socket != null) socket.close();
            return pinned(host, port, null, 0);
        }
    }
}
