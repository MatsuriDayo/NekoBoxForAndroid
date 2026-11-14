#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
source "$repo/buildScript/lib/core/get_source_env.sh"
export GOTOOLCHAIN=go1.25.5
test "$(git -C "$repo/../sing-box" rev-parse HEAD)" = "$COMMIT_SING_BOX"
test -z "$(git -C "$repo/../sing-box" status --porcelain --untracked-files=all)"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/app/libcore"
ln -s "$repo/../sing-box" "$work/sing-box"
ln -s "$repo/../libneko" "$work/libneko"
files=(box.go box_include.go certs.go cert_options.go dns_box.go geo_rules.go geoip.go geosite.go
       platform_java.go sniff_compat.go compat_host_test.go dns_callback_test.go)
for file in "${files[@]}" go.mod go.sum; do
    cp "$repo/libcore/$file" "$work/app/libcore/"
done
cp -R "$repo/libcore/device" "$work/app/libcore/device"
if [ -n "${NEKO_GEO_ASSETS:-}" ]; then
    cp "$repo/app/src/test/core/remote_rule_set_test.go" "$work/app/libcore/"
    cp "$repo/app/src/test/core/replace_unix_test.go" "$work/app/libcore/"
    files+=(remote_rule_set_test.go replace_unix_test.go)
fi
if [ -n "${NEKO_LEGACY_GEO_FIXTURES:-}" ]; then
    cp "$repo/app/src/test/core/legacy_geo_rule_test.go" "$work/app/libcore/"
    files+=(legacy_geo_rule_test.go)
fi
cd "$work/app/libcore"
go test -mod=readonly -count=1 -v \
    -tags=with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api "${files[@]}"
# The pinned upstream NetworkManager races during host interface discovery.
# Keep race detection on our callback lifecycle and file-ownership regressions;
# full Core startup remains covered by the functional run above.
go test -mod=readonly -race -count=1 -v -run 'TestDNS|TestGeoSiteLoadsCloseTheirFiles' \
    -tags=with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api "${files[@]}"
