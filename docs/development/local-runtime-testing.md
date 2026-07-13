# Local runtime testing

The active development target can be launched from two terminals without
remembering its Gradle module path:

```bash
scripts/run-dev-target.sh forge-1.20.1 server
scripts/run-dev-target.sh forge-1.20.1 client
```

The script uses Java 17 for Forge 1.20.1. Override the detected JDK with
`TRUEUUID_JAVA_HOME=/path/to/jdk` when necessary. The server run directory is
owned by ForgeGradle; accept the EULA and configure its `server.properties`
there before a real login test.

Run the server and client commands in separate terminals. The client must use
the same freshly built protocol-v1 source tree as the server; it cannot
interoperate with an older TrueUUID jar.

The launcher intentionally rejects targets not listed in its case statement.
Add a target only after its module, declared JDK build, focused tests, and
runtime acceptance prerequisites exist. It does not launch historical NeoForge
1.21.1 work or planned Forge/NeoForge 1.21.1 adapters.
