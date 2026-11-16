#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/target/smoke-classes"
mkdir -p "$OUT"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -d "$OUT"
javac --release 11 --add-modules jdk.httpserver -encoding UTF-8 -cp "$OUT" -d "$OUT" "$ROOT/scripts/RelayBookSmokeTest.java"
java --add-modules jdk.httpserver -cp "$OUT" com.relaybook.RelayBookSmokeTest
