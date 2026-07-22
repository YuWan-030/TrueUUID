package cn.alini.trueuuid.fabric.client;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Client-side resolution of an authlib-injector skin site's hasJoined endpoint.
 * The server still applies its own allowlist, DNS/public-address checks, TLS
 * verification, redirect denial, and bounded responses before contacting it.
 */
public final class ClientYggdrasilEndpoint {
    public static String resolveHasJoinedUrl() {
        String agentUrl = resolveFromJavaAgent();
        if (!agentUrl.isEmpty()) return agentUrl;

        try {
            Class<?> sessionService = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");
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
            // Mojang default.
        }
        return "";
    }

    static String buildHasJoinedUrlFromApiRoot(String apiRoot) {
        if (apiRoot == null || apiRoot.isBlank()) return "";
        String root = apiRoot.trim();
        if (!root.endsWith("/")) root = root + "/";
        return sanitizeUrl(root + "sessionserver/session/minecraft/hasJoined");
    }

    static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";

        String url = rawUrl;
        if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
            int httpsIndex = url.indexOf("/https/");
            if (httpsIndex >= 0) {
                url = "https://" + url.substring(httpsIndex + "/https/".length());
            } else {
                int httpIndex = url.indexOf("/http/");
                if (httpIndex >= 0) url = "http://" + url.substring(httpIndex + "/http/".length());
            }
        }

        if (url.contains("sessionserver.mojang.com")) return "";
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
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
            // JVM arguments unavailable; fall back to authlib reflection.
        }
        return "";
    }

    private static String readStaticField(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof URL url) return url.toString();
            if (value instanceof String text) return text;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ClientYggdrasilEndpoint() {}
}
