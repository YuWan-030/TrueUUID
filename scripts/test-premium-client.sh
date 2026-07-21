#!/usr/bin/env bash
# Launch a REAL premium (Microsoft/Mojang) Minecraft client for one TrueUUID
# target, with the freshly built mod jar, so the premium session path
# (joinServer / hasJoined) can actually be exercised. Unlike
# run-dev-target.sh, whose Gradle runClient uses a throwaway dev account, this
# authenticates a real account through portablemc.
#
# Auth is Microsoft device-code: portablemc prints a code, you approve it in a
# browser once, and it caches a REFRESH token (never your password). The token
# store lives OUTSIDE the repo under ~/.local/share/trueuuid-testclient and is
# chmod 700. Never commit it; never paste it anywhere.
#
# Pair this with the offline-mode dev server from run-dev-target.sh:
#   Terminal 1:  scripts/run-dev-target.sh fabric-1.20.1 server
#   Terminal 2:  scripts/test-premium-client.sh fabric-1.20.1
# Then in-game: Multiplayer -> Direct Connect -> 127.0.0.1
# (Minecraft >= 1.20 dropped the --server launch arg, so this one click is
#  the only manual step.)
set -euo pipefail

# Any file this script (or portablemc) creates under the test home — above all
# the cached auth/refresh token — stays private (files 600, dirs 700).
umask 077

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
targets_file="$root/release/targets.json"

# All test state (auth db, shared asset cache, per-target run dirs) stays out of
# the repo. Override the base with TRUEUUID_TEST_HOME if you must.
test_home="${TRUEUUID_TEST_HOME:-$HOME/.local/share/trueuuid-testclient}"
main_dir="$test_home/shared"   # assets, libraries, versions, portablemc auth db (shared across targets)

usage() {
    cat <<'EOF'
Usage:
  scripts/test-premium-client.sh <target>     Launch a premium client for <target>
  scripts/test-premium-client.sh login        Sign in once (Microsoft device code)
  scripts/test-premium-client.sh logout        Forget the cached account

Registered targets come from release/targets.json:
EOF
    if command -v jq >/dev/null 2>&1 && [[ -f "$targets_file" ]]; then
        jq -r '.targets[] | "  \(.id)  \(.loader) / Minecraft \(.game_version)"' "$targets_file"
    fi
    cat <<'EOF'

Account: set TRUEUUID_MS_EMAIL=you@example.com, or put it in
  ~/.local/share/trueuuid-testclient/email
Use a dedicated/secondary Mojang account if you have one.

Env overrides:
  TRUEUUID_MS_EMAIL       Microsoft account email (else read from the file above)
  TRUEUUID_PORTABLEMC     portablemc invocation (default: portablemc)
  TRUEUUID_GAME_JVM       JDK path to run the game (else chosen from the target's Java level)
  TRUEUUID_LOADER_VERSION Pin the loader build (e.g. 47.4.10); default is portablemc's recommended
  TRUEUUID_FABRIC_API_JAR Path to a Fabric API jar (fabric targets need it; else auto-located)
  TRUEUUID_BUILD_JAVA     JDK 21 path used only if the mod jar must be built (default: java-21-openjdk)
  TRUEUUID_TEST_HOME      Base dir for auth/cache/run state (default: ~/.local/share/trueuuid-testclient)
EOF
}

cmd="${1:-}"
case "$cmd" in ''|-h|--help|help) usage; exit 0 ;; esac

command -v jq >/dev/null 2>&1 || { echo 'jq is required.' >&2; exit 69; }

# portablemc may be a bare command or "python3 -m portablemc".
read -r -a portablemc <<<"${TRUEUUID_PORTABLEMC:-portablemc}"
if ! command -v "${portablemc[0]}" >/dev/null 2>&1; then
    cat >&2 <<EOF
portablemc is not installed. Install it once (it is a small, maintained CLI):
  pipx install portablemc      # recommended
  # or: python3 -m pip install --user portablemc
Then re-run this script. Override the command with TRUEUUID_PORTABLEMC if needed.
EOF
    exit 69
fi

mkdir -p "$main_dir"
chmod 700 "$test_home" "$main_dir" 2>/dev/null || true

resolve_email() {
    if [[ -n "${TRUEUUID_MS_EMAIL:-}" ]]; then
        printf '%s' "$TRUEUUID_MS_EMAIL"; return 0
    fi
    local file="$test_home/email"
    if [[ -f "$file" ]]; then
        tr -d '[:space:]' < "$file"; return 0
    fi
    echo 'No account email. Set TRUEUUID_MS_EMAIL or write it to '"$test_home/email" >&2
    return 1
}

case "$cmd" in
    login)
        email="$(resolve_email)"
        "${portablemc[@]}" --main-dir "$main_dir" login "$email"
        chmod 600 "$main_dir"/*auth*.json 2>/dev/null || true
        exit 0
        ;;
    logout)
        email="$(resolve_email)"
        exec "${portablemc[@]}" --main-dir "$main_dir" logout "$email"
        ;;
esac

target="$cmd"
if ! meta=$(jq -ce --arg id "$target" '
    [.targets[] | select(.id == $id)] as $m |
    if ($m|length)==1 then $m[0] else error("target must appear exactly once") end
' "$targets_file" 2>/dev/null); then
    printf 'Target %q is not registered.\n\n' "$target" >&2
    usage >&2
    exit 64
fi

loader=$(jq -r '.loader' <<<"$meta")
game_version=$(jq -r '.game_version' <<<"$meta")
game_java=$(jq -r '.java' <<<"$meta")
artifact_tmpl=$(jq -r '.artifact' <<<"$meta")
build_task=$(jq -r '.build_task' <<<"$meta")

mod_version=$(sed -n 's/^mod_version=//p' "$root/gradle.properties" | tr -d '[:space:]')
[[ -n "$mod_version" ]] || { echo 'Could not read mod_version from gradle.properties.' >&2; exit 70; }
artifact="$root/${artifact_tmpl//%VERSION%/$mod_version}"

# Build the mod jar on demand (Gradle needs a Java 21 launcher; the game JVM is
# chosen separately below).
if [[ ! -f "$artifact" ]]; then
    build_java="${TRUEUUID_BUILD_JAVA:-/usr/lib/jvm/java-21-openjdk-amd64}"
    [[ -x "$build_java/bin/java" ]] || { echo "Need a JDK 21 to build; set TRUEUUID_BUILD_JAVA." >&2; exit 78; }
    echo "Mod jar missing; building $build_task ..."
    ( cd "$root" && JAVA_HOME="$build_java" ./gradlew "$build_task" --no-daemon )
fi
[[ -f "$artifact" ]] || { echo "Build did not produce $artifact" >&2; exit 70; }

# Pick a game JVM matching the target's Java level (the shell default may be a
# JDK the game cannot run on).
if [[ -n "${TRUEUUID_GAME_JVM:-}" ]]; then
    game_jvm="$TRUEUUID_GAME_JVM"
elif [[ "$game_java" == "17" ]]; then
    game_jvm="/usr/lib/jvm/jdk-17.0.12-oracle-x64"
else
    game_jvm="/usr/lib/jvm/java-21-openjdk-amd64"
fi
[[ -x "$game_jvm/bin/java" ]] || { echo "Game JDK $game_java not found; set TRUEUUID_GAME_JVM." >&2; exit 78; }

# portablemc version spec. A per-target work dir isolates mods/saves while the
# big asset/library cache and the auth db stay shared in main_dir.
loader_suffix="${TRUEUUID_LOADER_VERSION:+-$TRUEUUID_LOADER_VERSION}"
case "$loader" in
    fabric)   version_spec="fabric:${game_version}${loader_suffix}" ;;
    forge)    version_spec="forge:${game_version}${loader_suffix}" ;;
    neoforge) version_spec="neoforge:${game_version}${loader_suffix}" ;;
    *) echo "Unsupported loader: $loader" >&2; exit 65 ;;
esac

work_dir="$test_home/work/$target"
mods_dir="$work_dir/mods"
mkdir -p "$mods_dir"
# Only this target's mods for this run: clear then stage.
rm -f "$mods_dir"/*.jar
cp "$artifact" "$mods_dir/"

# Fabric clients also need the Fabric API mod present.
if [[ "$loader" == "fabric" ]]; then
    fabric_api="${TRUEUUID_FABRIC_API_JAR:-}"
    if [[ -z "$fabric_api" ]]; then
        coord=$(grep -oE "net\.fabricmc\.fabric-api:fabric-api:[0-9A-Za-z.+-]+" \
            "$root/platform/$target/build.gradle" | head -1 | awk -F: '{print $3}')
        if [[ -n "$coord" ]]; then
            fabric_api=$(find "$HOME/.gradle/caches" -name "fabric-api-$coord.jar" 2>/dev/null | head -1)
        fi
    fi
    if [[ -n "$fabric_api" && -f "$fabric_api" ]]; then
        cp "$fabric_api" "$mods_dir/"
        echo "Staged Fabric API: $(basename "$fabric_api")"
    else
        echo "WARNING: Fabric API jar not found. Set TRUEUUID_FABRIC_API_JAR to it, or the client will not load the mod." >&2
    fi
fi

email="$(resolve_email)"
echo "----------------------------------------------------------------"
echo "Target        : $target ($loader $game_version, game JDK $game_java)"
echo "Mod jar       : $(basename "$artifact")"
echo "Account       : $email (Microsoft; token cached in $main_dir, 700)"
echo "portablemc    : ${version_spec}"
echo "After it opens: Multiplayer -> Direct Connect -> 127.0.0.1"
echo "----------------------------------------------------------------"

exec "${portablemc[@]}" --main-dir "$main_dir" --work-dir "$work_dir" \
    start --login "$email" --jvm "$game_jvm/bin/java" "$version_spec"
