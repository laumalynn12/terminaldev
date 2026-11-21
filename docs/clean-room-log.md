# Clean-room development log

This document tracks all reference code examined during the development of TerminalDev, the design insights extracted, and how our implementation maintains architectural and code independence.

## Reference projects studied

### 1. termux-app
- **Upstream:** [https://github.com/termux/termux-app](https://github.com/termux/termux-app)
- **License:** GPLv3 only
- **Files reviewed:**
  - `gradle.properties`: inspected `minSdkVersion=21` and `targetSdkVersion=28`. Observed why Termux chose `targetSdkVersion=28` to retain binary execution privileges within the app writable data directory on Android 10+.
  - `terminal-emulator/src/main/jni/termux.c`: examined standard POSIX pseudo-terminal handling using `open("/dev/ptmx")`, `grantpt`, `unlockpt`, `ptsname_r`, `setsid`, and `dup2` into standard file descriptors.
  - `terminal-emulator/src/main/java/com/termux/terminal/`: examined VT escape sequence state handling, screen buffers, and scrollback ring buffers.
  - `terminal-view/src/main/java/com/termux/view/TerminalView.java`: reviewed custom View rendering, touch input handling, and selection gestures.
- **Design conclusions and independence:**
  - Termux relies on `targetSdkVersion 28`, which is blocked or restricted on modern Android 14+ devices. TerminalDev will target modern SDKs (`targetSdk 35`) and package PRoot as an Android native library.
  - We do not reuse any code from `termux.c` or Java classes. Our PTY layer is written in modern C using `posix_openpt` with non-blocking pipes and minimal JNI boundaries. Our terminal parser and buffer are written from scratch in pure Kotlin following the ECMA-48 and DEC/ANSI parser specifications.

### 2. rikkahub
- **Upstream:** [https://github.com/RikkaApp/rikkahub](https://github.com/RikkaApp/rikkahub)
- **License:** AGPLv3
- **Files reviewed:**
  - `terminal/src/main/cpp/termux_pty.cpp`: analyzed C++ JNI bridge with `posix_openpt`, process spawning, and 16 KB page-size compilation alignment.
  - `terminal/README.md`: reviewed architecture notes on decoupling `TerminalTransport` from session logic, holding file descriptors via `ParcelFileDescriptor` to avoid reflection, and managing child process groups when using PRoot.
  - `gradle/libs.versions.toml`: observed modern AGP 9.4.0, Kotlin 2.4.21, and compileSdk 37 settings.
- **Design conclusions and independence:**
  - Transport abstraction is clean, but TerminalDev maintains an even simpler, flatter architecture without third-party DI or Compose on the terminal surface.
  - All JNI bindings use distinct function signatures and clean C interfaces without copying `termux_pty.cpp`.

### 3. Xed-Editor
- **Upstream:** [https://github.com/Xed-Editor/Xed-Editor](https://github.com/Xed-Editor/Xed-Editor)
- **License:** GPLv3
- **Files reviewed:**
  - `features/terminal/src/main/java/com/rk/terminal/MkSession.kt` and `MkRootfs.kt`: examined process environment setup, session working directory resolution, and rootfs extraction handling.
  - `features/terminal/src/main/assets/terminal/sandbox.sh` and `setup.sh`: reviewed PRoot launch parameters, host bind mounts (`/apex`, `/linkerconfig`, `/sdcard`, `/proc`, `/sys`), and network file setup (`/etc/resolv.conf`, `/etc/hosts`, `/etc/group`).
  - `features/terminal/proot/src/main/cpp/CMakeLists.txt`: studied packaging PRoot (`proot`) as `libproot.so` and loader as `libloader.so` to place executables in the APK native library directory for W^X compliance on Android 10+.
  - `features/terminal/proot/src/main/cpp/extension/`: examined extensions such as `link2symlink`, `fake_id0`, and `sysvipc`.
- **Design conclusions and independence:**
  - Packaging PRoot as `libproot.so` inside the APK native library path successfully satisfies Android W^X security rules on modern target SDKs.
  - Instead of shell scripts in assets (`sandbox.sh`, `setup.sh`), TerminalDev constructs the PRoot command line directly in Kotlin, ensuring type safety, explicit parameter validation, and atomic rootfs initialization without invoking intermediate `/system/bin/sh` scripts.
  - Canonical official Ubuntu Base tarballs will be verified via published SHA-256 sums rather than third-party mirror releases.

## Code provenance confirmation
No code, layouts, resources, or tests from the reference repositories have been or will be copied into TerminalDev. All modules and subsystems are designed from first principles and standard specifications.
