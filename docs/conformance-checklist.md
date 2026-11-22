# TerminalDev emulator and renderer conformance checklist

This checklist documents VT/ANSI terminal emulation conformance, parser behaviors, and real-device interactive tests for Milestone 2.

## 1. Automated JVM conformance tests

All tests below are implemented in `term-core/src/test/kotlin/` and verified in CI:

- [x] **Cursor addressing and movement (`CSI H`, `CSI A..G`):**
  Verified in `TerminalParserTest.testCursorPositioningCSI`. Correctly calculates 1-based to 0-based coordinates and clamps cursor within active screen boundaries.
- [x] **Erase in display and line (`CSI J`, `CSI K`):**
  Verified in `TerminalParserTest.testEraseDisplayAndLine`. Tests modes 0 (cursor to end), 1 (start to cursor), and 2 (entire line/screen).
- [x] **SGR text styles (`CSI m`):**
  Verified in `TerminalParserTest.testSgrColorsAndStyles`. Tests bold (1), faint (2), italic (3), underline (4), blink (5), inverse (7), and reset (0).
- [x] **Color palette modes:**
  - 16 standard ANSI colors (30..37, 40..47, 90..97, 100..107).
  - 256-color palette (`\e[38;5;n` and `\e[48;5;n`).
  - 24-bit TrueColor RGB (`\e[38;2;r;g;b` and `\e[48;2;r;g;b`).
- [x] **Alternate screen buffer switching (`\e[?1049h` / `\e[?1049l`):**
  Verified in `TerminalParserTest.testAlternateScreenBuffer`. Protects main screen scrollback history while full-screen applications (vim, htop, nano, less) run in the alternate screen.
- [x] **Device status report (`\e[6n` CPR):**
  Verified in `TerminalParserTest.testCursorPositionReportCPR`. Generates `\e[<row>;<col>R` response to stdin.
- [x] **East Asian wide characters and emojis (`WcWidth`):**
  Verified in `WcWidthTest`. Computes character cell span (width 2 for CJK and emojis, 0 for combining characters, 1 for ASCII).
- [x] **Scrollback buffer and ring retention:**
  Verified in `CircularBufferTest`. Evicts oldest lines on boundary overflow without memory reallocation.
- [x] **Throughput benchmark:**
  Verified in `TerminalBenchmarkTest`. Measures processing speed and streaming bandwidth in MB/s on the JVM.

## 2. Interactive full-screen terminal tools test matrix

| Tool / Capability | Required Escape Sequences | Behavior Validated | Status |
|---|---|---|---|
| `vim` | Alternate screen (`\e[?1049h`), cursor addressing (`\e[H`), clear line/display (`\e[2J`) | Full screen text buffer, mode status bar, cursor positioning, smooth scrolling | Expected (ready for rootfs) |
| `htop` | SGR colors, cursor hide/show (`\e[?25h`/`l`), window resize (`SIGWINCH`) | CPU/memory bar charts with truecolor / 256-color rendering, dynamic terminal resize | Expected (ready for rootfs) |
| `tmux` | Application cursor keys (`\e[?1h`), alternate screen, mouse tracking (`\e[?1000h`) | Split panes, window tabs, mouse selection and scrolling | Expected (ready for rootfs) |
| `nano` | Alternate screen, bottom shortcut bar, scroll margins (`\e[r`) | Editing buffer with functional arrow keys, Backspace, Ctrl shortcuts | Expected (ready for rootfs) |
| `less` | Alternate screen, raw mode, page-down/up | Paging text files without polluting main scrollback buffer | Expected (ready for rootfs) |
| SGR mouse tracking | `\e[?1000h`, `\e[?1002h`, `\e[?1006h` | Touch taps and drags translate to SGR `<button;col;rowM` reporting sequences | Verified |
| Window resize | `ioctl(TIOCSWINSZ)` | Terminal grid recalculates rows and columns dynamically on display orientation change | Verified |

## 3. Ubuntu sandbox, PRoot, and shared storage (Milestone 3)

| Component | Test / Capability | Behavior Validated | Status |
|---|---|---|---|
| `SandboxConfig` | Official Canonical hashes | ARM64 (`a91d5a93...`), AMD64 (`e77b6f10...`), ARMHF (`4fcee4d2...`) SHA-256 sums match canonical release | Verified |
| `TarExtractor` | Streaming archive extraction | Extracts tar without path traversal, handles GNU long names, creates symlinks and restores Unix modes | Verified |
| `RootfsPatcher` | System file provisioning | Writes `/etc/resolv.conf`, `/etc/hosts`, `/etc/hostname`, and GID mapping (inet 3003, everybody 9997, storage 1077) | Verified |
| `ProotLauncher` | Command & env construction | Builds `-0 --link2symlink --sysvipc -r <rootfs> -w /root -b /sdcard` launch arguments and guest environment | Verified |
| Shared Storage | `/sdcard` bind mount & permissions | Declares `MANAGE_EXTERNAL_STORAGE` and `READ/WRITE_EXTERNAL_STORAGE`; binds `/sdcard` and generates `~/storage` symlinks | Verified |
| Cursor idle-blink | Keystroke pause | Cursor remains solid visible during typing and arrow repeat hold, resuming blink only after 650 ms idle | Verified |
