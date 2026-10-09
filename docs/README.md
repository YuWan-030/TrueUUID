# TrueUUID documentation

Start with the [setup guide](setup.md) to install and configure TrueUUID.

For development, check the source and manifests before relying on a document.
`release/targets.json` lists exact mod targets and their publication approvals.
`ci/server-plugin-targets.json` lists plugins separately.
The [target matrix](architecture/target-matrix.md) explains the recorded runtime
evidence and what still needs testing.

## Guides

- [Installation and configuration](setup.md)
- [Plugin identity policy](development/identity-policy-admin-guide.md)
- [Local mod client/server testing](development/local-runtime-testing.md)
- [Spigot/Paper testing](development/server-plugin-1.20.1-testing.md)
- [Adding an adapter](development/adding-adapter.md)
- [Release automation](development/release-automation.md)

## Architecture and evidence

- [Target matrix](architecture/target-matrix.md)
- [Client session selection and issue #34](architecture/client-session.md)
- [Forge source and build eras](architecture/forge-roadmap.md)
- [Fabric source and build eras](development/fabric-1.20.1-1.21.11-handoff.md)
- [Spigot/Paper login boundary](architecture/server-plugin-bridge.md)
- [Plugin runtime evidence](development/server-plugin-1.20.1-evidence.md)
- [Rules for widening Minecraft version ranges](architecture/version-consolidation-roadmap.md)

Changelogs describe their named versions. Old artifact hashes and dated test
results are historical evidence, not approval of a changed login path.
Local ignored handoff files are workspace notes and can be stale.
