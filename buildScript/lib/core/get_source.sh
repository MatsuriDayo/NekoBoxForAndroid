#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
ROOT_DIR="$PWD"
pushd ..

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout "$REPOSITORY_SING_BOX"
fi
pushd sing-box
git remote set-url origin "$REPOSITORY_SING_BOX"
git fetch --tags origin
git checkout -B "nb4a-sing-box-${COMMIT_SING_BOX:0:12}" "$COMMIT_SING_BOX"
for patch in "$ROOT_DIR"/buildScript/lib/core/patches/*.patch; do
  if git apply --check "$patch"; then
    git -c user.name='NekoBoxForAndroid build' -c user.email='build@nekoboxforandroid.invalid' am --3way "$patch"
  elif ! git apply --reverse --check "$patch"; then
    echo "sing-box patch does not apply: $patch" >&2
    exit 1
  fi
done
popd

####

if [ ! -d "libneko" ]; then
  git clone --no-checkout "$REPOSITORY_LIBNEKO"
fi
pushd libneko
git remote set-url origin "$REPOSITORY_LIBNEKO"
git fetch origin "$COMMIT_LIBNEKO"
git checkout "$COMMIT_LIBNEKO"
popd

####

popd
