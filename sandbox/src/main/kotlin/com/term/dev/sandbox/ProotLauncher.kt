package com.term.dev.sandbox

import java.io.File

data class ProotCommand(
    val executable: String,
    val args: Array<String>,
    val envVars: Array<String>,
    val cwd: String
)

class ProotLauncher {

    companion object {
        val SYSTEM_BIND_MOUNTS = listOf(
            "/dev",
            "/proc",
            "/sys",
            "/dev/urandom:/dev/random",
            "/proc/self/fd:/dev/fd",
            "/proc/self/fd/0:/dev/stdin",
            "/proc/self/fd/1:/dev/stdout",
            "/proc/self/fd/2:/dev/stderr",
            "/sdcard",
            "/storage",
            "/apex",
            "/linkerconfig",
            "/system",
            "/vendor",
            "/plat_property_contexts"
        )
    }

    fun buildLaunchCommand(
        nativeLibDir: String,
        ubuntuDir: File,
        tmpDir: File,
        guestCommand: List<String> = listOf("/bin/bash", "-l"),
        extraBindings: List<String> = emptyList(),
        noSeccomp: Boolean = false
    ): ProotCommand {
        val prootBin = File(nativeLibDir, "libproot.so").absolutePath
        val loaderBin = File(nativeLibDir, "libloader.so").absolutePath

        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }

        val args = mutableListOf<String>()
        args.add("--kill-on-exit")
        args.add("-0")
        args.add("--link2symlink")
        args.add("--sysvipc")

        // Root directory and working directory
        args.add("-r")
        args.add(ubuntuDir.absolutePath)
        args.add("-w")
        args.add("/root")

        // Shared memory
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/dev/shm")

        // Standard bindings
        for (mount in SYSTEM_BIND_MOUNTS) {
            val hostPath = mount.substringBefore(':')
            if (File(hostPath).exists()) {
                args.add("-b")
                args.add(mount)
            }
        }

        // Extra user bindings
        for (extra in extraBindings) {
            val hostPath = extra.substringBefore(':')
            if (File(hostPath).exists()) {
                args.add("-b")
                args.add(extra)
            }
        }

        // Guest command
        args.addAll(guestCommand)

        val envList = mutableListOf(
            "PROOT_LOADER=$loaderBin",
            "PROOT_TMP_DIR=${tmpDir.absolutePath}",
            "TMPDIR=${tmpDir.absolutePath}",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME=/root",
            "SHELL=/bin/bash",
            "USER=root"
        )

        if (noSeccomp) {
            envList.add("PROOT_NO_SECCOMP=1")
        }

        return ProotCommand(
            executable = prootBin,
            args = args.toTypedArray(),
            envVars = envList.toTypedArray(),
            cwd = ubuntuDir.absolutePath
        )
    }
}
