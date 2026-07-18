#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <jar> <mixin-probe-class>" >&2
    exit 64
fi

jar_path=$1
probe_class=$2

[[ -f "$jar_path" ]] || { echo "missing SRG-era artifact: $jar_path" >&2; exit 66; }
[[ "$probe_class" =~ ^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$ ]] || {
    echo "invalid mixin probe class: $probe_class" >&2
    exit 64
}

entries=$(jar tf "$jar_path")
grep -Fxq 'trueuuid.refmap.json' <<<"$entries" || {
    echo "SRG-era artifact has no trueuuid.refmap.json: $jar_path" >&2
    exit 65
}

manifest=$(unzip -p "$jar_path" META-INF/MANIFEST.MF | tr -d '\r')
grep -Eq '^MixinConfigs: trueuuid\.mixins\.json$' <<<"$manifest" || {
    echo "SRG-era artifact has no MixinConfigs manifest registration: $jar_path" >&2
    exit 65
}

unzip -p "$jar_path" trueuuid.refmap.json | jq -e '
    (.mappings | type == "object" and length > 0) and
    (.data.searge | type == "object" and length > 0)
' >/dev/null || {
    echo "SRG-era artifact has an empty or malformed refmap: $jar_path" >&2
    exit 65
}

probe_output=$(javap -classpath "$jar_path" -p -c "$probe_class")
method_count=$(grep -Eo '\bm_[0-9]+_\(' <<<"$probe_output" | wc -l || true)
field_count=$(grep -Eo '\bf_[0-9]+_;' <<<"$probe_output" | wc -l || true)

(( method_count > 0 )) || {
    echo "mixin probe contains no SRG method names: $probe_class" >&2
    exit 65
}
(( field_count > 0 )) || {
    echo "mixin probe contains no SRG shadow fields: $probe_class" >&2
    exit 65
}

echo "Verified SRG mixin artifact: $jar_path (methods=$method_count fields=$field_count)"
