# Client session selection

Issue [#34](https://github.com/YuWan-030/TrueUUID/issues/34) reports that PCL2
offline accounts fail authentication because their token is nonempty.

PCL2's `McLoginLegacyStart` sets the access token to the profile UUID.
Its launch argument builder sets `user_type` to `msa` for every login mode.
Account type and token presence therefore cannot identify this offline session.
This behavior was checked in
[PCL2's launch source](https://github.com/Hex-Dragon/PCL2/blob/0e0d12fdce6a2804916fb2be60e41144da637c18/Plain%20Craft%20Launcher%202/Modules/Minecraft/ModLaunch.vb#L695).

## Local decision

[`ClientSession.java`](../../shared/protocol/src/main/java/cn/alini/trueuuid/protocol/ClientSession.java)
accepts plain Java values.
Every Forge, Fabric, and NeoForge client handshake uses it before
`joinServer`. A session is missing when:

- the profile UUID is absent;
- the token is null, blank, or exactly `0`;
- the token equals that profile UUID, with or without hyphens, ignoring case.

Other tokens still require proof, including UUID-shaped tokens that differ
from the profile UUID. Custom providers may issue opaque tokens in that format.
This check needs no launcher-specific network request and never sends or logs
the token.

An offline session sends the existing bounded no-session answer. A session
that attempts premium proof sends a proof answer even if the call fails or
times out. Fabric captures the session before scheduling the call, so the
classification and proof use the same local account.

## Server decision

The client selects a proof path. It does not authorize admission.
`OfflineClientResponse.isExplicit` recognizes only the exact no-session
answer. Server policy then checks whether offline admission is allowed and
whether the name is protected.

A rejected or expired premium session, timeout, malformed response, or
authority failure must not become an offline request. The Fabric client
previously marked every failed call as missing-session. The issue #34 change
also corrects that behavior. No wire-format change is needed.

## Validation

`ClientSessionTest` checks PCL2 placeholders, existing development
placeholders, opaque provider tokens, and separation of failed proof from
offline requests. Existing codec and server-policy tests cover the unchanged
wire format and known-name denial.

The changed clients still need installed-JAR testing: PCL2 offline login,
premium login, an expired premium session, known-name offline denial, and
allowlisted Yggdrasil login. Repeat the normal four-case matrix on all changed
targets before approving a release. The September 2026 evidence predates this
client change.
