package cn.alini.trueuuid.protocol;

/** Versioning for the loader-independent login wire contract. */
public final class ProtocolVersion {
    public static final int MAGIC = 0x54555549; // "TUUI"
    public static final int CURRENT = 1;

    private ProtocolVersion() {}
}
