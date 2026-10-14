# Tasker / external automation (0.8.1)

Enable **Settings → Automation / Tasker → Allow external control** in MiniScreen Takeover. The switch is off by default. While enabled, other apps can send the six commands below. No root is required by this interface.

In Tasker, add **Send Intent**:

- **Action:** one full action from the table
- **Package:** `com.miniscreen.takeover`
- **Class:** `com.miniscreen.takeover.AutomationReceiver`
- **Target:** `Broadcast Receiver`
- Leave Data, MIME type, categories and extras empty.

| Action | Effect |
| --- | --- |
| `com.miniscreen.takeover.ENABLE` | Enable takeover. Resume if schedule, lock, pause and automation gates permit. |
| `com.miniscreen.takeover.DISABLE` | Close takeover and stop its watchdog. Keep the configured schedule and design profiles. Do not power the panel off. |
| `com.miniscreen.takeover.SUSPEND` | Close takeover and stop its watchdog while retaining the enabled state and all settings. Does not power the panel off. |
| `com.miniscreen.takeover.RESUME` | Remove the automation suspension. Restore takeover only if it was enabled and the remaining gates permit. |
| `com.miniscreen.takeover.SCREEN_OFF` | Close takeover, stop its watchdog, ask the manufacturer service to power the rear panel off, and hold it off until SCREEN_ON. |
| `com.miniscreen.takeover.SCREEN_ON` | Remove the automation screen-off lock and any manual-off state, then ask the manufacturer to power the rear panel on. Restore takeover only if enabled and not suspended/paused/otherwise gated. |

The actions are idempotent state commands, not toggles. DISABLE/ENABLE and SUSPEND/RESUME are separate pairs. SCREEN_ON never enables a disabled takeover and never clears SUSPEND. ENABLE and RESUME never clear SCREEN_OFF. A normal schedule edge cannot override an automation suspension or screen-off lock. These locks persist across app restarts, updates and reboots; they are device-local and excluded from profile exports.

SCREEN_ON outside a configured schedule temporarily overrides that window until the next scheduled OFF, matching the intent of manually turning the panel on. The schedule configuration stays saved. Android lock-only mode, an existing ordinary pause and external-app ownership remain effective.

Disabling permission for external control rejects subsequent external commands but preserves existing locks. The Settings section provides local Resume and Screen On buttons to remove them. Home shows active automation locks.

## Essentials / AOD example

Before Essentials takes over AOD handling, send SUSPEND. After it releases the display, send RESUME. If you also need the rear panel physically off, send SCREEN_OFF; later send SCREEN_ON as well as RESUME. The exact Tasker trigger depends on the Essentials events available on the device. This is an automation workaround; it does not repair or dismiss Essentials' own stuck overlay.

## ADB examples

```sh
adb shell am broadcast -a com.miniscreen.takeover.SUSPEND -n com.miniscreen.takeover/.AutomationReceiver
adb shell am broadcast -a com.miniscreen.takeover.RESUME -n com.miniscreen.takeover/.AutomationReceiver
adb shell am broadcast -a com.miniscreen.takeover.SCREEN_OFF -n com.miniscreen.takeover/.AutomationReceiver
adb shell am broadcast -a com.miniscreen.takeover.SCREEN_ON -n com.miniscreen.takeover/.AutomationReceiver
```

## Platform behavior

The receiver works after the user first unlocks following boot. It validates actions and the opt-in before executing a command. A pending broadcast keeps the process eligible to finish the existing delayed power-off callbacks.

Android and manufacturer background restrictions still apply to service/activity launches. Receiving ENABLE or RESUME is not proof that Android allowed the screen activity to appear. Service-start exceptions are recorded in the app's monitoring status; other command failures are also recorded. There is no success acknowledgement protocol in this release. Manufacturer broadcasts request a panel state; they do not guarantee Essentials releases its AOD overlay.
