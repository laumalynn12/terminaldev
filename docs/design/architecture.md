# TerminalDev architecture specification

TerminalDev provides an Ubuntu terminal experience on Android. The architecture emphasizes correctness, low latency, minimal memory footprint, and modular separation.

## 1. Module layout

The repository is structured into five modules:

```
terminaldev/
├── app/          # Android application shell: UI, settings, Foreground Service, permission flow
├── term-core/    # Pure Kotlin/JVM: VT parser, terminal screen buffer, circular scrollback
├── term-pty/     # NDK C/JNI: posix_openpt, process fork/exec, window resize, waitpid
├── term-view/    # Custom Android View: Canvas and TextPaint renderer, touch and key input
└── sandbox/      # Kotlin: Ubuntu rootfs management, SHA-256 verification, PRoot command assembly
```

## 2. Subsystem designs

### A. Terminal emulator core (`term-core`)
- **Technology:** Pure Kotlin JVM with zero Android SDK dependencies. Enables unit testing directly on standard JVM test runners in CI.
- **Parser:** Table-driven deterministic state machine based on the DEC/ANSI parser model (Paul Flo Williams architecture: Ground, Escape, CSI Entry, CSI Param, OSC String). Handles standard VT escape sequences, xterm 256 colors, truecolor (24-bit RGB), cursor addressing, alternate screen buffers, bracketed paste mode, and mouse tracking (SGR 1006).
- **Buffer layout:**
  - Fast flat circular ring buffer for scrollback history.
  - Packed 64-bit primitive integers (`LongArray`) to represent terminal cells: 21 bits for Unicode code point, 8 bits for foreground color, 8 bits for background color, 8 bits for style flags (bold, faint, italic, underline, blink, inverse, invisible), and auxiliary width flags.
  - Zero object allocation during steady-state character streaming.

### B. Terminal renderer (`term-view`)
- **Technology:** Custom Android `View` utilizing hardware-accelerated `Canvas` and `android.text.TextPaint`.
- **Justification against Compose for the terminal grid:**
  Jetpack Compose introduces recomposition tracking, layout node trees, and per-frame memory allocations that cause dropped frames during heavy terminal output (such as `cat` on large files or continuous compilation logs). A custom `View` draws directly to the hardware canvas, batches contiguous text spans with identical attributes, and caches monospace font metrics.
- **Rendering pipeline:**
  - Measures glyph width and line height once during layout or font change.
  - Binds to `Choreographer.postFrameCallback` to coalesce multiple incoming terminal byte chunks into a single render pass per display refresh interval.
  - Renders background color blocks first, followed by batched text draw calls (`canvas.drawText`).
  - Supports smooth touch scrolling, pinch-to-zoom font scaling, and text selection with handles.

### C. Native pseudo-terminal interface (`term-pty`)
- **Technology:** C library compiled through Android NDK with 16 KB page-size alignment (`-Wl,-z,max-page-size=16384`).
- **PTY lifecycle:**
  - Uses standard POSIX APIs: `posix_openpt(O_RDWR | O_CLOEXEC)`, `grantpt`, `unlockpt`, and `ptsname_r`.
  - Sets UTF-8 line discipline and disables terminal flow control (`IXON | IXOFF`) via `termios`.
  - Configures terminal window sizes via `ioctl(fd, TIOCSWINSZ, &ws)`.
  - Spawns subprocesses using `fork` and `execve`, closing inherited file descriptors via `/proc/self/fd`.
  - Manages file descriptors in Kotlin using `ParcelFileDescriptor` to avoid reflection on private framework fields.
- **Concurrency:** Dedicated background IO thread reading PTY bytes into a bounded ring buffer.

### D. Sandbox and process launcher (`sandbox`)
- **Technology:** Kotlin library managing Ubuntu rootfs verification, extraction, and PRoot launch options.
- **Architecture:**
  - Downloads official Ubuntu Base tarballs directly from Canonical servers with resume support.
  - Verifies downloads against authoritative SHA-256 checksums before unpacking.
  - Packages PRoot as `libproot.so` and loader as `libloader.so` inside APK `lib/<abi>/` to satisfy Android W^X security rules.
  - Launches `/bin/bash` login shells inside the chroot with proper environment variables and bind mounts.

### E. Service and lifecycle (`app`)
- **Foreground service:** `TerminalService` manages the lifecycle of running terminal sessions.
- **Session persistence:** Sessions remain active when the user switches apps or when the Activity is destroyed and recreated during configuration changes.
- **Notification:** A persistent status notification shows active session counts and provides actions to acquire/release wake locks or cleanly exit sessions.
- **Dependency injection:** Explicit constructor injection throughout. No Dagger, Hilt, or Koin.
- **Persistence:** App settings and color schemes are saved in `SharedPreferences` and lightweight JSON files. No Room or DataStore dependencies.
