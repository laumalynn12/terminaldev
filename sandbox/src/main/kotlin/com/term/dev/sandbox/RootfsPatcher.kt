@file:Suppress("NewApi")
package com.term.dev.sandbox

import java.io.File
import java.nio.file.Files

class RootfsPatcher {

    fun patch(rootfsDir: File, hostname: String = SandboxConfig.DEFAULT_HOSTNAME) {
        val etcDir = File(rootfsDir, "etc")
        if (!etcDir.isDirectory) {
            etcDir.mkdirs()
        }

        patchResolvConf(etcDir)
        patchHosts(etcDir, hostname)
        patchHostname(etcDir, hostname)
        patchGroups(etcDir)
        patchTempDirs(rootfsDir)
        patchStorageSymlinks(rootfsDir)
    }

    private fun patchResolvConf(etcDir: File) {
        val resolvConf = File(etcDir, "resolv.conf")
        if (Files.isSymbolicLink(resolvConf.toPath()) || resolvConf.exists()) {
            resolvConf.delete()
        }
        resolvConf.writeText(
            """
            nameserver 8.8.8.8
            nameserver 8.8.4.4
            nameserver 1.1.1.1
            options edns0 trust-ad
            """.trimIndent() + "\n"
        )
    }

    private fun patchHosts(etcDir: File, hostname: String) {
        val hosts = File(etcDir, "hosts")
        hosts.writeText(
            """
            127.0.0.1 localhost $hostname
            ::1 localhost ip6-localhost ip6-loopback
            """.trimIndent() + "\n"
        )
    }

    private fun patchHostname(etcDir: File, hostname: String) {
        val hostnameFile = File(etcDir, "hostname")
        hostnameFile.writeText("$hostname\n")
    }

    private fun patchGroups(etcDir: File) {
        val groupFile = File(etcDir, "group")
        val requiredGroups = mapOf(
            "root" to "root:x:0:",
            "daemon" to "daemon:x:1:",
            "bin" to "bin:x:2:",
            "sys" to "sys:x:3:",
            "adm" to "adm:x:4:",
            "tty" to "tty:x:5:",
            "disk" to "disk:x:6:",
            "sudo" to "sudo:x:27:",
            "inet" to "inet:x:3003:root",
            "everybody" to "everybody:x:9997:root",
            "android_external_storage" to "android_external_storage:x:1077:root"
        )

        val existingLines = if (groupFile.isFile) groupFile.readLines() else emptyList()
        val existingGroupNames = existingLines.mapNotNull { line ->
            line.substringBefore(':').trim().takeIf { it.isNotBlank() }
        }.toSet()

        val linesToAdd = requiredGroups.filterKeys { it !in existingGroupNames }.values

        if (linesToAdd.isNotEmpty()) {
            val content = buildString {
                if (existingLines.isNotEmpty()) {
                    append(groupFile.readText())
                    if (!endsWith('\n')) append('\n')
                }
                linesToAdd.forEach { appendLine(it) }
            }
            groupFile.writeText(content)
        }
    }

    private fun patchTempDirs(rootfsDir: File) {
        val tmpDir = File(rootfsDir, "tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
        tmpDir.setReadable(true, false)
        tmpDir.setWritable(true, false)
        tmpDir.setExecutable(true, false)

        val devShm = File(rootfsDir, "dev/shm")
        if (!devShm.exists()) devShm.mkdirs()
    }

    private fun patchStorageSymlinks(rootfsDir: File) {
        val rootDir = File(rootfsDir, "root")
        val storageDir = File(rootDir, "storage")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val links = mapOf(
            "shared" to "/sdcard",
            "downloads" to "/sdcard/Download",
            "documents" to "/sdcard/Documents",
            "pictures" to "/sdcard/Pictures",
            "dcim" to "/sdcard/DCIM",
            "movies" to "/sdcard/Movies"
        )

        links.forEach { (name, target) ->
            val symlinkFile = File(storageDir, name)
            if (!symlinkFile.exists() && !Files.isSymbolicLink(symlinkFile.toPath())) {
                try {
                    Files.createSymbolicLink(symlinkFile.toPath(), File(target).toPath())
                } catch (ignored: Exception) {}
            }
        }
    }
}
