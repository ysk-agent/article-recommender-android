#!/usr/bin/env bash
# Emulator launch test (API 35) for Discover.
#
# Test-time techniques carried over from yt-client-android (2026-11-21):
#  - Condition-based waits (wait_until polls every 1 s with a hard cap).
#    Caps are sized from observed CI runs: every successful wait in
#    runs 1-3 completed on the first poll, so caps only bound the
#    failure path — keep them tight (10-15 s, 30 s for cold start).
#    instead of blind fixed sleeps — YT Client's fixed sleeps summed to
#    ~160 s per run before this change.
#  - TEST_SECTIONS selects which sections run (all | feed | interact),
#    chosen per commit via the [test: ...] marker in the commit message
#    (see build.yml); [test: all] is reserved for minor releases.
#  - ANR-dialog dismissal before screenshots, package-scoped FATAL
#    check, AVD snapshot cache + Gradle cache (both in build.yml).
# A compiled APK is not a verified app — this script is the gate.
set -u
cd "$(dirname "$0")/.."
APK=discover.apk
PKG=dev.moriya.discover
FAIL=0
note_fail() { echo "LAUNCH-TEST FAILURE: $1"; FAIL=1; }
top_activity() { adb shell dumpsys activity activities 2>/dev/null | grep "topResumedActivity" | head -1; }
# Match the short class name on the topResumedActivity line only.
# (ReaderActivity/SettingsActivity live in the .ui subpackage, so a
# "$PKG/.$1" component pattern never matches them — CI run 3 polled
# 45s for a reader that was already on top.)
top_is() { top_activity | grep -q "$1"; }
pid_alive() { [ -n "$(adb shell pidof $PKG 2>/dev/null | tr -d '\r\n')" ]; }

wait_until() {
  local timeout=$1 desc=$2; shift 2
  local waited=0
  while [ $waited -lt $timeout ]; do
    if "$@" >/dev/null 2>&1; then echo "wait ok: $desc (${waited}s)"; return 0; fi
    sleep 1; waited=$((waited + 1))
  done
  echo "wait timeout: $desc (${timeout}s)"; return 1
}

dump_ui() { adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1; adb pull /sdcard/window_dump.xml window_dump.xml >/dev/null 2>&1; }
ui_has() { dump_ui && grep -q "$1" window_dump.xml; }

dismiss_anr() {
  dump_ui || return 0
  grep -q "isn't responding" window_dump.xml || return 0
  tap_text "Wait" || true
  sleep 2
}
screencap() { dismiss_anr; adb exec-out screencap -p > "$1" 2>/dev/null; echo "screenshot: $1 ($(stat -c%s "$1" 2>/dev/null) bytes)"; }

# tap_text <text>: find the node whose text matches and tap its centre.
tap_text() {
  dump_ui || return 1
  local node bounds nums
  node=$(grep -o "text=\"$1\"[^>]*" window_dump.xml | head -1)
  [ -n "$node" ] || { echo "tap_text: not found: $1"; return 1; }
  bounds=$(echo "$node" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -1)
  nums=$(echo "$bounds" | grep -o '[0-9]\+')
  set -- $nums
  [ $# -eq 4 ] || return 1
  local cx=$(( ($1 + $3) / 2 )) cy=$(( ($2 + $4) / 2 ))
  echo "tap_text bounds=[$1,$2][$3,$4] -> $cx,$cy"
  adb shell input tap "$cx" "$cy"
}

check_top() { # activity, description
  local top; top=$(top_activity); echo "top: $top"
  if top_is "$1"; then echo "OK: $1 is top"; else note_fail "$1 not top ($2)"; fi
}

ensure_main() {
  if ! top_is MainActivity; then
    echo "ensure_main: relaunching MainActivity"
    adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
    if ! wait_until 10 "MainActivity on top" top_is MainActivity; then
      echo "ensure_main: force-stop + relaunch"
      adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
      adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
      wait_until 20 "MainActivity on top after relaunch" top_is MainActivity || true
    fi
  fi
}

# --- sections ------------------------------------------------------------

section_feed() {
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
  wait_until 30 "MainActivity on top" top_is MainActivity || true
  check_top MainActivity "launch"
  pid_alive || note_fail "process dead after launch"
  # Feed rows come from FakeBackend: poll the UI dump for a known title
  # instead of sleeping a fixed number of seconds.
  wait_until 15 "feed rows rendered" ui_has "Balance Sheet" || note_fail "no feed rows rendered"
  screencap screenshot-main.png
  for tab in フィード Inbox 媒体 履歴 設定; do
    ui_has "$tab" || note_fail "tab/button missing: $tab"
  done
}

section_interact() {
  ensure_main
  # Open the first article: the WebView reader must come to the top.
  tap_text "The Fed's Balance Sheet Is Shrinking in a New Way" || note_fail "could not tap first article"
  wait_until 15 "ReaderActivity on top" top_is ReaderActivity || true
  check_top ReaderActivity "article tap"
  screencap screenshot-reader.png
  if top_is ReaderActivity; then
    adb shell input keyevent KEYCODE_BACK
    wait_until 10 "back to MainActivity" top_is MainActivity || true
  fi
  ensure_main

  # Two-stage swipe, stage 1 (left, ~46% of width): the undo banner
  # must show the applied action name (design §3).
  dump_ui
  local node bounds nums
  node=$(grep -o 'text="Repo Fails Are Telling a Different Story This Month"[^>]*' window_dump.xml | head -1)
  if [ -n "$node" ]; then
    bounds=$(echo "$node" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | head -1)
    nums=$(echo "$bounds" | grep -o '[0-9]\+'); set -- $nums
    local cy=$(( ($2 + $4) / 2 ))
    adb shell input swipe 1000 "$cy" 500 "$cy" 300
    wait_until 10 "undo banner after swipe" ui_has "低評価" || note_fail "swipe banner not shown"
    tap_text "取り消し" || true
  else
    echo "interact: swipe target row not visible; skipping swipe leg"
  fi

  # Tab round trip: Inbox -> Sources -> History -> Feed.
  tap_text "Inbox" || note_fail "tap Inbox failed"
  wait_until 10 "inbox renders" ui_has "Inboxは空です" || echo "note: inbox not empty or text differs"
  tap_text "媒体" || note_fail "tap Sources failed"
  wait_until 10 "sources render" ui_has "Fed Guy" || note_fail "sources list not rendered"
  screencap screenshot-sources.png
  tap_text "履歴" || note_fail "tap History failed"
  wait_until 10 "history renders" ui_has "開いた" || echo "note: history has no open entry yet"
  screencap screenshot-history.png
  tap_text "フィード" || note_fail "tap Feed failed"
  wait_until 10 "feed re-renders" ui_has "Balance Sheet" || note_fail "feed did not re-render"
}

section_settings() {
  ensure_main
  tap_text "設定" || note_fail "tap Settings failed"
  wait_until 15 "SettingsActivity on top" top_is SettingsActivity || true
  check_top SettingsActivity "settings button"
  wait_until 10 "exploration setting renders" ui_has "探索割合" || note_fail "exploration share setting missing"
  screencap screenshot-settings.png
  if top_is SettingsActivity; then adb shell input keyevent KEYCODE_BACK; fi
  wait_until 10 "back to MainActivity" top_is MainActivity || true
}

# --- main ------------------------------------------------------------------

TEST_SECTIONS="${TEST_SECTIONS:-all}"
RUN_FEED=0; RUN_INTERACT=0
case "$TEST_SECTIONS" in
  all) RUN_FEED=1; RUN_INTERACT=1 ;;
  feed) RUN_FEED=1 ;;
  interact) RUN_FEED=1; RUN_INTERACT=1 ;;
  *) echo "unknown TEST_SECTIONS='$TEST_SECTIONS'; running all"; RUN_FEED=1; RUN_INTERACT=1 ;;
esac
echo "TEST_SECTIONS=$TEST_SECTIONS (feed=$RUN_FEED interact=$RUN_INTERACT)"

adb root >/dev/null 2>&1 || true
wait_until 10 "adb responsive" adb shell echo ok || true
adb install -r "$APK" || note_fail "adb install failed"
adb logcat -c || true

if [ "$RUN_FEED" -eq 1 ]; then section_feed; fi
if [ "$RUN_INTERACT" -eq 1 ]; then section_interact; section_settings; fi

pid_alive || note_fail "process dead at end"
adb logcat -d -v threadtime > logcat.txt 2>&1 || true
if grep -q "FATAL EXCEPTION" logcat.txt && grep -A3 "FATAL EXCEPTION" logcat.txt | grep -q "$PKG"; then
  note_fail "FATAL EXCEPTION for $PKG in logcat"
fi
echo "LAUNCH_TEST_FAIL=$FAIL"
exit $FAIL
