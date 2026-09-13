package cn.alini.trueuuid.paper.v1_20_1;

import cn.alini.trueuuid.craftbukkit.v1_20_1.TrueuuidPlugin;

/** TrueUUID for Paper 1.20.1. The shared engine owns every authentication decision. */
public final class TrueuuidPaperPlugin extends TrueuuidPlugin {
    public TrueuuidPaperPlugin() {
        super(new PaperPlatform());
    }
}
