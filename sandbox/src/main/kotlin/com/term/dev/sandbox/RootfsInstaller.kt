@file:Suppress("NewApi")
package com.term.dev.sandbox

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class InstallStage {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    CONFIGURING,
    FINISHED,
    FAILED
}

data class InstallProgress(
    val stage: InstallStage,
    val message: String,
    val bytesRead: Long = 0,
    val totalBytes: Long = 0,
    val progressPercent: Int = 0
)

class RootfsInstaller(
    private val patcher: RootfsPatcher = RootfsPatcher(),
    private val extractor: TarExtractor = TarExtractor()
) {

    fun isInstalled(targetDir: File): Boolean {
        val bashFile = File(targetDir, "bin/bash")
        val osRelease = File(targetDir, "etc/os-release")
        return bashFile.isFile && osRelease.isFile
    }

    fun install(
        pkg: SandboxConfig.RootfsPackage,
        targetDir: File,
        tempDir: File,
        onProgress: (InstallProgress) -> Unit = {}
    ) {
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val archiveFile = File(tempDir, "rootfs.tar.gz")
        val stagingDir = File(tempDir, "ubuntu-staging")

        try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            // 1. Download
            downloadWithChecksum(pkg.url, archiveFile, onProgress)

            // 2. Verify SHA-256
            onProgress(InstallProgress(InstallStage.VERIFYING, "Verifying SHA-256 checksum..."))
            val calculatedHash = computeSha256(archiveFile)
            if (!calculatedHash.equals(pkg.sha256, ignoreCase = true)) {
                throw SecurityException("SHA-256 checksum mismatch!\nExpected: ${pkg.sha256}\nActual:   $calculatedHash")
            }

            // 3. Extract
            onProgress(InstallProgress(InstallStage.EXTRACTING, "Extracting Ubuntu filesystem..."))
            FileInputStream(archiveFile).use { fis ->
                extractor.extractGzipTar(fis, stagingDir) { count, path ->
                    if (count % 250 == 0) {
                        onProgress(
                            InstallProgress(
                                InstallStage.EXTRACTING,
                                "Extracting files ($count entries): ${path.takeLast(30)}"
                            )
                        )
                    }
                }
            }

            // 4. Configure
            onProgress(InstallProgress(InstallStage.CONFIGURING, "Configuring DNS, hosts, and permissions..."))
            patcher.patch(stagingDir)

            // 5. Finalize directory move
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            val moved = stagingDir.renameTo(targetDir)
            if (!moved) {
                // If renameTo fails across mounts, copy
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            onProgress(InstallProgress(InstallStage.FINISHED, "Ubuntu 24.04.5 LTS sandbox ready.", progressPercent = 100))
        } catch (e: Exception) {
            onProgress(InstallProgress(InstallStage.FAILED, "Installation failed: ${e.message}"))
            throw e
        } finally {
            if (archiveFile.exists()) {
                archiveFile.delete()
            }
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun downloadWithChecksum(
        urlStr: String,
        targetFile: File,
        onProgress: (InstallProgress) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"

        try {
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Download failed with HTTP code $responseCode from $urlStr")
            }

            val totalBytes = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var bytesRead = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32768)
                    var lastPercent = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            val mbRead = bytesRead / (1024 * 1024)
                            val mbTotal = if (totalBytes > 0) totalBytes / (1024 * 1024) else 0
                            val msg = if (totalBytes > 0) {
                                "Downloading Ubuntu rootfs: $percent% ($mbRead MB / $mbTotal MB)"
                            } else {
                                "Downloading Ubuntu rootfs: $mbRead MB"
                            }
                            onProgress(
                                InstallProgress(
                                    stage = InstallStage.DOWNLOADING,
                                    message = msg,
                                    bytesRead = bytesRead,
                                    totalBytes = totalBytes,
                                    progressPercent = percent
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(32768)
            while (true) {
                val read = fis.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b.toInt() and 0xff))
        }
        return sb.toString()
    }
}
