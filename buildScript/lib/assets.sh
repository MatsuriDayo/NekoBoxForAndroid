#!/bin/bash

set -e

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

# Prefer authenticated API in Actions (avoids anonymous rate limit → empty tag → 404)
get_latest_release() {
  local repo="$1"
  local fallback="$2"
  local hdr=()
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    hdr=(-H "Authorization: Bearer ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json")
  fi
  local tag
  tag=$(curl -fsSL "${hdr[@]}" "https://api.github.com/repos/${repo}/releases/latest" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
  if [ -z "$tag" ]; then
    echo "warn: failed to resolve latest tag for ${repo}, using fallback ${fallback}" >&2
    tag="$fallback"
  fi
  echo "$tag"
}

#### geoip
VERSION_GEOIP=$(get_latest_release "SagerNet/sing-geoip" "20260912")
echo VERSION_GEOIP=$VERSION_GEOIP
if [ -z "$VERSION_GEOIP" ]; then
  echo "error: empty VERSION_GEOIP" >&2
  exit 1
fi
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO "https://github.com/SagerNet/sing-geoip/releases/download/${VERSION_GEOIP}/geoip.db"
xz -9 geoip.db

#### geosite
VERSION_GEOSITE=$(get_latest_release "SagerNet/sing-geosite" "20260922023940")
echo VERSION_GEOSITE=$VERSION_GEOSITE
if [ -z "$VERSION_GEOSITE" ]; then
  echo "error: empty VERSION_GEOSITE" >&2
  exit 1
fi
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO "https://github.com/SagerNet/sing-geosite/releases/download/${VERSION_GEOSITE}/geosite.db"
xz -9 geosite.db
