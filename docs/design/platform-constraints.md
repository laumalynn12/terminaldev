# Android platform constraints and target SDK analysis

This document analyzes the Android OS constraints that dictate TerminalDev's design, including binary execution, process lifecycle rules, permissions, and SDK version targets.

## 1. Binary execution (W^X) and the targetSdk decision

### Context
Beginning with Android 10 (API 29), Android enforces W^X memory restrictions and SELinux policies prohibiting `execve` on writable files inside an application's private data directory (`/data/data/<package>/`).

### Comparison of approaches
1. **Low `targetSdk` (Termux approach, `targetSdk 28`):**
   - *Advantage:* Allows direct execution of binaries from `$PREFIX/bin`.
   - *Disadvantage:* Google Play completely blocks targetSdk < 24 for installation on modern Android versions. Starting with Android 14 (API 34), the OS blocks side-loading apps targeting old SDKs (`INSTALL_FAILED_DEPRECATED_SDK_VERSION`). Users must pass `--bypass-low-target-sdk-block` via ADB. Furthermore, modern storage permissions (`MANAGE_EXTERNAL_STORAGE`) do not behave consistently on low target SDKs.
2. **High `targetSdk` (TerminalDev decision, `targetSdk 35`, `compileSdk 35`):**
   - *Implementation:* PRoot is compiled as an ELF executable but named `libproot.so` and placed in the APK `lib/<abi>/` directory. The Android package manager extracts it to `nativeLibraryDir` with execute (`+x`) permissions. PRoot is executed from `nativeLibraryDir`, and guest Ubuntu binaries are executed under PRoot's ptrace interception.
   - *Advantage:* Installs cleanly on all Android versions up to Android 15/16 without OS deprecation warnings or installation blocks. Uses modern Android permission flows.

## 2. Phantom process killer (Android 12+)

### Constraint
Android 12 (API 31) introduced a kernel/framework monitor that tracks child processes spawned by apps. The system limits phantom processes to a maximum of 32 total across all background apps, aggressively killing processes that consume background CPU.

### Mitigation in TerminalDev
1. **Foreground Service:** Active terminal sessions run under a continuous Android Foreground Service holding a partial wake lock, signaling to the OS that terminal sessions are user-facing.
2. **User guidance:** In settings and documentation, provide power users with the standard ADB command to lift phantom process limits on devices running Android 12 through Android 13:
   ```sh
   adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
   ```
3. **Graceful recovery:** Sessions monitor child PID exit codes. If SIGKILL is received unexpectedly while in the background, a clear message is surfaced to the terminal buffer.

## 3. Foreground service types and notifications

### Constraint
- Android 13 (API 33) requires the runtime permission `android.permission.POST_NOTIFICATIONS`.
- Android 14 (API 34) requires explicit `android:foregroundServiceType` declarations in `AndroidManifest.xml` and when calling `startForeground()`.

### Implementation
- Request `POST_NOTIFICATIONS` gracefully when the first background session is created.
- Declare `foregroundServiceType="specialUse"` in the manifest with property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` documenting terminal session continuity, or `systemExempted`.
- Notification includes controls to acquire/release wake locks and cleanly shut down sessions.

## 4. Seccomp and ptrace restrictions

### Constraint
PRoot relies on `ptrace(PTRACE_SYSEMU, ...)` and seccomp filters (`SECCOMP_MODE_FILTER`) for system call interception. Starting in Android 8.0 (API 26), Android's zygote applies seccomp filters to all unprivileged app processes.

### Implementation
- PRoot detects whether seccomp filtering is permitted by the host kernel.
- If the host seccomp filter restricts `PTRACE_SECCOMP`, PRoot automatically falls back to standard `PTRACE_SYSEMU` or `PTRACE_SYSCALL` tracing.
- Support runtime toggle `PROOT_NO_SECCOMP=1` in the launch builder for devices with non-standard kernel seccomp rules.

## 5. 16 KB page-size alignment

### Constraint
Android 15 introduces support and testing for devices with 16 KB physical memory page sizes. Shared libraries built with standard 4 KB alignment fail to load on 16 KB kernels with dynamic linker errors.

### Implementation
- All native targets in `term-pty` and PRoot are linked with `-Wl,-z,max-page-size=16384`.
- This ensures compatibility with both standard 4 KB page devices and 16 KB page devices.
