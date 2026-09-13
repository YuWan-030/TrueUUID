#!/usr/bin/env bash
set -euo pipefail

# Resolves one release-approved target from either inventory into the neutral
# shape consumed by the Release workflow and the Modrinth publisher. Mods come
# from release/targets.json and server plugins from ci/server-plugin-targets.json.
# stdout is machine-readable JSON. Diagnostics go to stderr.

if [[ $# -ne 1 ]]
then
    echo "usage: $0 <target-id>" >&2
    exit 64
fi
target_id=$1

case "$target_id" in
    forge-*|fabric-*|neoforge-*)
        target=$(./scripts/release/validate-targets.sh --approved "$target_id")
        jq -ce '
            def loader_name:
                if . == "forge" then "Forge"
                elif . == "fabric" then "Fabric"
                elif . == "neoforge" then "NeoForge"
                else error("unsupported loader")
                end;
            {id, artifact, game_version,
             kind: "mod",
             loader: .loader,
             loader_name: (.loader | loader_name),
             modrinth_loaders: [.loader],
             modrinth_environment: "client_and_server",
             curseforge: true}
        ' <<<"$target"
        ;;
    spigot-*|paper-*)
        target=$(./scripts/ci/validate-server-plugin-targets.sh --approved "$target_id")
        jq -ce '
            {id, artifact, game_version, modrinth_loaders,
             kind: "plugin",
             loader: .platform,
             loader_name: .platform_name,
             modrinth_environment: "server_only",
             curseforge: false}
        ' <<<"$target"
        ;;
    *)
        echo "unknown release target: $target_id" >&2
        exit 65
        ;;
esac
