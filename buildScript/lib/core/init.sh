#!/usr/bin/env bash

source "buildScript/init/env.sh"

[ -f libcore/go.mod ] || exit 1
cd libcore

./init.sh || exit 1
