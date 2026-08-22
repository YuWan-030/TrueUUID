package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.protocol.HybridIdentityPolicy;
import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAuthPort;
import cn.alini.trueuuid.server.MinecraftNames;
import cn.alini.trueuuid.server.UnifiedAdmissionPolicy;

/** Player-readable, bounded messages for server-owned login denials. */
final class LoginDenialMessages {
    static final String SERVER_BUSY =
            "Too many logins are being verified. Wait a moment and try again.";
    static final String STARTUP_FAILURE =
            "TrueUUID could not safely start this login. Try again; contact the server administrator if it persists.";
    static final String SERVER_SHUTTING_DOWN =
            "The server is shutting down. Reconnect after it starts again.";
    static final String PROFILE_APPLICATION_FAILED =
            "Your verified identity could not be applied safely. Reconnect; contact the server administrator if it persists.";

    private static final int MAXIMUM_LENGTH = 240;

    static String forAttempt(
            HybridLoginCoordinator.DenialReason reason,
            HybridIdentityPolicy.AuthorityLookup authority,
            HybridIdentityPolicy.PremiumProof premiumFailure,
            OfflineAuthPort.Failure offlineFailure,
            String explicitMessage
    ) {
        if (explicitMessage != null) return requireMessage(explicitMessage);
        if (reason == HybridLoginCoordinator.DenialReason.DISCONNECTED
                && authority == HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS) {
            return requireMessage(authorityPolicyMessage(authority));
        }
        return requireMessage(switch (reason) {
            case IDENTITY_POLICY -> authorityPolicyMessage(authority);
            case CLIENT_QUERY_UNSUPPORTED ->
                    "This server requires the matching TrueUUID client mod for premium login.";
            case PREMIUM_PROOF_FAILED -> premiumFailureMessage(premiumFailure);
            case OFFLINE_AUTH_FAILED -> offlineFailureMessage(offlineFailure);
            case TRANSACTION_MISMATCH, REPLAY, PROTOCOL_VIOLATION ->
                    "The login response did not match this connection. Reconnect with the matching TrueUUID version.";
            case PROFILE_APPLICATION_FAILED -> PROFILE_APPLICATION_FAILED;
            case TIMEOUT ->
                    "TrueUUID login verification timed out. Check your connection and try again.";
            case DISCONNECTED ->
                    "The connection closed before TrueUUID verification completed. Reconnect and try again.";
            case CANCELLED ->
                    "Login was cancelled before authentication completed. Reconnect and try again.";
            case INTERNAL_ERROR -> STARTUP_FAILURE;
        });
    }

    static String collision(
            String requestedName,
            String offlineAlias,
            UnifiedAdmissionPolicy.CollisionResolution resolution
    ) {
        String name = MinecraftNames.requireValid(requestedName);
        String alias = MinecraftNames.requireValidEffective(offlineAlias);
        String incoming = switch (resolution) {
            case ALIAS_INCOMING_OFFLINE -> "offline";
            case MOVE_EXISTING_OFFLINE -> "premium";
        };
        return requireMessage("Name collision: no identity was changed. Ask an administrator to run "
                + "/trueuuid identity collision allow " + name + " " + incoming
                + ", then reconnect within 60 seconds. The offline identity will use " + alias + ".");
    }

    private static String authorityPolicyMessage(HybridIdentityPolicy.AuthorityLookup authority) {
        if (authority == HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS) {
            return "This username belongs to a premium Minecraft account. Sign in with that account; offline impersonation is blocked.";
        }
        if (authority == HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE) {
            return "Minecraft account lookup is unavailable. Login was denied to protect premium usernames. Try again later.";
        }
        return "This account is not permitted by the server's TrueUUID login policy.";
    }

    private static String premiumFailureMessage(HybridIdentityPolicy.PremiumProof failure) {
        if (failure == null) {
            return "Premium account verification failed. Restart your launcher, sign in again, and retry.";
        }
        return switch (failure) {
            case VERIFIED -> throw new IllegalArgumentException("VERIFIED is not a denial");
            case NOT_JOINED, HTTP_204 ->
                    "Minecraft did not confirm this premium session. Restart your launcher, sign in again, and retry.";
            case RATE_LIMITED, SERVER_ERROR, AUTHORITY_UNAVAILABLE, TLS_FAILURE, DNS_FAILURE ->
                    "Minecraft session services are unavailable. Try again later; premium names cannot use offline fallback.";
            case MALFORMED_RESPONSE, OVERSIZED_RESPONSE, REDIRECTED, NAME_MISMATCH, UUID_MISMATCH,
                    WRONG_VERIFY_TOKEN, REPLAYED ->
                    "The premium proof was invalid or did not match this connection. Restart your launcher and retry.";
            case CLIENT_ABORTED ->
                    "The client cancelled premium verification. Sign in to your premium account and reconnect.";
            case CANCELLED ->
                    "Premium verification was cancelled. Reconnect and try again.";
            case TIMEOUT ->
                    "Premium verification timed out. Check your connection and try again.";
            case INTERNAL_ERROR ->
                    "Premium verification failed internally. Try again; contact the server administrator if it persists.";
        };
    }

    private static String offlineFailureMessage(OfflineAuthPort.Failure failure) {
        if (failure == null) {
            return "Offline authentication failed. Check the server's offline-login requirements and try again.";
        }
        return switch (failure) {
            case INVALID_CREDENTIAL ->
                    "The offline login response was invalid. Use the matching TrueUUID client or check your offline credentials.";
            case ALREADY_ENROLLED ->
                    "This offline username is already enrolled. Use its configured offline authentication method.";
            case UNAVAILABLE ->
                    "Offline authentication is unavailable on this server. Try again later or contact the administrator.";
            case CANCELLED ->
                    "Offline authentication was cancelled. Reconnect and try again.";
            case TIMEOUT ->
                    "Offline authentication timed out. Check your connection and try again.";
            case INTERNAL_ERROR ->
                    "Offline authentication failed internally. Contact the server administrator.";
        };
    }

    private static String requireMessage(String message) {
        if (message.isBlank() || message.length() > MAXIMUM_LENGTH || message.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid login denial message");
        }
        return message;
    }

    private LoginDenialMessages() {
    }
}
