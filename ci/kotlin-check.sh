#!/usr/bin/env bash
# VM-side Kotlin compile check (same pattern as yt-client-android,
# Yosuke 2026-11-21): run BEFORE pushing so compile errors are caught
# locally in ~30 s instead of after a CI round trip. Gradle cannot run
# on the Muse VM, and a compiled APK is only verified by the CI
# emulator launch test — this check covers type/syntax/reference
# errors, which is the class of failure it exists for.
#
# The app deliberately uses only appcompat + recyclerview and builds
# its UI in code (no Material, no ViewBinding, no R references), so
# the sibling project's dependency jars can be reused as-is.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$HOME/workspace/toolchains"
export JAVA_HOME="${JAVA_HOME:-$TC/jdk-21}"
export PATH="$JAVA_HOME/bin:$PATH"
KOTLINC="$TC/kotlinc/bin/kotlinc"
ANDROID_JAR="$TC/android-sdk/platforms/android-35/android.jar"
LIBS="${KCHECK_LIBS:-$TC/yt-client-kcheck/jars}"

if [ ! -d "$LIBS" ] || [ -z "$(ls "$LIBS"/*.jar 2>/dev/null)" ]; then
    echo "kotlin-check: dependency jars not found in $LIBS" >&2
    echo "See yt-client-android/ci/kotlin-check.sh for the fetch recipe." >&2
    exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CP="$(ls "$LIBS"/*.jar | tr '\n' ':')$ANDROID_JAR"
START=$(date +%s)
find "$ROOT/app/src/main/java" -name '*.kt' > "$WORK/sources.txt"
"$KOTLINC" -classpath "$CP" -d "$WORK/out" @"$WORK/sources.txt" > "$WORK/log.txt" 2>&1
RC=$?
ELAPSED=$(( $(date +%s) - START ))
if [ $RC -eq 0 ]; then
    echo "kotlin-check: PASS (${ELAPSED}s)"
else
    echo "kotlin-check: FAIL (${ELAPSED}s) — first errors:"
    grep "error:" "$WORK/log.txt" | head -20
fi
exit $RC
