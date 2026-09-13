#!/usr/bin/env bash
# Strict validator and machine-readable resolver for ci/server-plugin-targets.json.
#
# Without arguments it validates the whole inventory. With --target or
# --approved it prints exactly one resolved target as JSON on stdout. Release
# jobs, CI dispatch, Gradle-independent scripts, and the local runner all read
# targets through this one interface.
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
manifest="$repo_root/ci/server-plugin-targets.json"

die() {
    local status=$1
    shift
    printf '%s\n' "$*" >&2
    exit "$status"
}

if [[ $# -ne 0 && ! ( $# -eq 2 && ( $1 == --target || $1 == --approved ) ) ]]
then
    die 64 "usage: $0 [--target <target-id> | --approved <target-id>]"
fi
command -v jq >/dev/null 2>&1 || die 69 'jq is required'
[[ -f "$manifest" ]] || die 66 "missing server-plugin manifest: $manifest"

project_version=$(sed -n 's/^mod_version=//p' "$repo_root/gradle.properties" | tr -d '[:space:]')
[[ "$project_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] ||
    die 65 'gradle.properties must contain one strict SemVer mod_version'

# A plugin approval is bound to one repository version, exactly like the mod
# manifest. Only a supported target may be approved. Store project IDs are not
# versioned here. They live in GitHub Actions variables, and the Release
# workflow probes every store an approved plugin goes to before uploading.
jq -e --arg project_version "$project_version" '
    (keys | sort) == (["distribution", "protocollib", "release_version", "schema_version", "targets"] | sort) and
    .schema_version == 4 and
    .release_version == $project_version and

    (.distribution | type == "object" and
        (keys | sort) == (["hangar_channel", "hangar_owner", "hangar_slug"] | sort) and
        (.hangar_owner | type == "string" and test("^[A-Za-z0-9_-]{1,32}$")) and
        (.hangar_slug | type == "string" and test("^[A-Za-z0-9_.-]{1,64}$")) and
        (.hangar_channel | type == "string" and test("^[A-Za-z0-9_-]{1,32}$"))) and

    (.protocollib | type == "object" and
        (keys | sort) == (["compile_version", "minimum_version", "runtime_version", "verified"] | sort) and
        (.verified | type == "array" and length > 0 and
            all(type == "object" and (keys | sort) == (["sha256", "version"]) and
                (.version | type == "string" and test("^[0-9]+\\.[0-9]+\\.[0-9]+$")) and
                (.sha256 | type == "string" and test("^[0-9a-f]{64}$"))) and
            ([.[].version] | unique | length) == length) and
        (.minimum_version as $minimum | .compile_version as $compile | .runtime_version as $runtime |
            [.verified[].version] |
            index($minimum) != null and index($compile) != null and index($runtime) != null)) and

    (.targets | type == "array" and length > 0 and
        all(
            (keys | sort) == ([
                "ci_script", "engine_module", "game_version", "hangar_platform", "id", "java",
                "modrinth_loaders", "module", "platform", "platform_name", "release", "support_status"
            ] | sort) and
            (.platform == "spigot" or .platform == "paper") and
            (.platform_name == (if .platform == "spigot" then "Spigot" else "Paper" end)) and
            (.game_version | type == "string" and test("^[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) and
            (.id == (.platform + "-" + .game_version)) and
            (.java == 17 or .java == 21) and
            (.module == (":plugin:" + .platform + ":" + .game_version)) and
            (.engine_module == (":plugin:craftbukkit:" + .game_version)) and
            (.support_status == "unsupported-candidate" or .support_status == "supported") and
            (.release | type == "boolean") and
            (.release == false or .support_status == "supported") and
            (.modrinth_loaders == [.platform]) and
            (.hangar_platform == (if .platform == "paper" then "PAPER" else null end)) and
            (.ci_script == ("scripts/ci/server-plugins/" + .id + ".sh"))
        ) and
        ([.[].id] | unique | length) == length)
' "$manifest" >/dev/null || die 65 'server-plugin manifest failed strict schema validation'

# The Java startup gate must accept exactly the tested ProtocolLib minimum.
minimum=$(jq -r '.protocollib.minimum_version' "$manifest")
minimum_java="MINIMUM_VERSION = new Version(${minimum//./, })"

while IFS= read -r target
do
    target_id=$(jq -r '.id' <<<"$target")
    platform=$(jq -r '.platform' <<<"$target")
    platform_name=$(jq -r '.platform_name' <<<"$target")
    game_version=$(jq -r '.game_version' <<<"$target")
    module=$(jq -r '.module' <<<"$target")
    engine_module=$(jq -r '.engine_module' <<<"$target")
    ci_script=$(jq -r '.ci_script' <<<"$target")
    module_dir="$repo_root/plugin/$platform/$game_version"
    engine_dir="$repo_root/plugin/craftbukkit/$game_version"
    version_package="v${game_version//./_}"

    [[ -d "$module_dir" ]] || die 66 "server-plugin module directory is missing for $target_id"
    [[ -d "$engine_dir" ]] || die 66 "engine module directory is missing for $target_id"
    grep -Fq "include '${module#:}'" "$repo_root/settings.gradle" || die 65 "settings.gradle does not include $module"
    grep -Fq "include '${engine_module#:}'" "$repo_root/settings.gradle" ||
        die 65 "settings.gradle does not include $engine_module"
    grep -Fq "apply from: rootProject.file('plugin/server-plugin-target.gradle')" "$module_dir/build.gradle" ||
        die 65 "$module must apply plugin/server-plugin-target.gradle"
    main_source="$module_dir/src/main/java/cn/alini/trueuuid/$platform/$version_package/Trueuuid${platform_name}Plugin.java"
    [[ -f "$main_source" ]] || die 66 "$target_id lacks its main class source: $main_source"
    grep -Fq "ServerPlatform.${platform^^}" "$module_dir/src/main/java/cn/alini/trueuuid/$platform/$version_package/${platform_name}Platform.java" ||
        die 65 "$target_id platform adapter does not declare ServerPlatform.${platform^^}"
    grep -RFq "$minimum_java" "$engine_dir/src/main/java" ||
        die 65 "$engine_module does not enforce the manifest ProtocolLib minimum $minimum"
    [[ -f "$repo_root/$ci_script" && -x "$repo_root/$ci_script" ]] ||
        die 66 "$target_id references a missing or non-executable CI script: $ci_script"
    grep -Fxq "expected_target='$target_id'" "$repo_root/$ci_script" ||
        die 65 "$target_id CI script is not pinned to that exact target: $ci_script"
done < <(jq -c '.targets[]' "$manifest")

if [[ $# -eq 0 ]]
then
    echo 'Verified server-plugin target inventory.'
    exit 0
fi

target_id=$2
[[ "$target_id" =~ ^(spigot|paper)-[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || die 64 "invalid server-plugin target id: $target_id"

# stdout is a machine-readable API. The artifact path, manifest marker, Actions
# artifact namespace, and distribution coordinates are derived here, and
# plugin/server-plugin-target.gradle applies the same support_status rule.
jq -ce --arg id "$target_id" --arg mode "$1" '
    . as $root |
    [.targets[] | select(.id == $id)] as $matches |
    if ($matches | length) != 1 then
        error("server-plugin target must appear exactly once")
    elif $mode == "--approved" and $matches[0].release != true then
        error("server-plugin target is not release-approved")
    else
        $matches[0] |
        ("plugin/" + .platform + "/" + .game_version + "/build/libs/trueuuid-") as $libs |
        . + {
            artifact: (if .support_status == "supported"
                then $libs + "%VERSION%-" + .id + ".jar"
                else $libs + .id + "-candidate.jar" end),
            manifest_status: (if .support_status == "supported"
                then "SUPPORTED" else "UNSUPPORTED-CANDIDATE" end),
            artifact_name: ((if .release then "release-" else "tested-" end) + .id),
            curseforge_project_variable: "CURSEFORGE_ID_BUKKIT",
            hangar: (if .hangar_platform == null then null else {
                owner: $root.distribution.hangar_owner,
                slug: $root.distribution.hangar_slug,
                channel: $root.distribution.hangar_channel,
                platform: .hangar_platform
            } end)
        }
    end
' "$manifest" || die 65 "refusing server-plugin target: $target_id"
