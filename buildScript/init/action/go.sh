#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $GOPATH
cd $golang

if [ ! -f "go/bin/go" ]; then
    curl -Lso go.tar.gz https://go.dev/dl/go1.20.7.linux-amd64.tar.gz
    echo "f0a87f1bcae91c4b69f8dc2bc6d7e6bfcd7524fceec130af525058c0c17b1b44 go.tar.gz" | sha256sum -c -
    tar xzf go.tar.gz
fi

go version
go env
