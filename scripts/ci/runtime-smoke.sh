#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: $0 <target-id> <server|client> <output-directory>" >&2
    exit 64
fi

target_id=$1
role=$2
output_dir=$3
[[ "$target_id" =~ ^(forge|neoforge|fabric)-[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    echo "invalid runtime smoke target: $target_id" >&2
    exit 64
}
command -v jq >/dev/null 2>&1 || { echo 'jq is required' >&2; exit 69; }

case "$role" in
    server|client) ;;
    *) echo "invalid smoke role: $role" >&2; exit 64 ;;
esac

root=$(pwd)
[[ -x "$root/gradlew" && -d "$root/platform/$target_id" ]] || {
    echo "runtime smoke must run from the repository root" >&2
    exit 66
}

./scripts/release/validate-targets.sh >/dev/null
if ! target=$(jq -ce --arg id "$target_id" '
    [.targets[] | select(.id == $id)] as $matches |
    if ($matches | length) == 1 then $matches[0]
    else error("target must appear exactly once")
    end
' release/targets.json 2>/dev/null); then
    echo "target has no runtime smoke configuration: $target_id" >&2
    exit 65
fi

loader=$(jq -r '.loader' <<<"$target")
standalone=$(jq -r '.standalone // false' <<<"$target")
case "$loader" in
    forge) load_pattern='TrueUUID Forge adapter loaded' ;;
    neoforge) load_pattern='TrueUUID NeoForge adapter loaded' ;;
    fabric) load_pattern='TrueUUID Fabric adapter loaded' ;;
    *) echo "unsupported runtime smoke loader: $loader" >&2; exit 65 ;;
esac
if [[ "$target_id" == forge-1.20.1 || "$target_id" == neoforge-1.20.1 ]]; then
    load_pattern='TrueUUID 已经加载'
fi

if [[ "$standalone" == true ]]; then
    task="run${role^}"
    gradle_wrapper="$root/platform/$target_id/gradlew"
    gradle_project="$root/platform/$target_id"
else
    task=":platform:${target_id}:run${role^}"
    gradle_wrapper="$root/gradlew"
    gradle_project="$root"
fi
gradle_command=("$gradle_wrapper" -p "$gradle_project" "$task" --no-daemon --stacktrace)

mkdir -p "$output_dir"
console_log="$output_dir/${role}-console.log"
latest_copy="$output_dir/${role}-latest.log"
started=$(mktemp)
touch "$started"

if [[ "$role" == server ]]; then
    # Every launcher working directory a target's runServer may use: the module
    # run root, the split server run dir (Forge/Fabric), and the repo root.
    for run_dir in \
        "$root/platform/$target_id/run" \
        "$root/platform/$target_id/run/server" \
        "$root/platform/$target_id/runs/server" \
        "$root/run"; do
        mkdir -p "$run_dir"
        printf 'eula=true\n' > "$run_dir/eula.txt"
        properties="$run_dir/server.properties"
        touch "$properties"
        for setting in \
            'online-mode=false' \
            'server-ip=127.0.0.1' \
            'server-port=25565' \
            'level-name=trueuuid-ci-world' \
            'max-tick-time=-1'; do
            key=${setting%%=*}
            if grep -q "^${key}=" "$properties"; then
                sed -i -E "s|^${key}=.*|${setting}|" "$properties"
            else
                printf '%s\n' "$setting" >> "$properties"
            fi
        done
    done
    command=("${gradle_command[@]}")
elif [[ "$loader" == fabric || "$loader" == neoforge ]]; then
    # Loom and NeoForge ModDev own their run-task program arguments. Replacing
    # them with Gradle's --args drops required launcher state (Loom's asset/game
    # flags or ModDev's real main class) and makes a clean CI client fail before
    # Minecraft starts.
    if command -v xvfb-run >/dev/null 2>&1; then
        command=(env LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a --server-args=-screen\ 0\ 1280x720x24
            "${gradle_command[@]}")
    elif [[ -n "${DISPLAY:-}" ]]; then
        command=(env LIBGL_ALWAYS_SOFTWARE=1 "${gradle_command[@]}")
    else
        echo 'client smoke needs xvfb-run or an existing DISPLAY' >&2
        exit 69
    fi
else
    if command -v xvfb-run >/dev/null 2>&1; then
        command=(env LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a --server-args=-screen\ 0\ 1280x720x24
            "${gradle_command[@]}"
            '--args=--username TrueUUIDCI --width 854 --height 480')
    elif [[ -n "${DISPLAY:-}" ]]; then
        command=(env LIBGL_ALWAYS_SOFTWARE=1 "${gradle_command[@]}"
            '--args=--username TrueUUIDCI --width 854 --height 480')
    else
        echo 'client smoke needs xvfb-run or an existing DISPLAY' >&2
        exit 69
    fi
fi

pid=
collect_descendants() {
    local parent=$1
    local child
    while IFS= read -r child; do
        [[ -n "$child" ]] || continue
        collect_descendants "$child"
        printf '%s\n' "$child"
    done < <(pgrep -P "$parent" 2>/dev/null || true)
}

stop_process() {
    local -a process_ids=()
    local process_id
    local alive
    if [[ -n "$pid" ]]; then
        mapfile -t process_ids < <(collect_descendants "$pid")
        process_ids+=("$pid")
        kill -INT -- "-$pid" 2>/dev/null || true
        kill -INT "${process_ids[@]}" 2>/dev/null || true
        for _ in {1..15}; do
            alive=false
            for process_id in "${process_ids[@]}"; do
                if kill -0 "$process_id" 2>/dev/null; then
                    alive=true
                    break
                fi
            done
            [[ "$alive" == true ]] || break
            sleep 1
        done
        kill -KILL -- "-$pid" 2>/dev/null || true
        kill -KILL "${process_ids[@]}" 2>/dev/null || true
        wait "$pid" 2>/dev/null || true
    fi
    rm -f "$started"
}
trap stop_process EXIT INT TERM

setsid "${command[@]}" >"$console_log" 2>&1 &
pid=$!
success=false
latest_log=
smoke_timeout=${TRUEUUID_SMOKE_TIMEOUT:-900}
[[ "$smoke_timeout" =~ ^[1-9][0-9]*$ ]] || {
    echo 'TRUEUUID_SMOKE_TIMEOUT must be a positive integer number of seconds' >&2
    exit 64
}

for (( elapsed = 0; elapsed < smoke_timeout; elapsed++ )); do
    latest_log=$(find "$root/platform/$target_id" "$root/run" \
        -path '*/logs/latest.log' -type f -newer "$started" -print 2>/dev/null | head -n 1 || true)
    if [[ -n "$latest_log" ]] && grep -Fq "$load_pattern" "$latest_log"; then
        if [[ "$role" == server ]] && grep -Eq 'Done \([0-9.]+s\)!' "$latest_log"; then
            success=true
            break
        fi
        if [[ "$role" == client ]] && grep -Eq '\[Render thread/INFO\].*(Setting user|Backend library|Reloading ResourceManager|Created:)' "$latest_log"; then
            success=true
            break
        fi
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
        break
    fi
    sleep 1
done

if [[ -n "$latest_log" && -f "$latest_log" ]]; then
    cp "$latest_log" "$latest_copy"
fi

if [[ "$success" != true ]]; then
    echo "$target_id $role smoke did not reach its ready marker" >&2
    tail -n 120 "$console_log" >&2 || true
    if [[ -f "$latest_copy" ]]; then
        tail -n 120 "$latest_copy" >&2 || true
    fi
    exit 1
fi

echo "$target_id $role smoke reached its ready marker."
