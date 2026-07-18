#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <release-changelog.md>" >&2
    exit 64
fi

changelog=$1
[[ -s "$changelog" ]] || { echo "release changelog is missing or empty: $changelog" >&2; exit 66; }

iconv -f UTF-8 -t UTF-8 "$changelog" >/dev/null || {
    echo "release changelog must be valid UTF-8" >&2
    exit 65
}
if LC_ALL=C grep -q $'\r' "$changelog"; then
    echo "release changelog must use LF line endings" >&2
    exit 65
fi

awk '
    function nonblank(value) { return value ~ /[^[:space:]]/ }

    nonblank($0) && first_nonblank == 0 {
        first_nonblank = NR
        if ($0 != "## English") {
            print "the first nonblank changelog line must be: ## English" > "/dev/stderr"
            invalid = 1
            exit 1
        }
    }

    $0 == "## English" {
        english_headings++
        if (english_headings > 1 || chinese_headings > 0) {
            print "the English changelog heading must occur exactly once and first" > "/dev/stderr"
            invalid = 1
            exit 1
        }
        section = "english"
        next
    }

    $0 == "## 中文" {
        chinese_headings++
        if (english_headings != 1 || chinese_headings > 1) {
            print "the Chinese changelog heading must occur exactly once after English" > "/dev/stderr"
            invalid = 1
            exit 1
        }
        section = "chinese"
        next
    }

    /^## / {
        print "only the ## English and ## 中文 language sections are allowed" > "/dev/stderr"
        invalid = 1
        exit 1
    }

    nonblank($0) && section == "english" { english_content = 1 }
    nonblank($0) && section == "chinese" { chinese_content = 1 }

    END {
        if (invalid == 1) {
            exit 1
        }
        if (first_nonblank == 0 || english_headings != 1 || chinese_headings != 1 ||
            english_content != 1 || chinese_content != 1) {
            print "changelog requires nonempty ## English and ## 中文 sections, in that order" > "/dev/stderr"
            exit 1
        }
    }
' "$changelog" || exit 65

echo "Verified English-first bilingual changelog: $changelog"
