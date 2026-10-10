#!/bin/bash
# Small UI driver for the app running on a connected device or emulator.
#
# Usage:
#   ui.sh dump              List visible texts and content descriptions with their bounds
#   ui.sh tap <text>        Tap the first element whose text or content description equals <text>
#   ui.sh text <string>     Type <string> into the focused input field
#   ui.sh key <keycode>     Send a key event, e.g. KEYCODE_BACK or KEYCODE_DEL
#   ui.sh shot <file.png>   Save a screenshot to <file.png>

set -e

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
sdk_dir=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$repo_root/local.properties" 2>/dev/null)}
ADB="${sdk_dir:+$sdk_dir/platform-tools/}adb"

dump_ui() {
    "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
    "$ADB" shell cat /sdcard/ui.xml
}

case $1 in
    dump)
        dump_ui | python3 -c '
import re, sys
for m in re.finditer(r"(?:text|content-desc)=\"([^\"]+)\"[^>]*?bounds=\"([^\"]+)\"", sys.stdin.read()):
    print(m.group(1), "|", m.group(2))'
        ;;
    tap)
        coords=$(dump_ui | python3 -c '
import re, sys
m = re.search(r"(?:text|content-desc)=\"" + re.escape(sys.argv[1]) + r"\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read())
if not m:
    sys.exit("Element not found: " + sys.argv[1])
print((int(m[1]) + int(m[3])) // 2, (int(m[2]) + int(m[4])) // 2)' "$2")
        "$ADB" shell input tap $coords
        sleep 1.5
        ;;
    text)
        "$ADB" shell input text "$2"
        ;;
    key)
        "$ADB" shell input keyevent "$2"
        ;;
    shot)
        "$ADB" exec-out screencap -p > "$2"
        ;;
    *)
        sed -n '4,9p' "$0"
        exit 1
        ;;
esac
