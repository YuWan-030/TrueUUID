#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: $0 <target-id> <server|client> <output-directory>" >&2
    exit 64
fi

target_id=$1
role=$2
output_dir=$3

case "$role" in
    server|client) ;;
    *) echo "invalid smoke role: $role" >&2; exit 64 ;;
esac

case "$target_id" in
    forge-1.20.1)
        task=":platform:forge-1.20.1:run${role^}"
        load_pattern='TrueUUID 已经加载'
        ;;
    forge-1.21.1|forge-1.21.3|forge-1.21.4|forge-1.21.5|forge-1.21.8)
        task=":platform:${target_id}:run${role^}"
        load_pattern='TrueUUID Forge adapter loaded'
        ;;
    neoforge-1.21.1)
        task=":platform:neoforge-1.21.1:run${role^}"
        load_pattern='TrueUUID NeoForge 1.21.1 adapter loaded'
        ;;
    *) echo "target has no runtime smoke configuration: $target_id" >&2; exit 65 ;;
esac

root=$(pwd)
[[ -x "$root/gradlew" && -d "$root/platform/$target_id" ]] || {
    echo "runtime smoke must run from the repository root" >&2
    exit 66
}

mkdir -p "$output_dir"
console_log="$output_dir/${role}-console.log"
latest_copy="$output_dir/${role}-latest.log"
started=$(mktemp)
touch "$started"

if [[ "$role" == server ]]; then
    for run_dir in "$root/platform/$target_id/run" "$root/run"; do
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
    command=(./gradlew "$task" --no-daemon --stacktrace)
else
    command=(env LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a --server-args=-screen\ 0\ 1280x720x24
        ./gradlew "$task" --no-daemon --stacktrace
        '--args=--username TrueUUIDCI --width 854 --height 480')
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

for _ in {1..240}; do
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
