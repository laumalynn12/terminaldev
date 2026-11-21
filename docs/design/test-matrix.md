# Compatibility test matrix

TerminalDev supports Android devices from Android 5.0 (API 21) up to the latest stable Android release (Android 15, API 35). This document defines the testing strategy across OS versions and architectures.

## 1. Supported ABIs

| ABI | Architecture | Target Devices | Ubuntu Rootfs Archive | Status |
|---|---|---|---|---|
| `arm64-v8a` | 64-bit ARM | Modern Android phones, tablets, foldables | `ubuntu-base-24.04-base-arm64.tar.gz` | Expected |
| `armeabi-v7a` | 32-bit ARM | Older budget phones and legacy hardware | `ubuntu-base-24.04-base-armhf.tar.gz` | Expected |
| `x86_64` | 64-bit x86 | Android emulators, ChromeOS, Windows Subsystem for Android | `ubuntu-base-24.04-base-amd64.tar.gz` | Expected |

*Note on x86 32-bit:* Canonical no longer produces 32-bit x86 Ubuntu Base tarballs for 24.04. Therefore, `x86` (32-bit) is dropped from the build matrix as required by Section 5 of the specification.

## 2. Android version test matrix

| API Level | Android Version | Key Validation Focus | Testing Strategy | Status |
|---|---|---|---|---|
| API 21 | Android 5.0 (Lollipop) | Minimum SDK floor, legacy TLS connectivity, 32-bit ARM filesystem limits | Physical device / legacy emulator | Expected |
| API 26 | Android 8.0 (Oreo) | Notification channel registration, background execution limits | CI Emulator (`compat.yml`) | Expected |
| API 29 | Android 10 (Q) | W^X enforcement on native libs, legacy storage opt-out | CI Emulator (`compat.yml`) | Expected |
| API 30 | Android 11 (R) | `MANAGE_EXTERNAL_STORAGE` permission grant, `/sdcard` binding | CI Emulator (`compat.yml`) | Expected |
| API 33 | Android 13 (Tiramisu) | Runtime `POST_NOTIFICATIONS` flow, phantom process behavior | CI Emulator (`compat.yml`) | Expected |
| API 35 | Android 15 (Vanilla Ice Cream) | 16 KB page-size compatibility, modern foreground service types | CI Emulator / physical device | Expected |

## 3. Automated compatibility workflow (`compat.yml`)

The compatibility workflow runs separately from routine PR builds:
- Triggers on manual dispatch (`workflow_dispatch`) and weekly scheduled runs.
- Runs an Android emulator matrix for x86_64 across API 26, 29, 30, and 33 using standard GitHub Actions runner hardware acceleration (KVM).
- Executes an automated smoke test:
  1. Installs `terminaldev-vx.y.z-x86_64-release.apk`.
  2. Launches application and grants storage permissions.
  3. Spawns a PTY session running `/system/bin/sh`.
  4. Confirms terminal output capture and exit code propagation.
- For physical ARM devices (`arm64-v8a` and `armeabi-v7a`), manual test scripts are provided for verification.
