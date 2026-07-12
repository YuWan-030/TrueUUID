package cn.alini.trueuuid.protocol;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class EndpointPolicy {
    public static final String HAS_JOINED_PATH_SUFFIX = "/sessionserver/session/minecraft/hasJoined";
    public static final int MAX_RESOLVED_ADDRESSES = 8;

    @FunctionalInterface public interface Resolver { InetAddress[] resolve(String host) throws UnknownHostException; }
    public record ApprovedEndpoint(URI uri, String host, List<InetAddress> addresses) {}

    private final List<String> allowedHosts;
    private final Resolver resolver;

    public EndpointPolicy(Collection<String> allowedHosts) {
        this(allowedHosts, InetAddress::getAllByName);
    }

    public EndpointPolicy(Collection<String> allowedHosts, Resolver resolver) {
        this.allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .map(EndpointPolicy::normalizeAllowEntry).filter(s -> !s.isEmpty()).distinct().toList();
        this.resolver = resolver;
    }

    public ApprovedEndpoint approveClientEndpoint(String raw) throws UnknownHostException {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("custom endpoint is empty");
        if (raw.length() > AuthMessages.MAX_ENDPOINT_CHARS) throw new IllegalArgumentException("custom endpoint is too long");
        URI uri;
        try { uri = URI.create(raw); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("custom endpoint is not a valid URI", ex); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("custom endpoint must use HTTPS");
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("custom endpoint cannot contain user-info, query, or fragment");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("custom endpoint must use port 443");
        String host = normalizeHost(uri.getHost());
        if (host.isEmpty() || isIpLiteral(host)) throw new IllegalArgumentException("custom endpoint requires a DNS hostname");
        if (!isAllowed(host)) throw new IllegalArgumentException("custom endpoint host is not allowlisted");
        String path = uri.getRawPath();
        if (path == null || path.contains("%") || !path.endsWith(HAS_JOINED_PATH_SUFFIX)
                || path.contains("//") || path.contains("/../") || path.contains("/./")) {
            throw new IllegalArgumentException("custom endpoint path is not an allowed hasJoined path");
        }
        InetAddress[] resolved = resolver.resolve(host);
        if (resolved.length == 0) throw new UnknownHostException(host);
        if (resolved.length > MAX_RESOLVED_ADDRESSES) throw new IllegalArgumentException("custom endpoint resolves to too many addresses");
        List<InetAddress> safe = new ArrayList<>(resolved.length);
        for (InetAddress address : resolved) {
            if (!isPublic(address)) throw new IllegalArgumentException("custom endpoint resolves to a non-public address");
            safe.add(address);
        }
        return new ApprovedEndpoint(uri, host, List.copyOf(safe));
    }

    private boolean isAllowed(String host) {
        for (String allowed : allowedHosts) {
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(2);
                if (host.endsWith("." + suffix) && !host.equals(suffix)) return true;
            } else if (host.equals(allowed)) return true;
        }
        return false;
    }

    private static String normalizeAllowEntry(String entry) {
        if (entry == null || entry.isBlank()) return "";
        String value = entry.trim().toLowerCase(Locale.ROOT);
        boolean wildcard = value.startsWith("*.");
        if (wildcard) value = value.substring(2);
        if (value.contains("://") || value.contains("/") || value.contains(":") || isIpLiteral(value)) return "";
        String host = normalizeHost(value);
        return host.isEmpty() ? "" : (wildcard ? "*." : "") + host;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) return "";
        try { return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException ex) { return ""; }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && c >= 64 && c <= 127)
                    && !(a == 169 && c == 254) && !(a == 172 && c >= 16 && c <= 31)
                    && !(a == 192 && (c == 0 || c == 168))
                    && !(a == 198 && (c == 18 || c == 19 || c == 51))
                    && !(a == 203 && c == 0);
        }
        int first = b[0] & 255, second = b[1] & 255;
        return (first & 0xfe) != 0xfc && !(first == 0x20 && second == 0x01 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8);
    }
}
