#!/usr/bin/env bash

source "buildScript/init/env.sh"
export CGO_ENABLED=1
export GO386=softfloat

cd libcore || exit 1
go mod tidy
rel=1 ./build.sh || exit 1
