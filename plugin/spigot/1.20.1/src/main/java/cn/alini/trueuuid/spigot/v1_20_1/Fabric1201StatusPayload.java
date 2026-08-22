package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.protocol.HybridStatusWireCodec;

/** Exact one-byte play payload consumed by the TrueUUID Fabric 1.20.1 client. */
final class Fabric1201StatusPayload {
    static final String CHANNEL = "trueuuid:account_status";

    static byte[] encode(AccountStatus status) {
        return new byte[]{HybridStatusWireCodec.encode(status)};
    }

    private Fabric1201StatusPayload() {
    }
}
