#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $GOPATH
cd $golang

if [ ! -f "go/bin/go" ]; then
    curl -Lso go.tar.gz https://go.dev/dl/go1.20.3.linux-amd64.tar.gz
    echo "979694c2c25c735755bf26f4f45e19e64e4811d661dd07b8c010f7a8e18adfca go.tar.gz" | sha256sum -c -
    tar xzf go.tar.gz
fi

go version
go env
