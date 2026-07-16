package cn.alini.trueuuid.client;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Client-side resolution of an authlib-injector skin site's hasJoined endpoint,
 * ported from forge-1.20.1's ClientHandshakeMixin. The result is only a hint:
 * the server still applies its own endpoint allowlist, public-address checks,
 * DNS pinning, and redirect/response limits before contacting it. An empty
 * string means a plain Mojang session and keeps the server on its fixed
 * Mojang endpoint.
 *
 * <p>Pure Java on purpose (reflection only, no authlib import), so it compiles
 * unchanged across every modern Forge target and can be duplicated verbatim
 * for NeoForge.</p>
 */
public final class ClientYggdrasilEndpoint {

    /**
     * The {@code -javaagent:authlib-injector.jar=<API root>} launcher argument
     * is preferred over the CHECK_URL field: some authlib-injector versions
     * replace CHECK_URL with a 127.0.0.1 local proxy the server cannot reach.
     */
    public static String resolveHasJoinedUrl() {
        String agentUrl = resolveFromJavaAgent();
        if (!agentUrl.isEmpty()) return agentUrl;

        try {
            Class<?> sessionService = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");

            // authlib-injector rewrites CHECK_URL at class load on the authlib
            // versions that still have it (1.5.x-era; absent on newer authlib,
            // where the agent argument above is the operative source).
            String url = readStaticField(sessionService, "CHECK_URL");
            if (url != null) return sanitizeUrl(url);

            for (Field field : sessionService.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && URL.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    URL value = (URL) field.get(null);
                    if (value != null && value.toString().contains("hasJoined")) {
                        return sanitizeUrl(value.toString());
                    }
                }
            }
            for (Field field : sessionService.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && String.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    if (field.get(null) instanceof String value && value.contains("hasJoined")) {
                        return sanitizeUrl(value);
                    }
                }
            }
        } catch (Throwable ignored) {
            // No authlib-injector (or an incompatible authlib): Mojang default.
        }
        return "";
    }

    private static String resolveFromJavaAgent() {
        try {
            List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String argument : arguments) {
                if (!argument.startsWith("-javaagent:")) continue;
                if (!argument.toLowerCase(Locale.ROOT).contains("authlib")) continue;

                int equals = argument.indexOf('=');
                if (equals < 0 || equals + 1 >= argument.length()) continue;

                String hasJoined = buildHasJoinedUrlFromApiRoot(argument.substring(equals + 1).trim());
                if (!hasJoined.isEmpty()) return hasJoined;
            }
        } catch (Throwable ignored) {
            // JVM arguments unavailable; fall back to the CHECK_URL reflection.
        }
        return "";
    }

    static String buildHasJoinedUrlFromApiRoot(String apiRoot) {
        if (apiRoot == null || apiRoot.isBlank()) return "";
        String root = apiRoot.trim();
        if (!root.endsWith("/")) root = root + "/";
        return sanitizeUrl(root + "sessionserver/session/minecraft/hasJoined");
    }

    private static String readStaticField(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof URL url) return url.toString();
            if (value instanceof String text) return text;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Unwraps authlib-injector's local proxy format
     * ({@code http://127.0.0.1:<port>/https/<domain><path>}) to the real URL,
     * returns an empty string for the standard Mojang endpoint, and strips
     * query parameters.
     */
    static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";

        String url = rawUrl;
        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
            int httpsIndex = url.indexOf("/https/");
            if (httpsIndex >= 0) {
                url = "https://" + url.substring(httpsIndex + "/https/".length());
            } else {
                int httpIndex = url.indexOf("/http/");
                if (httpIndex >= 0) {
                    url = "http://" + url.substring(httpIndex + "/http/".length());
                }
            }
        }

        // The proxy target may still be Mojang itself; an empty answer keeps
        // the server on its fixed endpoint instead of echoing it back.
        if (url.contains("sessionserver.mojang.com")) return "";

        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) url = url.substring(0, queryIndex);
        return url;
    }

    private ClientYggdrasilEndpoint() {}
}
