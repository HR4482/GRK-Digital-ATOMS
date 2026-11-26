#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/com/grk/spatialaudio/AudioProfile.java app/src/main/java/com/grk/spatialaudio/Dsp51.java app/src/main/java/com/grk/spatialaudio/PcmWav.java app/src/main/java/com/grk/spatialaudio/HeightRenderer.java tests/AudioProfileTest.java tests/Dsp51Test.java tests/ImmersiveTest.java tests/LongStreamTest.java
java -cp "$out" com.grk.spatialaudio.AudioProfileTest

java -cp "$out" com.grk.spatialaudio.Dsp51Test

java -cp "$out" com.grk.spatialaudio.ImmersiveTest

java -cp "$out" com.grk.spatialaudio.LongStreamTest
