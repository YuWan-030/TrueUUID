#!/usr/bin/env bash
# Pinned CI inputs for the Spigot 1.20.1 plugin target.
#
# These pins make CI reproducible. They are not a runtime gate: the plugin
# accepts any Spigot 1.20.1 build whose login seams all resolve.
set -euo pipefail

expected_target='spigot-1.20.1'
buildtools_build=200
buildtools_sha256='b61fa90158f594ee95bea1a27399eb64d439b4c8ae9345bd4476a02ce49b06ff'
declare -A spigot_refs=(
    [BuildData]='221903b51701960ac778d8641b31cebcf411caa8'
    [Bukkit]='69c7ce23f295a5bf1b1b7128bc1daece4ead768e'
    [CraftBukkit]='3f9263ba3a726846a9466e12da95d73229af4ad9'
    [Spigot]='d2eba2c820b52b742eb542c6d2c4d76e3d743570'
)
smoke_shutdown_markers=(
    'Stopping the server'
    'ThreadedAnvilChunkStorage: All dimensions are saved'
)

# shellcheck source=scripts/ci/server-plugins/lib.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"

prepare_server_runtime() {
    local work="$sp_work_root/buildtools"
    local buildtools_jar="$work/BuildTools-$buildtools_build.jar"
    if [[ ! -f "$work/.build-complete" ]]
    then
        sp_require_auto_build 'the Spigot BuildTools runtime'
        sp_require_tools git
        sp_download "https://hub.spigotmc.org/jenkins/job/BuildTools/$buildtools_build/artifact/target/BuildTools.jar" \
            "$buildtools_jar" "$buildtools_sha256"
        mkdir -p "$work/output" "$work/m2"
        echo "Building Spigot $sp_game_version with BuildTools $buildtools_build. This takes several minutes." >&2
        if ! (
            cd "$work"
            MAVEN_OPTS="-Dmaven.repo.local=$work/m2" "$sp_java17_home/bin/java" -Xmx2G \
                -jar "$buildtools_jar" --rev "$sp_game_version" --compile SPIGOT \
                --output-dir "$work/output" --nogui
        ) >"$work/buildtools.log" 2>&1
        then
            tail -n 80 "$work/buildtools.log" >&2 || true
            die 1 "BuildTools failed. See $work/buildtools.log"
        fi
        touch "$work/.build-complete"
    fi
    sp_check_sha256 "$buildtools_sha256" "$buildtools_jar"
    local repository actual
    for repository in BuildData Bukkit CraftBukkit Spigot
    do
        actual=$(git -C "$work/$repository" rev-parse HEAD)
        [[ "$actual" == "${spigot_refs[$repository]}" ]] || die 65 "$repository revision mismatch: $actual"
    done
    server_launch_jar="$work/output/spigot-$sp_game_version.jar"
    server_mapped_jar="$work/m2/org/spigotmc/spigot/$sp_game_version-R0.1-SNAPSHOT/spigot-$sp_game_version-R0.1-SNAPSHOT.jar"
    server_home_seed=()
    server_runtime_files=("$buildtools_jar" "$server_launch_jar" "$server_mapped_jar")
    [[ -f "$server_launch_jar" ]] || die 66 "BuildTools did not produce $server_launch_jar"
    [[ -f "$server_mapped_jar" ]] || die 66 "BuildTools did not install the mapped Spigot runtime JAR"
}

server_plugin_main "$@"
