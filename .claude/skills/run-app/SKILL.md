---
name: run-app
description: Install, launch and drive the openHAB Android app on an emulator or device via adb, e.g. to check a UI change in the real app.
---

# Run the app on an emulator

## Prerequisites

- The SDK location is read from `sdk.dir` in `local.properties` (or `ANDROID_HOME`).
- Check for a running emulator or device:
  ```bash
  $SDK/platform-tools/adb devices
  ```
  If none is running, list the AVDs with `$SDK/emulator/emulator -list-avds` and start one in the background
  with `$SDK/emulator/emulator -avd <name>`.

## Install and launch

```bash
./gradlew :mobile:installFossBetaDebug
$SDK/platform-tools/adb shell monkey -p org.openhab.habdroid.beta -c android.intent.category.LAUNCHER 1
```

The beta flavor has the package name `org.openhab.habdroid.beta`, the stable one `org.openhab.habdroid`.

## Drive the UI

Use `.claude/skills/run-app/ui.sh`:

- `ui.sh dump` lists all visible texts and content descriptions with their bounds. Use it to find out what can
  be tapped.
- `ui.sh tap "<text>"` taps the element with exactly that text or content description, e.g. `"Open side menu"`,
  `"Navigate up"` or `"OK"`.
- `ui.sh text "<string>"` types into the focused field, `ui.sh key KEYCODE_DEL` deletes one char.
- `ui.sh shot <file.png>` saves a screenshot. Store it in the scratchpad and look at it with the Read tool, a
  dump alone doesn't show colors, icons or layout issues.

Example: open the settings

```bash
ui=.claude/skills/run-app/ui.sh
$ui tap "Open side menu"
$ui tap "Settings"
$ui shot "$SCRATCHPAD/settings.png"
```

## Notes

- The emulator may contain the user's own server configurations. Don't save changes made for testing, leave the
  server editor with "Navigate up" and choose "Discard".
- `10.0.2.2` is the host machine as seen from the emulator.
