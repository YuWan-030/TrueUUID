package cn.alini.trueuuid.fabric.login;

import cn.alini.trueuuid.protocol.AuthMessages;

/** Version-neutral login-query send seam owned by one connection transaction. */
@FunctionalInterface
interface FabricLoginQuerySender {
    void send(AuthMessages.Query query);
}
