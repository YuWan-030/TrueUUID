#!/usr/bin/env bash
# Pinned CI inputs for the Paper 1.20.1 plugin target.
#
# These pins make CI reproducible. They are not a runtime gate: the plugin
# accepts any Paper 1.20.1 build whose login seams all resolve. Paper JARs are
# downloaded from PaperMC and are never committed or redistributed.
set -euo pipefail

expected_target='paper-1.20.1'
paper_build=196
paper_sha256='234a9b32098100c6fc116664d64e36ccdb58b5b649af0f80bcccb08b0255eaea'
smoke_shutdown_markers=(
    'Stopping the server'
    "[ChunkHolderManager] Saving all chunkholders for world 'trueuuid-test-world'"
    'Closing Server'
)

# shellcheck source=scripts/ci/server-plugins/lib.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"

prepare_server_runtime() {
    local work="$sp_work_root/runtime"
    local paperclip="$work/paper-$sp_game_version-$paper_build.jar"
    local patched="$work/versions/$sp_game_version/paper-$sp_game_version.jar"
    if [[ ! -f "$patched" ]]
    then
        sp_require_auto_build 'the patched Paper runtime'
        sp_download "https://fill-data.papermc.io/v1/objects/$paper_sha256/paper-$sp_game_version-$paper_build.jar" \
            "$paperclip" "$paper_sha256"
        echo "Patching Paper $sp_game_version build $paper_build." >&2
        if ! (
            cd "$work"
            "$sp_java17_home/bin/java" -Dpaperclip.patchonly=true -jar "$paperclip"
        ) >"$work/paperclip.log" 2>&1
        then
            tail -n 80 "$work/paperclip.log" >&2 || true
            die 1 "Paperclip failed. See $work/paperclip.log"
        fi
    fi
    sp_check_sha256 "$paper_sha256" "$paperclip"
    [[ -f "$patched" ]] || die 66 "Paperclip did not produce $patched"
    sp_unfolded_manifest "$patched" | grep -Fxq "Implementation-Version: git-Paper-$paper_build" ||
        die 65 "patched Paper runtime is not build $paper_build"
    server_launch_jar="$paperclip"
    server_mapped_jar="$patched"
    server_home_seed=("$work/cache" "$work/libraries" "$work/versions")
    server_runtime_files=("$paperclip" "$patched")
}

server_plugin_main "$@"
