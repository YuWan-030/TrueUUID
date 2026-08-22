package cn.alini.trueuuid.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;

/** Internal transport seam for deterministic safe-session tests. */
interface SessionHttpTransport {
    SafeSessionHttpClient.Response getTrusted(URI uri) throws IOException;

    SafeSessionHttpClient.Response get(URI uri, List<InetAddress> approvedAddresses) throws IOException;
}
