# Third-party components

The CC0 dedication in this project's LICENSE.md covers original MiniScreen Takeover code only. It does not change licenses of imported watchfaces, firmware, QEMU, upstream Pebble hardware models or vendored JavaScript.

- Pebble emulator source reference: https://github.com/ericmigi/pebble-qemu-wasm, revision recorded in UPSTREAM.json.
- QEMU: GPL-2.0; source/build references are in the retained upstream README/build scripts; source archives for Core Devices QEMU and PebbleOS v4.35.0 are included under `pebble/sources/`. A distributor of compiled runtime binaries must satisfy the applicable source obligations.
- Hardware models retain their existing copyright/license headers.
- `app/src/main/assets/pebble/vendor/` contains upstream Pebble protocol, installer and JS-runtime code from that same repository. The location fallback was changed to avoid silently using IP geolocation.
- The firmware adapter is for Core Devices/PebbleOS: https://github.com/coredevices/PebbleOS. The inspected upstream activity sources identify Apache-2.0. Stock PebbleOS 4.35.0 emulator images for Gabbro/Emery/Flint are included unchanged. They do not contain our health adapter.
- Imported PBWs and optional custom runtime images keep their respective licenses.

## Bundled assets and source provenance

Binary supplier: ericmigi/pebble-qemu-wasm, commit aefa8f180c31c63e3271fbde60ec2021a4878677. Bundled files are unchanged; SHA-256 values are in the bundled runtime manifest. GPL-2.0 and Apache-2.0 texts and retained firmware resource notices are packaged under `pebble/bundled/`. The application’s CC0 dedication never overrides these licenses.

The source archives pin Core Devices QEMU af38a82d208d4cfb7be03ffa95cc12780fbd2965 and PebbleOS v4.35.0 e7fe4f667d8a20603888cbcecebc007c7ff598a8. The binary supplier does not record the exact QEMU source commit/build recipe for these prebuilt bytes. The included source reference must not be described as a verified reproducible corresponding-source build. Before distributing a compiled release, reproduce the runtime from a fully recorded source/toolchain or obtain the supplier’s exact corresponding source. No Android release is compiled here.

AndroidX WebKit 1.18.0-alpha02 (Apache-2.0) is used as a Gradle dependency for the WebView origin allowlist API. Its own and transitive dependencies retain their respective licenses.
