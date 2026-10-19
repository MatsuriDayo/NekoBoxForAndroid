#!/bin/bash
set -e

source ../buildScript/lib/core/get_source_env.sh

if [ -d .build ]; then
    chmod -R 777 .build
fi
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# Keep the source checkout because go.mod replaces golang.org/x/mobile with it.
if [ ! -d gomobile ]; then
    git clone --no-checkout "$REPOSITORY_GOMOBILE" gomobile
fi
pushd gomobile
git remote set-url origin "$REPOSITORY_GOMOBILE"
git fetch origin "$COMMIT_GOMOBILE"
git checkout "$COMMIT_GOMOBILE"

# Install the matching gomobile tools once per GOPATH.
if [ ! -f "$GOPATH/bin/gomobile-matsuri" ]; then
    pushd cmd/gomobile
    go install -v
    popd
    pushd cmd/gobind
    go install -v
    popd
    mv "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
    mv "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
fi
popd

GOBIND="$GOPATH/bin/gobind-matsuri" "$GOPATH/bin/gomobile-matsuri" init
