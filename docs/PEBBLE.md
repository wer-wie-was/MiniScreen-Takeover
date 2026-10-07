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

Gabbro (260×260 round), Emery (200×228) and Flint (144×168) emulator/stock PebbleOS 4.35.0 images are bundled as Android assets. No download, unpacking or user runtime import is required for PBWs that supply one of these targets. The shared engine is stored once; each platform has its own flash images. PBW import chooses a runnable matching platform, preferring round targets. It never runs a binary on a different target. Older Aplite/Basalt/Chalk/Diorite SDK images are not bundled.

A complete imported runtime overrides its entire platform. Missing files never silently mix a custom firmware with the bundled engine. Runtime HTTP paths are allowlisted. Persisted flash is separated by bundled version versus custom image installation, avoiding stale filesystem reuse after a runtime switch. Built-in source hashes and provenance are recorded in `app/src/main/assets/pebble/bundled/runtime.json`. Firmware snapshots are writable copies held by QEMU; packaged assets are immutable.

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

`runtime.json`: `{"schema":1,"platforms":["chalk"]}`. Other supported identifiers: aplite, basalt, diorite, emery, flint, gabbro. If an upstream build has a separate worker JS file, include it under the same platform. Limits: 256 files, 256 MiB uncompressed. PBWs are limited to 32 MiB compressed, 64 MiB uncompressed and 2,000 entries.

Use `pebble/tools/prepare_runtime.py` to package **existing** binaries and images:

```bash
python3 pebble/tools/prepare_runtime.py \
  --upstream /path/to/pebble-qemu-wasm \
  --firmware /path/to/firmware \
  --platforms chalk basalt emery \
  --output Pebble-Runtime.zip
```

This command packages files; it does not compile them. The current WASM source reference is recorded in `pebble/UPSTREAM.json`. Upstream hardware-model sources/build scripts are retained in `pebble/upstream`; full QEMU corresponding source is in the upstream repositories named there. Original licenses continue to apply.

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

A token-scoped loopback HTTP origin supplies COOP/COEP headers. No JavaScript-to-Java bridge is installed. Frame size, imports, storage and IPC are bounded. Arbitrary filesystem access is unavailable. Network access is off by default; requests use the opt-in broker and private-network destinations are rejected. The broker is not yet a full browser/phone-network implementation (cookies, arbitrary redirects and protocol differences need further work).

Native HTML designs remain network-blocked even though the APK now declares INTERNET for the Pebble broker and loopback engine. Config pages run on the primary screen without a Java bridge. Existing native charging, widget, ticker and dot layers remain above the Pebble renderer; pixelshifting is applied to the renderer.

## Checks of the bundled images

The unchanged Gabbro, Emery and Flint WASM packages were booted under Node.js with the application’s QEMU arguments. Within 25 seconds all three produced frames (260×260, 200×228 and 144×168) and firmware-ready console output. This is a host boot check, not an Android/WebView or full PBW compatibility test. No compiler was invoked.

## Android WebView isolation

Android WebView does not enable shared-memory web APIs solely because COOP/COEP headers are supplied. The engine explicitly uses AndroidX WebKit 1.18.0-alpha02 `Profile.setCrossOriginIsolatedAllowlist` before navigation, guarded by both `MULTI_PROFILE` and `CROSS_ORIGIN_ISOLATED_ALLOWLIST`. Only the exact `http://127.0.0.1:<bound-port>` origin is allowed, in a dedicated `miniscreen-pebble` profile inside the separate `:pebble` process. The allowlist is cleared on shutdown/restart; no wildcard or external origin is granted access.

All loopback responses, including runtime-file responses, send `Document-Isolation-Policy: isolate-and-require-corp` in addition to existing COOP/COEP/CORP headers. The JS preflight independently checks secure context, cross-origin isolation, SharedArrayBuffer, WebAssembly, Worker and actual shared WebAssembly memory allocation. Errors distinguish a missing native provider feature from failed document isolation and missing shared-memory support. Pebble settings show the installed WebView package/version.

The library supplies an API adapter, not a replacement WebView engine. A provider without the required feature remains unable to run this threaded WASM runtime; updating Android System WebView may be necessary but does not guarantee feature availability. No check is bypassed and shared memory is not simulated. A native emulator or a separately built non-threaded engine remains the fallback work if the provider cannot support it. Device testing is still required.

References: https://developer.android.com/reference/androidx/webkit/Profile#setCrossOriginIsolatedAllowlist(java.util.Set) and https://developer.chrome.com/blog/document-isolation-policy.
