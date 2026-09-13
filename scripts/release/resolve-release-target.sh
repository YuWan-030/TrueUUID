#!/usr/bin/env bash
set -euo pipefail

# Resolves one release-approved target from either inventory into the neutral
# shape consumed by the Release workflow and every store publisher. Mods come
# from release/targets.json and server plugins from ci/server-plugin-targets.json.
# stdout is machine-readable JSON. Diagnostics go to stderr.

if [[ $# -ne 1 ]]
then
    echo "usage: $0 <target-id>" >&2
    exit 64
fi
target_id=$1

if [[ "$target_id" =~ ^(forge|fabric|neoforge)- ]]
then
    target=$(./scripts/release/validate-targets.sh --approved "$target_id")
    curseforge_project_id=$(jq -r '.curseforge_project_id' release/targets.json)
    [[ "$curseforge_project_id" =~ ^[1-9][0-9]*$ ]] || {
        echo "release/targets.json has no valid CurseForge mod project ID" >&2
        exit 65
    }
    jq -ce --argjson curseforge_project_id "$curseforge_project_id" '
        .loader as $loader |
        {id, artifact, game_version,
         kind: "mod",
         loader: $loader,
         loader_name: ({"forge": "Forge", "fabric": "Fabric", "neoforge": "NeoForge"} | .[$loader]
             // error("unsupported loader")),
         modrinth_loaders: [$loader],
         modrinth_environment: "client_and_server",
         curseforge_project_id: $curseforge_project_id,
         curseforge_api: "minecraft",
         hangar: null,
         spigotmc: false}
    ' <<<"$target"
elif [[ "$target_id" =~ ^(spigot|paper)- ]]
then
    target=$(./scripts/ci/validate-server-plugin-targets.sh --approved "$target_id")
    jq -ce '
        if .curseforge_project_id == null then error("approved plugins need a CurseForge plugin project ID") else . end |
        {id, artifact, game_version, modrinth_loaders, curseforge_project_id, hangar,
         kind: "plugin",
         loader: .platform,
         loader_name: .platform_name,
         modrinth_environment: "server_only",
         curseforge_api: "bukkit",
         spigotmc: (.platform == "spigot")}
    ' <<<"$target"
else
    echo "unknown release target: $target_id" >&2
    exit 65
fi
