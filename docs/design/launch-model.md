# Ubuntu sandbox and launch model

TerminalDev runs an unmodified Ubuntu userspace environment on Android without requiring root privileges. This document defines how the guest system is initialized, isolated, and executed.

## 1. Native execution model (PRoot)

On modern Android (API 29+ / Android 10+), SELinux W^X rules prohibit executing binary files located in writable application storage (`/data/data/com.term.dev/`).

To execute Ubuntu binaries legally on high target SDKs:
1. PRoot is compiled as an ELF executable but packaged in the APK as a shared library named `libproot.so`.
2. The PRoot loader helper is packaged as `libloader.so` (and `libloader32.so` for 32-bit processes on 64-bit kernels).
3. The Android package manager extracts these libraries into `applicationInfo.nativeLibraryDir` (e.g., `/data/app/.../lib/arm64`), where execution permissions are granted.
4. When launching a session, the PTY spawns `nativeLibraryDir/libproot.so`.
5. PRoot uses `ptrace` to intercept guest system calls. When the guest executes an Ubuntu binary (`/bin/bash`), PRoot intercepts `execve`, relocates paths to the internal rootfs, and translates syscalls in userspace. The Android kernel only executes the approved `libproot.so` binary.

## 2. Command line parameters

The launch command is constructed in Kotlin within the `sandbox` module:

```
libproot.so \
  --kill-on-exit \
  -w /root \
  -r /data/user/0/com.term.dev/files/ubuntu \
  -0 \
  --link2symlink \
  --sysvipc \
  -b /dev \
  -b /dev/urandom:/dev/random \
  -b /proc \
  -b /proc/self/fd:/dev/fd \
  -b /proc/self/fd/0:/dev/stdin \
  -b /proc/self/fd/1:/dev/stdout \
  -b /proc/self/fd/2:/dev/stderr \
  -b /sys \
  -b /data/user/0/com.term.dev/files/tmp:/dev/shm \
  -b /sdcard \
  -b /storage \
  [system mounts if present: /apex, /linkerconfig, /system, /vendor, /plat_property_contexts] \
  /bin/bash -l
```

### Flag explanations
- `--kill-on-exit`: Terminates all traced child processes when PRoot exits, preventing orphaned background processes.
- `-w /root`: Initial working directory for the login shell.
- `-r <path>`: Guest root directory path.
- `-0`: Emulates root user (UID 0, GID 0), allowing commands like `apt`, `dpkg`, and `adduser` to run without permission denied errors.
- `--link2symlink`: Converts hard links into symlinks. Android application data partitions often disallow hard links across directories.
- `--sysvipc`: Provides userspace emulation for System V shared memory, semaphores, and message queues.

## 3. Environment configuration

When launching the Ubuntu shell, the following environment variables are exported:

```sh
export TERM=xterm-256color
export COLORTERM=truecolor
export LANG=C.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export HOME=/root
export SHELL=/bin/bash
export TMPDIR=/tmp
```

## 4. Rootfs initialization and network configuration

Upon the initial extraction of the Ubuntu Base tarball, the following system files are provisioned:

1. **/etc/hostname:**
   ```
   terminaldev
   ```

2. **/etc/hosts:**
   ```
   127.0.0.1   localhost terminaldev
   ::1         localhost ip6-localhost ip6-loopback
   ```

3. **/etc/resolv.conf:**
   ```
   nameserver 8.8.8.8
   nameserver 8.8.4.4
   nameserver 1.1.1.1
   ```

4. **/etc/group:**
   Ensures Android Application IDs and socket permissions are recognized by Ubuntu utilities:
   ```
   root:x:0:
   daemon:x:1:
   bin:x:2:
   sys:x:3:
   adm:x:4:
   tty:x:5:
   disk:x:6:
   sudo:x:27:
   inet:x:3003:root
   everybody:x:9997:root
   android_external_storage:x:1077:root
   ```
   The `inet` group (GID 3003) is required by Android SELinux rules for creating network sockets.
