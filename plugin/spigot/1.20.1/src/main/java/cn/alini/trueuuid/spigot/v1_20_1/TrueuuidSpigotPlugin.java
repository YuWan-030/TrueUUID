package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.craftbukkit.v1_20_1.TrueuuidPlugin;

/** TrueUUID for Spigot 1.20.1. The shared engine owns every authentication decision. */
public final class TrueuuidSpigotPlugin extends TrueuuidPlugin {
    public TrueuuidSpigotPlugin() {
        super(new SpigotPlatform());
    }
}
