#!/usr/bin/env bash
# Reclaim disk from local development: Gradle build outputs, dev-server run
# dirs/caches, and the portablemc premium-client downloads. Everything it removes is
# regenerable (rebuilt by Gradle, re-downloaded by portablemc). Source is never
# touched, and your cached login is KEPT unless you pass --logout.
#
#   scripts/clean-dev.sh              # build + client caches (keeps login), asks first
#   scripts/clean-dev.sh --build      # only repo build/run/Gradle outputs
#   scripts/clean-dev.sh --client     # only portablemc downloads (keeps login)
#   scripts/clean-dev.sh --dry-run    # show what would be freed, delete nothing
#   scripts/clean-dev.sh --all --logout -y   # everything incl. token, no prompt
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_home="${TRUEUUID_TEST_HOME:-$HOME/.local/share/trueuuid-testclient}"

scope="all"; dry=0; assume_yes=0; logout=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --build) scope="build" ;;
        --client) scope="client" ;;
        --all) scope="all" ;;
        --logout) logout=1 ;;
        --dry-run|-n) dry=1 ;;
        -y|--yes) assume_yes=1 ;;
        -h|--help)
            sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 64 ;;
    esac
    shift
done

# Collect existing paths to remove, per scope. Only ever named
# build/run/runs/.gradle dirs in the repo and the regenerable caches under the
# test home.
targets=()
add() { [[ -e "$1" ]] && targets+=("$1"); return 0; }

if [[ "$scope" == "build" || "$scope" == "all" ]]; then
    add "$root/build"
    add "$root/.gradle"
    for d in \
        "$root"/platform/*/build \
        "$root"/platform/*/run \
        "$root"/platform/*/runs \
        "$root"/platform/*/.gradle \
        "$root"/shared/*/build; do
        add "$d"
    done
fi
if [[ "$scope" == "client" || "$scope" == "all" ]]; then
    # Everything portablemc can re-download, but keep the auth db by default.
    if [[ -d "$test_home/shared" ]]; then
        while IFS= read -r -d '' p; do
            [[ "$logout" -eq 0 && "$p" == *auth*.json ]] && continue
            targets+=("$p")
        done < <(find "$test_home/shared" -mindepth 1 -maxdepth 1 -print0)
    fi
    add "$test_home/work"
    if [[ "$logout" -eq 1 ]]; then
        add "$test_home/email"
        for f in "$test_home/shared"/*auth*.json; do add "$f"; done
    fi
fi

if [[ ${#targets[@]} -eq 0 ]]; then
    echo "Nothing to clean (scope: $scope)."
    exit 0
fi

echo "Will remove (scope: $scope$([[ $logout -eq 1 ]] && echo ', + login')):"
for p in "${targets[@]}"; do
    printf '  %6s  %s\n' "$(du -sh "$p" 2>/dev/null | cut -f1)" "${p/#$HOME/~}"
done
total="$(du -sch "${targets[@]}" 2>/dev/null | tail -1 | cut -f1)"
echo "  ------  total: $total"

if [[ "$logout" -eq 0 && ( "$scope" == "client" || "$scope" == "all" ) ]]; then
    echo "Login is KEPT (portablemc_auth.json). Use --logout to also remove it."
fi

if [[ "$dry" -eq 1 ]]; then
    echo "(dry run — nothing deleted)"
    exit 0
fi

if [[ "$assume_yes" -eq 0 ]]; then
    read -r -p "Proceed? [y/N] " answer
    [[ "$answer" == [yY]* ]] || { echo "Aborted."; exit 0; }
fi

rm -rf -- "${targets[@]}"
echo "Freed $total."
