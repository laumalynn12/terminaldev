# Shared storage and `/sdcard` integration

Providing reliable access to shared Android storage (`/sdcard`) inside the Ubuntu environment is a required core feature of TerminalDev. This document details per-version permissions, bind mounting, and file exchange behaviors.

## 1. Storage permissions across Android versions

| Android Version | API Level | Permission Model | TerminalDev Behavior | Status |
|---|---|---|---|---|
| Android 5.0 - 5.1 | 21 - 22 | Install-time permissions | Declared in manifest (`READ/WRITE_EXTERNAL_STORAGE`). Automatically granted upon install. | Expected |
| Android 6.0 - 9.0 | 23 - 28 | Runtime permissions | Prompts user for `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`. | Expected |
| Android 10 | 29 | Scoped storage / legacy opt-out | Uses `requestLegacyExternalStorage="true"` in manifest. Direct file access enabled. | Expected |
| Android 11 - 15+ | 30 - 35+ | All Files Access (`MANAGE_EXTERNAL_STORAGE`) | Checks `Environment.isExternalStorageManager()`. Launches `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for full `/sdcard` POSIX access. | Expected |

## 2. Guided permission flow

When TerminalDev initializes:
1. The app checks whether storage access has already been granted for the running API level.
2. If access is missing, a setup dialog explains why shared storage access is needed.
3. On Android 11+ (API 30+), tapping "Grant Storage Access" launches the system "All files access" settings screen for TerminalDev.
4. On Android 6 to 10 (API 23 to 29), it invokes the runtime permission request dialog for read and write permissions.
5. If the user declines, the terminal continues to operate with internal storage only, but `/sdcard` operations will report permission errors.

## 3. Storage bind mounts inside Ubuntu

PRoot binds the host storage directory directly into the guest filesystem:

```
-b /sdcard:/sdcard
-b /storage:/storage
```

During container initialization, TerminalDev creates convenient symlinks inside the user's home directory (`/root/storage/` or `/home/storage/`):

```
~/storage/
├── shared     -> /sdcard
├── downloads  -> /sdcard/Download
├── documents  -> /sdcard/Documents
├── pictures   -> /sdcard/Pictures
├── dcim       -> /sdcard/DCIM
└── movies     -> /sdcard/Movies
```

## 4. Two-way file operations

Users can copy and move files in both directions:
- **Importing into Ubuntu:**
  ```sh
  cp /sdcard/Download/archive.tar.gz ~/
  cp -r /sdcard/Documents/project ~/src/
  ```
- **Exporting to Android:**
  ```sh
  cp ~/build/output.apk /sdcard/Download/
  cp ~/report.pdf /sdcard/Documents/
  ```

Files written to `/sdcard` become immediately visible to host file managers. For media files (images, audio, videos), TerminalDev optionally triggers `MediaScannerConnection.scanFile()` on exported paths so the Android MediaStore indexes them.

## 5. Technical limitations of shared storage

These limitations are inherent to Android's storage layer and are documented for users:
- **No POSIX permissions:** Shared storage uses FAT32 or Android FUSE/sdcardfs abstractions. `chmod` and `chown` have no effect. Files copied from internal storage to `/sdcard` lose Unix permission bits.
- **No symlinks:** Creating symlinks inside `/sdcard` will return `EPERM` (Operation not permitted).
- **No direct execution:** Shared storage filesystems are mounted with `noexec` on Android. Binaries cannot be executed directly from `/sdcard`.
- **Restricted system directories:** On Android 11+, `/sdcard/Android/data` and `/sdcard/Android/obb` are restricted by the OS and remain inaccessible.
