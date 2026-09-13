#!/usr/bin/env bash
set -euo pipefail

# Resolves one release-approved target from either inventory into the neutral
# shape consumed by the Release workflow and every store publisher. Mods come
# from release/targets.json and server plugins from ci/server-plugin-targets.json.
# Store project IDs are not versioned. `curseforge_project_variable` names the
# GitHub Actions variable that holds the target's CurseForge project ID.
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
    jq -ce '
        .loader as $loader |
        {id, artifact, game_version,
         kind: "mod",
         loader: $loader,
         loader_name: ({"forge": "Forge", "fabric": "Fabric", "neoforge": "NeoForge"} | .[$loader]
             // error("unsupported loader")),
         modrinth_loaders: [$loader],
         modrinth_environment: "client_and_server",
         curseforge_project_variable: "CURSEFORGE_ID_MOD",
         curseforge_api: "minecraft",
         hangar: null,
         spigotmc: false}
    ' <<<"$target"
elif [[ "$target_id" =~ ^(spigot|paper)- ]]
then
    target=$(./scripts/ci/validate-server-plugin-targets.sh --approved "$target_id")
    jq -ce '
        {id, artifact, game_version, modrinth_loaders, curseforge_project_variable, hangar,
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
