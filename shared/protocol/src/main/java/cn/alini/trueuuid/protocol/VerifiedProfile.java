package cn.alini.trueuuid.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A verified identity without authlib or Minecraft profile objects. */
public record VerifiedProfile(UUID uuid, String name, List<Property> properties) {
    public record Property(String name, String value, String signature) {
        public Property {
            name = requireBounded(name, 256, "property name");
            value = requireBounded(value, 32 * 1024, "property value");
            signature = signature == null ? null : requireBounded(signature, 32 * 1024, "property signature");
        }
    }

    public VerifiedProfile {
        Objects.requireNonNull(uuid, "uuid");
        name = requireBounded(name, 16, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name is blank");
        properties = List.copyOf(properties == null ? List.of() : properties);
        if (properties.size() > 32) throw new IllegalArgumentException("too many profile properties");
    }

    private static String requireBounded(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > maximum) throw new IllegalArgumentException(field + " is too long");
        return value;
    }
}
