package cn.alini.trueuuid.bukkit;

import java.util.List;
import java.util.Objects;

/** Platform-neutral content of the decorative enable banner. */
public final class StartupBanner {
    /** One banner line: an optional highlighted title followed by plain detail text. */
    public record Line(String title, Tone tone, String detail) {
        public Line {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(detail, "detail");
        }
    }

    public static List<Line> lines(
            String pluginVersion,
            String platformName,
            String minecraftVersion,
            boolean supported,
            String transport,
            String offlineClients,
            String admission
    ) {
        Line heading = new Line("TrueUUID", Tone.INFO,
                pluginVersion + " for " + platformName + " " + minecraftVersion
                        + (supported ? "" : " (unsupported candidate)"));
        Line policy = new Line("", Tone.DETAIL,
                "transport " + transport + ", offline clients " + offlineClients + ", admission " + admission);
        Line state = new Line("", supported ? Tone.SUCCESS : Tone.WARNING,
                "Premium verification is active and fails closed.");
        return List.of(heading, policy, state);
    }

    private StartupBanner() {
    }
}
