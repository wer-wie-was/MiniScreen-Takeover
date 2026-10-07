# Pebble runtime integration

## Status and scope

The Pebble design mode is an **experimental source integration**, not a claim of complete PBW compatibility. No Android APK, QEMU executable, WebAssembly module or firmware was compiled for this change. Android syntax and resource checks do not establish runtime compatibility.

Implemented: bounded PBW and runtime ZIP imports; platform selection; a separate-process QEMU/WebView service; a shared framebuffer for rear display and preview; PebbleKit JS/AppMessage integration; primary-screen configuration and buttons; persistent flash and per-app JavaScript storage; opt-in network broker; Android clock/time-zone, time format and battery injection; optional Health Connect permission flow and read-only current-day steps/latest fresh pulse acquisition; a source patch for a matching PebbleOS firmware.

Not yet established or completed:

- Android WebView compatibility with the upstream WASM engine, SharedArrayBuffer, cross-origin isolation and modern WebAssembly features.
- Compatibility of every PBW, legacy installer protocol, firmware generation and platform. A platform appearing in the menu does not certify compatibility. The upstream installer primarily uses modern BlobDB/PutBytes.
- A native Android/NDK QEMU port. This implementation uses the available WASM engine instead.
- Complete Pebble Health history, minute samples, sleep, goals, averaged metrics and sensor features. Current steps and pulse alone are not full HealthService emulation.
- A prebuilt firmware containing our Health Connect bridge. Stock firmware does not receive these values; the UI reports the missing bridge.
- Full phone-app parity, including Bluetooth peripherals, microphone/audio, location, notification/timeline integration and every browser/API edge case. No third-party weather service can be guaranteed to remain available.
- Memory/CPU/battery measurements and reliable long-running background operation on the target device.

## Included runtime

Gabbro (260×260 round), Emery (200×228) and Flint (144×168) emulator/stock PebbleOS 4.35.0 images are bundled as Android assets. No download, unpacking or user runtime import is required for PBWs that supply one of these targets. The shared engine is stored once; each platform has its own flash images. PBW import always prefers Gabbro when supplied by the watchface, then Chalk. Remaining targets prefer installed firmware. A missing preferred firmware is reported rather than silently selecting another target. The dropdown lists only the imported watchface’s targets and shows platform, watch abbreviation, resolution and `(b/w)` for monochrome displays. It never runs a binary on a different target. Older Aplite/Basalt/Chalk/Diorite SDK images are not bundled.

The modern engine is shared by Gabbro/Emery/Flint; a separate bundled classic engine supports Aplite/Basalt/Chalk/Diorite. Firmware for those four old targets must be supplied by the user: the historical SDK distribution terms do not establish permission to bundle these binaries in this application. See https://developer.repebble.com/legal/sdk-license/ and https://developer.rebble.io/legal/.

A complete imported runtime overrides its entire platform. A firmware-only import deliberately uses the matching bundled engine. Partial engine imports (JS without WASM, or vice versa) are rejected. Runtime HTTP paths are allowlisted. Persisted flash is separated by bundled version versus custom image installation, avoiding stale filesystem reuse after a runtime switch. Built-in source hashes and provenance are recorded in `app/src/main/assets/pebble/bundled/runtime.json`. Firmware snapshots are writable copies held by QEMU; packaged assets are immutable.

The stock firmware does **not** contain the Health Connect bridge. Read permissions alone do not make steps/pulse appear in Pebble Health. A patched firmware remains a separate requirement. Android WebView behavior and full PBW installation need device verification.

## Optional runtime package

A separate ZIP can override bundled targets or add other platforms, containing prebuilt QEMU JavaScript/WASM plus matching firmware:

```text
runtime.json
LICENSES.txt
chalk/qemu-system-arm.js
chalk/qemu-system-arm.wasm
chalk/qemu_micro_flash.bin
chalk/qemu_spi_flash.bin
```

For a firmware-only ZIP, omit the JavaScript and WASM files and include both flash images. The application uses its bundled classic or modern engine, according to the platform. Importing replaces the previous custom runtime package, so include all custom platforms you want to retain. Supply images and runtimes whose licenses permit your intended use.

`runtime.json`: `{"schema":1,"platforms":["chalk"]}`. Other supported identifiers: aplite, basalt, diorite, emery, flint, gabbro. If an upstream build has a separate worker JS file, include it under the same platform. Limits: 256 files, 256 MiB uncompressed. PBWs are limited to 32 MiB compressed, 64 MiB uncompressed and 2,000 entries.

Use `pebble/tools/prepare_runtime.py` to package **existing** binaries and images:

```bash
python3 pebble/tools/prepare_runtime.py \
  --upstream /path/to/pebble-qemu-wasm \
  --firmware /path/to/firmware \
  --platforms chalk basalt emery \
  --output Pebble-Runtime.zip
```

For firmware-only packaging, use `--firmware-only` and omit `--upstream`. This command packages files; it does not compile them. The current WASM source reference is recorded in `pebble/UPSTREAM.json`. Upstream hardware-model sources/build scripts are retained in `pebble/upstream`; full QEMU corresponding source is in the upstream repositories named there. Original licenses continue to apply.

## Health bridge

Health Connect integration uses the Android 14+ platform API. Steps are aggregated from local midnight to now. Heart-rate records are paginated over the selected maximum-age window and the most recent eligible sample is selected. Read permission does not create measurements. Background reads require the separate available/granted background permission; otherwise data is read only while the controls are active. Data is not written back to Health Connect or stored in app preferences. Revocation or missing data sends an empty snapshot. Watchface JavaScript is untrusted imported code and may transmit values if internet access is allowed.

`pebble/tools/patch_firmware.py /path/to/PebbleOS` modifies only the inspected source revisions (SHA-256 guards). It appends `takeover_health.inc` to the activity service and hooks current metric reads. The patch requires activity support, an initialized activity service and `CONFIG_SHELL`. It targets the current Core Devices/PebbleOS shell, not arbitrary historical SDK images. Firmware must be built separately afterwards; this patch command does not build it.

The engine probes `takeover_health version` over the PULSE console. Only the response `TAKEOVER_HEALTH_V1` enables injection. Snapshot command:

```text
takeover_health <today_steps_or_-1> <bpm_or_-1> <pulse_epoch_seconds> <local_midnight_epoch_seconds>
```

The patch supplies current activity metrics and a significant-update event. Missing values remain unavailable, not fabricated measurements. Host snapshots expire after 180 seconds in guest time. Health sample freshness is additionally checked on Android/JS and by Pebble's own APIs. Historical/current-range calculations must be validated and extended before claiming complete HealthService compatibility.

## Lifecycle and access

One `PebbleService` runs in `:pebble` with its own WebView data directory. A main-process session distributes decoded frames to all surfaces and polls health once per minute. The service is bound while a renderer or Pebble controls are active. When the last client leaves, it requests flash persistence and shuts down after a five-second grace period. This is persistent flash, not a complete CPU/RAM suspend snapshot.

**Restart emulator** is available in the main menu’s Pebble section and on the Pebble settings page. Both controls restart the same shared virtual watch, preserving the imported PBW and selected platform. The engine serializes pending flash saves, writes a fresh snapshot, waits for pending PebbleKit JS storage writes, then acknowledges the request with a unique token. The service creates a fresh WebView and reinstalls the watchface from the selected PBW; preview and rear-display clients remain connected. If the engine is unresponsive, the service restarts after 20 seconds using the last successfully saved state. A stale acknowledgement cannot restart a newer session. If no emulator is running, the button does not start a background watch.

A token-scoped loopback HTTP origin supplies COOP/COEP headers. No JavaScript-to-Java bridge is installed. Frame size, imports, storage and IPC are bounded. Arbitrary filesystem access is unavailable. Network access is off by default; requests use the opt-in broker and private-network destinations are rejected. The broker is not yet a full browser/phone-network implementation (cookies, arbitrary redirects and protocol differences need further work).

Native HTML designs remain network-blocked even though the APK now declares INTERNET for the Pebble broker and loopback engine. Config pages run on the primary screen without a Java bridge. Existing native charging, widget, ticker and dot layers remain above the Pebble renderer; pixelshifting is applied to the renderer.

## Checks of the bundled images

The unchanged Gabbro, Emery and Flint WASM packages were booted under Node.js with the application’s QEMU arguments. Within 25 seconds all three produced frames (260×260, 200×228 and 144×168) and firmware-ready console output. This is a host boot check, not an Android/WebView or full PBW compatibility test. No compiler was invoked.

## Android WebView isolation

Android WebView does not enable shared-memory web APIs solely because COOP/COEP headers are supplied. The engine explicitly uses AndroidX WebKit 1.18.0-alpha02 `Profile.setCrossOriginIsolatedAllowlist` before navigation, guarded by both `MULTI_PROFILE` and `CROSS_ORIGIN_ISOLATED_ALLOWLIST`. Only the exact `http://127.0.0.1:<bound-port>` origin is allowed, in a dedicated `miniscreen-pebble` profile inside the separate `:pebble` process. The allowlist is cleared on shutdown/restart; no wildcard or external origin is granted access.

All loopback responses, including runtime-file responses, send `Document-Isolation-Policy: isolate-and-require-corp` in addition to existing COOP/COEP/CORP headers. The JS preflight independently checks secure context, cross-origin isolation, SharedArrayBuffer, WebAssembly, Worker and actual shared WebAssembly memory allocation. Errors distinguish a missing native provider feature from failed document isolation and missing shared-memory support. Pebble settings show the installed WebView package/version.

The library supplies an API adapter, not a replacement WebView engine. A provider without the required feature remains unable to run this threaded WASM runtime; updating Android System WebView may be necessary but does not guarantee feature availability. No check is bypassed and shared memory is not simulated. A native emulator or a separately built non-threaded engine remains the fallback work if the provider cannot support it. Device testing is still required.

References: https://developer.android.com/reference/androidx/webkit/Profile#setCrossOriginIsolatedAllowlist(java.util.Set) and https://developer.chrome.com/blog/document-isolation-policy.

## Source checks for 0.3.4

Java syntax parsing, XML/resource consistency, JavaScript syntax, bundled SHA-256 hashes and firmware-only ZIP contents were checked. A controlled asynchronous engine check verified that a restart waits for an existing flash save, requests a new save, then acknowledges the request. The unchanged classic engine booted test images for Aplite, Basalt, Chalk and Diorite under Node.js and produced frames. Basalt exports a 148×172 framebuffer with a two-pixel hardware border; the app crops that known border to the logical 144×168 display. This host boot check does not verify PBW installation or Android operation for the four older targets. No Android compilation was performed.

## Constant display brightness

For the exact bundled modern engine (Gabbro, Emery and Flint), the service starts the engine page with `steady=1`. Before instantiation, the loader verifies the bundled WASM SHA-256 and eight instruction sequences, then applies equal-length replacements in memory. The modern `pebble-display` renderer samples constant 255 for monochrome/color brightness and all three RGB backlight channels. The separate snowy driver also uses constant 255 for its palette scale. Palette colors, firmware registers, backlight timers, input and watchface animation remain unchanged. Actual phone display brightness remains independently controlled by Takeover. No repeated input or firmware command is sent.

A complete imported engine is not patched. Firmware-only imports on the three modern targets use the bundled engine and receive the same correction. The classic engine remains unchanged. Unknown modules are rejected by the hash/instruction guards. Boot restoration is unchanged.

Gnomon 2 version 1.0.4 was installed and launched on Gabbro using the bundled firmware and phone transport under Node.js. After a button press, the previous loader produced channel levels 0/85/170/255 initially, then 0/60/120/180 after the backlight timeout. With the corrected loader, levels remained 0/85/170/255 throughout the same test (samples approximately 0.5, 1.5, 4.5 and 9.5 seconds after input). This reproduces and prevents the emulator-side fade; Android/device validation remains separate. The downloaded watchface is not redistributed with this project.

The C files under `pebble/upstream/hw/display` illustrate the corresponding rendering changes against the supplied source snapshots. The authoritative guarded binary edits are in `steady-display.js`; rebuilding from a different source revision requires independently verifying the resulting binary rather than reusing these offsets.
