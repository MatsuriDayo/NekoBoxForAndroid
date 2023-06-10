#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $GOPATH
cd $golang

if [ ! -f "go/bin/go" ]; then
    curl -Lso go.tar.gz https://go.dev/dl/go1.20.5.linux-amd64.tar.gz
    echo "d7ec48cde0d3d2be2c69203bc3e0a44de8660b9c09a6e85c4732a3f7dc442612 go.tar.gz" | sha256sum -c -
    tar xzf go.tar.gz
fi

go version
go env
