# shellcheck shell=bash
# Shared implementation of the pinned server-plugin CI operations.
#
# A target script in this directory sets expected_target and its exact server
# pins, defines prepare_server_runtime, and calls server_plugin_main. Gradle
# tasks, JAR inspection, and the installed-server smoke live here once, so the
# Spigot and Paper targets cannot drift apart.

sp_repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd -P)
sp_manifest="$sp_repo_root/ci/server-plugin-targets.json"
sp_java17_home=${JAVA_HOME_17_X64:-/usr/lib/jvm/jdk-17.0.12-oracle-x64}
sp_java21_home=${JAVA_HOME_21_X64:-/usr/lib/jvm/java-21-openjdk-amd64}
sp_runner_pid=
sp_java_pid=

die() {
    local status=$1
    shift
    printf '%s\n' "$*" >&2
    exit "$status"
}

sp_require_tools() {
    local tool
    for tool in "$@"
    do
        command -v "$tool" >/dev/null 2>&1 || die 69 "$tool is required"
    done
}

sp_check_sha256() {
    local expected=$1
    local file=$2
    [[ -f "$file" ]] || die 66 "missing pinned file: $file"
    printf '%s  %s\n' "$expected" "$file" | sha256sum --check --strict --quiet >/dev/null ||
        die 65 "SHA-256 mismatch for $file"
}

sp_download() {
    local url=$1
    local destination=$2
    local expected=$3
    if [[ ! -f "$destination" ]]
    then
        mkdir -p "$(dirname -- "$destination")"
        curl --fail --location --silent --show-error \
            --proto '=https' --proto-redir '=https' --tlsv1.2 \
            --retry 3 --connect-timeout 20 --max-time 900 \
            --output "$destination.part" "$url"
        mv -- "$destination.part" "$destination"
    fi
    sp_check_sha256 "$expected" "$destination"
}

sp_require_auto_build() {
    [[ "${TRUEUUID_SERVER_PLUGIN_AUTO_BUILD:-1}" == 1 ]] ||
        die 66 "$1 is missing and TRUEUUID_SERVER_PLUGIN_AUTO_BUILD=0 forbids preparing it"
}

sp_load_target() {
    sp_require_tools jq sha256sum curl sed
    [[ "${TRUEUUID_SERVER_PLUGIN_TARGET:-$expected_target}" == "$expected_target" ]] ||
        die 65 "this pinned script only supports $expected_target"
    local target
    target=$("$sp_repo_root/scripts/ci/validate-server-plugin-targets.sh" --target "$expected_target")
    sp_version=$(sed -n 's/^mod_version=//p' "$sp_repo_root/gradle.properties" | tr -d '[:space:]')
    sp_platform=$(jq -r '.platform' <<<"$target")
    sp_platform_name=$(jq -r '.platform_name' <<<"$target")
    sp_game_version=$(jq -r '.game_version' <<<"$target")
    sp_module=$(jq -r '.module' <<<"$target")
    sp_engine_module=$(jq -r '.engine_module' <<<"$target")
    sp_manifest_status=$(jq -r '.manifest_status' <<<"$target")
    sp_artifact="$sp_repo_root/$(jq -r --arg version "$sp_version" \
        '.artifact | split("%VERSION%") | join($version)' <<<"$target")"
    local version_package="v${sp_game_version//./_}"
    sp_main_class="cn.alini.trueuuid.${sp_platform}.${version_package}.Trueuuid${sp_platform_name}Plugin"
    sp_engine_package="cn/alini/trueuuid/craftbukkit/${version_package}"
    sp_work_root="$sp_repo_root/build/server-plugins/$expected_target"
    sp_protocollib_dir="$sp_repo_root/build/server-plugins/protocollib"
    sp_protocollib_minimum=$(jq -r '.protocollib.minimum_version' "$sp_manifest")
    sp_protocollib_runtime=$(jq -r '.protocollib.runtime_version' "$sp_manifest")
    mapfile -t sp_protocollib_versions < <(jq -r '.protocollib.verified[].version' "$sp_manifest")
}

sp_fetch_protocollib() {
    local version sha256
    while IFS=$'\t' read -r version sha256
    do
        sp_download "https://github.com/dmulloy2/ProtocolLib/releases/download/$version/ProtocolLib.jar" \
            "$sp_protocollib_dir/ProtocolLib-$version.jar" "$sha256"
    done < <(jq -r '.protocollib.verified[] | [.version, .sha256] | @tsv' "$sp_manifest")
}

sp_print_runtime() {
    local seed
    printf 'server_launch_jar=%s\n' "$server_launch_jar"
    printf 'server_mapped_jar=%s\n' "$server_mapped_jar"
    for seed in "${server_home_seed[@]}"
    do
        printf 'server_home_seed=%s\n' "$seed"
    done
}

sp_build() {
    prepare_server_runtime
    sp_fetch_protocollib
    (
        cd "$sp_repo_root"
        JAVA_HOME="$sp_java21_home" PATH="$sp_java21_home/bin:$PATH" ./gradlew \
            :shared:protocol:test \
            :shared:server-core:test \
            :plugin:bukkit-common:test \
            "$sp_engine_module:test" \
            "$sp_engine_module:serverRuntimeContractTest" \
            "$sp_module:test" \
            "$sp_module:jar" \
            ":platform:fabric-$sp_game_version:remapJar" \
            "$sp_engine_module:crossLoaderCompatibilityTest" \
            -PserverRuntimeJar="$server_mapped_jar" \
            -PpluginTarget="$expected_target" \
            --configure-on-demand --no-daemon --max-workers=2
    )
    [[ -f "$sp_artifact" ]] || die 66 "Gradle did not produce $sp_artifact"
    local protocollib_jars=()
    local version
    for version in "${sp_protocollib_versions[@]}"
    do
        protocollib_jars+=("$sp_protocollib_dir/ProtocolLib-$version.jar")
    done
    mkdir -p "$sp_work_root/reports"
    sha256sum "${server_runtime_files[@]}" "${protocollib_jars[@]}" "$sp_artifact" \
        > "$sp_work_root/reports/artifacts.sha256"
    echo "$sp_platform_name $sp_game_version tests, login seam contract, and cross-loader wire checks passed."
}

# JAR manifests fold long attributes onto continuation lines. Unfold them
# before comparing exact values.
sp_unfolded_manifest() {
    unzip -p "$1" META-INF/MANIFEST.MF | tr -d '\r' | awk '
        /^ / {
            current = current substr($0, 2)
            next
        }
        {
            if (current != "") print current
            current = $0
        }
        END {
            if (current != "") print current
        }
    '
}

sp_verify() {
    local output_dir=$1
    sp_require_tools jar unzip
    [[ -f "$sp_artifact" ]] || die 66 "missing built plugin: $sp_artifact"
    unzip -tqq "$sp_artifact" || die 65 "corrupt plugin JAR: $sp_artifact"

    local entries required
    entries=$(jar tf "$sp_artifact")
    for required in \
        META-INF/MANIFEST.MF \
        plugin.yml \
        config.yml \
        trueuuid-target.properties \
        "${sp_main_class//./\/}.class" \
        "$sp_engine_package/TrueuuidPlugin.class" \
        "$sp_engine_package/LoginBridge.class" \
        "$sp_engine_package/LoginSeams.class" \
        "$sp_engine_package/ProtocolLibConnections.class" \
        cn/alini/trueuuid/bukkit/TrueuuidBukkitApi.class \
        cn/alini/trueuuid/presentation/BrandText.class \
        cn/alini/trueuuid/protocol/AuthWireCodec.class \
        cn/alini/trueuuid/protocol/HybridLoginCoordinator.class \
        cn/alini/trueuuid/server/UnifiedAdmissionPolicy.class \
        cn/alini/trueuuid/server/PersistentIdentityRepository.class
    do
        grep -Fxq "$required" <<<"$entries" || die 65 "missing plugin entry: $required"
    done

    local foreign duplicates
    foreign=$(grep -E '^cn/alini/trueuuid/(spigot|paper|forge|fabric|neoforge)/' <<<"$entries" |
        grep -Ev "^cn/alini/trueuuid/${sp_platform}/" || true)
    [[ -z "$foreign" ]] || die 65 "classes for another platform leaked into $sp_artifact: $foreign"
    duplicates=$(grep '\.class$' <<<"$entries" | sort | uniq -d)
    [[ -z "$duplicates" ]] || die 65 "duplicate classes in $sp_artifact: $duplicates"
    if grep -Eq '(^|/)(test|tests)/|Test\.class$|(^|/)scripts/|\.sh$' <<<"$entries"
    then
        die 65 "test or development files leaked into $sp_artifact"
    fi

    local manifest attribute
    manifest=$(sp_unfolded_manifest "$sp_artifact")
    for attribute in \
        "Implementation-Version: $sp_version" \
        "TrueUUID-Target: $expected_target" \
        "TrueUUID-Support-Status: $sp_manifest_status" \
        "Minecraft-Version: $sp_game_version" \
        "ProtocolLib-Minimum-Version: $sp_protocollib_minimum"
    do
        grep -Fxq "$attribute" <<<"$manifest" || die 65 "missing or mismatched manifest attribute: $attribute"
    done

    local plugin_yml metadata
    plugin_yml=$(unzip -p "$sp_artifact" plugin.yml | tr -d '\r')
    for metadata in \
        'name: TrueUUID' \
        "main: $sp_main_class" \
        "version: '$sp_version'" \
        "api-version: '${sp_game_version%.*}'" \
        'load: STARTUP' \
        'depend: [ProtocolLib]' \
        'softdepend: [LuckPerms]'
    do
        grep -Fxq "$metadata" <<<"$plugin_yml" || die 65 "missing or mismatched plugin.yml value: $metadata"
    done

    local expected_properties actual_properties
    expected_properties=$(printf '%s\n' \
        "target=$expected_target" \
        "platform=$sp_platform" \
        "minecraft=$sp_game_version" \
        "version=$sp_version" \
        "support_status=$sp_manifest_status")
    actual_properties=$(unzip -p "$sp_artifact" trueuuid-target.properties | tr -d '\r')
    [[ "$actual_properties" == "$expected_properties" ]] ||
        die 65 "trueuuid-target.properties does not match ci/server-plugin-targets.json"

    mkdir -p "$output_dir"
    cp -- "$sp_artifact" "$output_dir/"
    (
        cd "$output_dir"
        sha256sum "$(basename -- "$sp_artifact")" > SHA256SUMS
    )
    echo "Verified $sp_platform_name $sp_game_version plugin JAR: $sp_artifact"
}

sp_stop_processes() {
    local attempts=0
    if [[ -n "$sp_runner_pid" ]] && kill -0 "$sp_runner_pid" 2>/dev/null
    then
        kill -TERM "$sp_runner_pid" 2>/dev/null || true
        while (( attempts < 60 )) && kill -0 "$sp_runner_pid" 2>/dev/null
        do
            sleep 1
            attempts=$((attempts + 1))
        done
    fi
    local process_id
    for process_id in "$sp_java_pid" "$sp_runner_pid"
    do
        if [[ -n "$process_id" ]] && kill -0 "$process_id" 2>/dev/null
        then
            kill -KILL "$process_id" 2>/dev/null || true
        fi
    done
    if [[ -n "$sp_runner_pid" ]]
    then
        wait "$sp_runner_pid" 2>/dev/null || true
    fi
}

sp_smoke() {
    local output_dir=$1
    sp_require_tools python3 ss pgrep fuser
    local timeout=${TRUEUUID_SERVER_SMOKE_TIMEOUT:-240}
    [[ "$timeout" =~ ^[1-9][0-9]*$ ]] || die 64 'TRUEUUID_SERVER_SMOKE_TIMEOUT must be a positive integer'
    mkdir -p "$output_dir" "$sp_work_root"

    local server_home port console_log latest_log
    server_home=$(mktemp -d "$sp_work_root/ci-server.XXXXXX")
    console_log="$output_dir/server-console.log"
    latest_log="$server_home/logs/latest.log"
    port=$(python3 -c 'import socket
server = socket.socket()
server.bind(("127.0.0.1", 0))
print(server.getsockname()[1])
server.close()')
    [[ "$port" =~ ^[0-9]+$ ]] || die 69 'failed to allocate a test port'

    TRUEUUID_SERVER_TEST_HOME="$server_home" \
    TRUEUUID_SERVER_PORT="$port" \
    TRUEUUID_SERVER_PLUGIN_AUTO_BUILD=0 \
        "$sp_repo_root/scripts/run-server-plugin.sh" "$expected_target" server AUTO DENY CONSENT_REQUIRED \
        </dev/null >"$console_log" 2>&1 &
    sp_runner_pid=$!
    trap sp_stop_processes EXIT

    local attempts=0
    while (( attempts < 30 ))
    do
        sp_java_pid=$(pgrep -P "$sp_runner_pid" 2>/dev/null | head -n 1 || true)
        [[ -n "$sp_java_pid" ]] && break
        kill -0 "$sp_runner_pid" 2>/dev/null || break
        sleep 1
        attempts=$((attempts + 1))
    done

    local ready_prefix="Secure login bridge enabled: mode=AUTO, offline=DENY, admission=CONSENT_REQUIRED, platform=$sp_platform_name, server="
    local ready_suffix=", minecraft=$sp_game_version, ProtocolLib=$sp_protocollib_runtime"
    local ready=false
    local elapsed=0
    while (( elapsed < timeout ))
    do
        if [[ -f "$latest_log" ]] &&
            grep -F "$ready_prefix" "$latest_log" | grep -F "$ready_suffix" >/dev/null &&
            grep -Eq 'Done \([0-9.]+s\)!' "$latest_log"
        then
            ready=true
            break
        fi
        kill -0 "$sp_runner_pid" 2>/dev/null || break
        sleep 1
        elapsed=$((elapsed + 1))
    done
    if [[ "$ready" != true ]]
    then
        tail -n 160 "$console_log" >&2 || true
        die 1 "$sp_platform_name $sp_game_version smoke did not reach its secure ready marker."
    fi

    # Exercise the same signal-to-console-stop path an administrator uses.
    kill -TERM "$sp_runner_pid"
    local waited=0
    while (( waited < 180 )) && kill -0 "$sp_runner_pid" 2>/dev/null
    do
        sleep 1
        waited=$((waited + 1))
    done
    if kill -0 "$sp_runner_pid" 2>/dev/null
    then
        tail -n 160 "$console_log" >&2 || true
        die 1 "$sp_platform_name wrapper did not finish a clean shutdown after one TERM signal."
    fi
    local runner_status=0
    wait "$sp_runner_pid" || runner_status=$?
    sp_runner_pid=
    sp_java_pid=
    [[ "$runner_status" == 0 ]] || die 1 "$sp_platform_name wrapper exited with status $runner_status"

    cp -- "$latest_log" "$output_dir/server-latest.log"
    cp -- "$server_home/server-artifacts.sha256" "$output_dir/"
    cp -- "$server_home/plugins/TrueUUID/config.yml" "$output_dir/effective-config.yml"

    local marker
    for marker in \
        "TrueUUID $sp_version for $sp_platform_name $sp_game_version" \
        '[TrueUUID] Disabling TrueUUID v' \
        "${smoke_shutdown_markers[@]}"
    do
        grep -Fq "$marker" "$output_dir/server-latest.log" || die 1 "server log is missing marker: $marker"
    done
    grep -Fq 'Server exited cleanly.' "$console_log" || die 1 'wrapper did not report a clean Java exit'
    if grep -Eq 'refused startup|Failed to start the minecraft server|SessionLock\$ExceptionWorldConflict|Address already in use' \
            "$console_log" "$output_dir/server-latest.log"
    then
        die 1 'server log contains a startup refusal or isolation failure'
    fi
    if grep -q $'\e\[' "$output_dir/server-latest.log"
    then
        die 1 'latest.log contains ANSI escape codes from console styling'
    fi
    if [[ -n "$(ss -H -ltn "sport = :$port" 2>/dev/null | head -n 1)" ]]
    then
        die 1 "smoke left TCP port $port open"
    fi
    local world_lock="$server_home/trueuuid-test-world/session.lock"
    [[ -f "$world_lock" ]] || die 1 'smoke did not create the expected world lock'
    if fuser -s "$world_lock"
    then
        die 1 'smoke left the generated world lock held'
    fi
    trap - EXIT
    echo "$sp_platform_name $sp_game_version installed-plugin smoke and one-signal clean shutdown passed on 127.0.0.1:$port."
}

server_plugin_main() {
    sp_load_target
    local operation=${1:-}
    if [[ "$operation" == build && $# -eq 1 ]]
    then
        sp_build
    elif [[ "$operation" == verify && $# -eq 2 ]]
    then
        sp_verify "$2"
    elif [[ "$operation" == smoke && $# -eq 2 ]]
    then
        sp_smoke "$2"
    elif [[ "$operation" == prepare-runtime && $# -eq 1 ]]
    then
        prepare_server_runtime
        sp_print_runtime
    elif [[ "$operation" == fetch-protocollib && $# -eq 1 ]]
    then
        sp_fetch_protocollib
    else
        die 64 "usage: $0 build | verify <output-directory> | smoke <output-directory> | prepare-runtime | fetch-protocollib"
    fi
}
