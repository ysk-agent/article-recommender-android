#!/usr/bin/env bash
# Emulator launch test (API 35): install the built APK, launch the main
# activity, and verify the feed actually renders rows from the fake
# backend. A compiled APK is not a verified app — this is the gate.
set -u
PKG=dev.moriya.discover
adb install -r discover.apk || exit 1
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" || exit 1
sleep 5
adb shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
adb pull /sdcard/window_dump.xml window_dump.xml >/dev/null 2>&1
adb exec-out screencap -p > screenshot-main.png 2>/dev/null
adb logcat -d > logcat.txt 2>/dev/null

fail=0
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "FAIL: app process not running"; fail=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "FAIL: fatal exception in logcat"; grep -A5 "FATAL EXCEPTION" logcat.txt | head -20; fail=1
fi
# Feed rows come from FakeBackend in M1: at least one known mock title
# must be visible in the window dump.
if ! grep -qE "Fed|Hacker News|日銀|arXiv" window_dump.xml; then
  echo "FAIL: no feed rows rendered"; fail=1
fi
# Bottom navigation must be present with all four tabs.
for tab in フィード Inbox 媒体 履歴; do
  if ! grep -q "$tab" window_dump.xml; then echo "FAIL: tab missing: $tab"; fail=1; fi
done
if [ "$fail" = 0 ]; then echo "LAUNCH TEST PASSED"; fi
exit $fail
