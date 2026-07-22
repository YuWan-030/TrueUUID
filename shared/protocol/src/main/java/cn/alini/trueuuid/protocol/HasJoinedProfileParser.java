package cn.alini.trueuuid.protocol;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Canonical bounded-response parser for Mojang-compatible hasJoined JSON. */
public final class HasJoinedProfileParser {
    private static final Gson GSON = new Gson();

    public static Optional<VerifiedProfile> parse(SafeSessionHttpClient.Response response) {
        if (response == null || response.status() != 200) return Optional.empty();
        HasJoinedJson value = GSON.fromJson(response.body(), HasJoinedJson.class);
        if (value == null || value.id == null || value.name == null || value.name.isBlank()) {
            return Optional.empty();
        }
        UUID uuid = parseUuid(value.id);
        List<VerifiedProfile.Property> properties = value.properties == null ? List.of() : value.properties.stream()
                .filter(property -> property != null && property.name != null && property.value != null)
                .map(property -> new VerifiedProfile.Property(property.name, property.value, property.signature))
                .toList();
        return Optional.of(new VerifiedProfile(uuid, value.name, properties));
    }

    private static UUID parseUuid(String compact) {
        if (!compact.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("invalid profile UUID");
        }
        return UUID.fromString(compact.replaceFirst(
                "(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }

    private static final class HasJoinedJson {
        String id;
        String name;
        List<PropertyJson> properties;
    }

    private static final class PropertyJson {
        String name;
        String value;
        @SerializedName("signature") String signature;
    }

    private HasJoinedProfileParser() {}
}
