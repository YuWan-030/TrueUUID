#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <output-directory>" >&2
    exit 64
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
expected_target='spigot-1.20.1'
[[ "${TRUEUUID_SERVER_PLUGIN_TARGET:-$expected_target}" == "$expected_target" ]] || {
    echo "This exact runtime smoke only supports $expected_target." >&2
    exit 65
}
output_dir=$1
runner="$repo_root/scripts/run-spigot-candidate.sh"
work_root="$repo_root/build/spigot-candidate"
mkdir -p "$work_root"
server_home=$(mktemp -d "$work_root/ci-server.XXXXXX")
console_log="$output_dir/server-console.log"
latest_copy="$output_dir/server-latest.log"
smoke_timeout=${TRUEUUID_SPIGOT_SMOKE_TIMEOUT:-180}

[[ "$smoke_timeout" =~ ^[1-9][0-9]*$ ]] || {
    echo 'TRUEUUID_SPIGOT_SMOKE_TIMEOUT must be a positive integer' >&2
    exit 64
}
command -v python3 >/dev/null 2>&1 || { echo 'python3 is required' >&2; exit 69; }
command -v ss >/dev/null 2>&1 || { echo 'ss is required' >&2; exit 69; }
command -v pgrep >/dev/null 2>&1 || { echo 'pgrep is required' >&2; exit 69; }
command -v fuser >/dev/null 2>&1 || { echo 'fuser is required' >&2; exit 69; }
mkdir -p "$output_dir"

server_port=$(python3 - <<'PY'
import socket
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
)
[[ "$server_port" =~ ^[0-9]+$ ]] || { echo 'failed to allocate a test port' >&2; exit 69; }

runner_pid=
java_pid=
cleanup_processes() {
    local process_id
    if [[ -n "$runner_pid" ]] && kill -0 "$runner_pid" 2>/dev/null; then
        kill -TERM "$runner_pid" 2>/dev/null || true
        for _ in {1..30}; do
            kill -0 "$runner_pid" 2>/dev/null || break
            sleep 1
        done
    fi
    for process_id in "$java_pid" "$runner_pid"; do
        if [[ -n "$process_id" ]] && kill -0 "$process_id" 2>/dev/null; then
            kill -KILL "$process_id" 2>/dev/null || true
        fi
    done
    if [[ -n "$runner_pid" ]]; then
        wait "$runner_pid" 2>/dev/null || true
    fi
}
trap cleanup_processes EXIT

TRUEUUID_SPIGOT_TEST_HOME="$server_home" \
TRUEUUID_SPIGOT_SERVER_PORT="$server_port" \
TRUEUUID_SPIGOT_AUTO_BUILD=0 \
    "$runner" server AUTO DENY CONSENT_REQUIRED \
    </dev/null >"$console_log" 2>&1 &
runner_pid=$!

for _ in {1..30}; do
    java_pid=$(pgrep -P "$runner_pid" 2>/dev/null | head -n 1 || true)
    [[ -n "$java_pid" ]] && break
    kill -0 "$runner_pid" 2>/dev/null || break
    sleep 1
done

latest_log="$server_home/logs/latest.log"
ready=false
for ((elapsed = 0; elapsed < smoke_timeout; elapsed++)); do
    if [[ -f "$latest_log" ]] \
            && grep -Fq 'Secure login bridge enabled: mode=AUTO, offline=DENY, admission=CONSENT_REQUIRED, Spigot=3871-Spigot-d2eba2c-3f9263b, ProtocolLib=5.1.0' "$latest_log" \
            && grep -Eq 'Done \([0-9.]+s\)!' "$latest_log"; then
        ready=true
        break
    fi
    if ! kill -0 "$runner_pid" 2>/dev/null; then
        break
    fi
    sleep 1
done

if [[ "$ready" != true ]]; then
    echo 'Spigot 1.20.1 smoke did not reach its secure ready marker.' >&2
    tail -n 160 "$console_log" >&2 || true
    [[ -f "$latest_log" ]] && tail -n 160 "$latest_log" >&2 || true
    exit 1
fi

# Exercise the same signal-to-console-stop path used by an administrator.
kill -TERM "$runner_pid"
shutdown_complete=false
for _ in {1..120}; do
    if ! kill -0 "$runner_pid" 2>/dev/null; then
        shutdown_complete=true
        break
    fi
    sleep 1
done
if [[ "$shutdown_complete" != true ]]; then
    echo 'Spigot wrapper did not complete clean shutdown after one TERM signal.' >&2
    tail -n 160 "$console_log" >&2 || true
    exit 1
fi

set +e
wait "$runner_pid"
runner_status=$?
set -e
runner_pid=
java_pid=
[[ "$runner_status" == 0 ]] || {
    echo "Spigot wrapper exited with status $runner_status" >&2
    exit 1
}

cp "$latest_log" "$latest_copy"
cp "$server_home/candidate-artifacts.sha256" "$output_dir/"
cp "$server_home/plugins/TrueUUID/config.yml" "$output_dir/effective-config.yml"

for marker in \
    'Stopping the server' \
    '[TrueUUID] Disabling TrueUUID v' \
    'ThreadedAnvilChunkStorage: All dimensions are saved'; do
    grep -Fq "$marker" "$latest_copy" || {
        echo "Spigot shutdown log is missing marker: $marker" >&2
        exit 1
    }
done
grep -Fq 'Server exited cleanly.' "$console_log" || {
    echo 'Spigot wrapper did not report a clean collected Java exit.' >&2
    exit 1
}
if grep -Eq 'Failed to start the minecraft server|SessionLock\$ExceptionWorldConflict|Address already in use' \
        "$console_log" "$latest_copy"; then
    echo 'Spigot smoke log contains a startup or isolation failure.' >&2
    exit 1
fi
if [[ -n "$(ss -H -ltn "sport = :$server_port" 2>/dev/null | head -n 1)" ]]; then
    echo "Spigot smoke left TCP port $server_port open." >&2
    exit 1
fi
world_lock="$server_home/trueuuid-spigot-test-world/session.lock"
[[ -f "$world_lock" ]] || {
    echo 'Spigot smoke did not create the expected world lock.' >&2
    exit 1
}
if fuser -s "$world_lock"; then
    echo 'Spigot smoke left the generated world lock held.' >&2
    exit 1
fi

trap - EXIT
echo "Spigot 1.20.1 installed-plugin smoke and one-signal clean shutdown passed on 127.0.0.1:$server_port."
