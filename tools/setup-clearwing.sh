#!/bin/sh
# Clearwing 3.1.2 (what SwitchGDX 3.0.2 was built with): SwitchGDX/clearwing-vm @ c9a6555 + patches/clearwing-*.patch,
# transpiler fat jar built with the JDK 17 in $JDK17.
set -e
cd "$(dirname "$0")/.."
ROOT=$PWD
[ -d work/clearwing-vm ] || git clone -q https://github.com/SwitchGDX/clearwing-vm.git work/clearwing-vm
cd work/clearwing-vm
git checkout -q -f c9a6555
git reset -q --hard c9a6555
for p in "$ROOT"/patches/clearwing-*.patch; do git apply "$p"; done
export JAVA_HOME=${JDK17:?set JDK17 to a JDK 17 home}
sh ./gradlew -q :transpiler:shadowJar
ls -la transpiler/build/libs/transpiler-3.1.2.jar
