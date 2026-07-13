# Handoff: completing modern 1.21 support

## Starting point

Work from the current local `main`; inspect its commit, working tree and target
matrix before changing anything. Do not push, rewrite, or delete historical
refs without an explicit maintainer decision.

Relevant refs:

| Ref | Commit | Meaning |
|---|---|---|
| `main` | `03c6ede` | Current modular Forge 1.20.1 foundation. |
| `archive/forge-1.20-pre-monorepo` | `99cc1f5` | Preserved pre-module Forge work. |
| `archive/neoforge-1.21.1-pre-monorepo` | `abb9454` | Preserved NeoForge 1.21.1 work. |

The historical NeoForge project builds in its original layout with JDK 21:

```bash
JAVA_HOME=<jdk-21> PATH=<jdk-21>/bin:$PATH bash ./gradlew build --offline --no-daemon
```

That is preservation evidence only; it is not an active support claim.

## Critical porting constraint

Do **not** merge or copy the historical NeoForge login implementation as-is.
Its `SessionCheck` accepts a client-supplied session URL through Java's default
`HttpClient`; it does not retain the Forge 1.20.1 adapter's endpoint allowlist,
public-address resolution, DNS-pinning, redirect refusal, response-size limit,
or bounded request coordinator. Review it with:

```bash
git show archive/neoforge-1.21.1-pre-monorepo:src/main/java/cn/alini/trueuuid/server/SessionCheck.java
```

The old branch is valuable API/lifecycle evidence for 1.21.1 payloads, Mixins,
and metadata generation. It is not a secure implementation template.

## What is and is not implemented

- Forge 1.20.1 is the sole active target in `platform/forge-1.20.1`.
- NeoForge 1.21.1 has historical source only; `platform/neoforge-1.21.1` does
  not exist yet.
- Forge 1.21.1 has no source or build yet. NeoForge APIs must not be renamed or
  treated as Forge APIs.
- Forge 1.20.1 passed a local matching Forge 47.4.10/JDK 17 Mojang-account
  login on 2026-07-12, including verified UUID/name and skin replacement.
  Its Yggdrasil, denial/timeout/grace, and migration rollback scenarios are
  still maintainer acceptance work.
- No 1.21 client/server runtime matrix has been run by this repository work.

## Completed shared foundation

The current `main` already contains the first three prerequisites:

1. Java-only login state, verified profile, verifier, registry, grace-cache
   and migration interfaces in `shared/protocol`.
2. A shared bounded safe session verifier with endpoint allowlisting, public
   DNS checks, per-request DNS pinning, TLS hostname verification, no redirects
   and a response limit. Its pinned TLS transport is runtime-smoke-tested.
3. Protocol v1 header plus golden bounded query/answer fixtures; adapters use
   the same Java-only messages.

Do not extract or redesign these foundations again unless a focused test shows
a defect. Keep the Forge 1.20.1 focused tests passing while adding adapters.

## Required implementation order from here

1. Add `platform/neoforge-1.21.1` using the old branch only to map 1.21.1
   native payloads and lifecycle hooks onto the shared core. Build it with JDK
   21 and add codec/lifecycle tests.
2. Add `platform/forge-1.21.1` independently from the exact Forge 1.21.1
   userdev APIs. Do not start from the NeoForge module. Add it to the same
   fixture and test matrix.
3. Only then change either 1.21 target from Planned to Active in
   `docs/architecture/target-matrix.md`.

## Runtime acceptance matrix for the maintainer

For each target, test a matching modded client and offline-mode server:

1. Mojang account verification and UUID/name/skin replacement.
2. Allowed Yggdrasil verification; reject an unallowlisted, private, redirect,
   oversized, or malformed endpoint.
3. Client denial, missing token, malformed response, timeout, and disconnect.
4. Known-name offline fallback denial and short same-IP grace behavior.
5. Migration confirmation, successful backup/migration, cancellation, and a
   forced rollback/error path.

Record the exact loader/JDK/artifact in the target matrix with the test result.
