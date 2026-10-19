#!/bin/bash

set -euo pipefail

buildScript/lib/core/init.sh
pushd ../sing-box
go test -count=1 -ldflags='-checklinkname=0' -tags='with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0' ./boxapi ./nekoutils ./common/dialer ./protocol/group ./protocol/vless ./route/rule
popd
buildScript/lib/core/build.sh
