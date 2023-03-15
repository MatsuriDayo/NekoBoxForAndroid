#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $GOPATH
cd $golang

if [ ! -f "go/bin/go" ]; then
    curl -Lso go.tar.gz https://go.dev/dl/go1.20.2.linux-amd64.tar.gz
    echo "4eaea32f59cde4dc635fbc42161031d13e1c780b87097f4b4234cfce671f1768 go.tar.gz" | sha256sum -c -
    tar xzf go.tar.gz
fi

go version
go env
