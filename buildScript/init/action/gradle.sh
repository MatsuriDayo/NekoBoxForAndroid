#!/bin/bash
# Prepare script for Actions Gradle build
set -e

#### Download assets
bash buildScript/lib/assets.sh

exit

#### Download "external" from Internet
#rm -rf external
#mkdir -p external
#cd external
#
#echo "Downloading preferencex-android"
#wget -q -O tmp.zip https://github.com/SagerNet/preferencex-android/archive/8bdb0c6ae44f378b073c6a1c850d03d729b70ff8.zip
#unzip tmp.zip > /dev/null 2>&1
#mv preferencex-android-* preferencex
#
#rm tmp.zip
