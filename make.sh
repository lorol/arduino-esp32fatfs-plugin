#!/usr/bin/env bash

if [[ -z "$INSTALLDIR" ]]; then
    INSTALLDIR="$HOME/Documents/Arduino"
fi
echo "INSTALLDIR: $INSTALLDIR"

pde_path=`find ../../../ -name pde.jar`
core_path=`find ../../../ -name arduino-core.jar`
lib_path=`find ../../../ -name commons-codec-1.7.jar`
if [[ -z "$core_path" || -z "$pde_path" ]]; then
    echo "Some java libraries have not been built yet (did you run ant build?)"
    return 1
fi
echo "pde_path: $pde_path"
echo "core_path: $core_path"
echo "lib_path: $lib_path"

set -e

mkdir -p bin
javac -target 1.8 -cp "$pde_path:$core_path:$lib_path" \
      -d bin src/ESP32FatFS.java

pushd bin
mkdir -p $INSTALLDIR/tools
rm -rf $INSTALLDIR/tools/ESP32FatFS
mkdir -p $INSTALLDIR/tools/ESP32FatFS/tool
zip -r $INSTALLDIR/tools/ESP32FatFS/tool/esp32fatfs.jar *
popd

dist=$PWD/dist
rev=$(git describe --tags)
mkdir -p $dist
pushd $INSTALLDIR/tools
zip -r $dist/ESP32FatFS-$rev.zip ESP32FatFS/
popd
