@file:Suppress("NewApi")
package com.term.dev.sandbox

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

enum class TarEntryType {
    FILE,
    DIRECTORY,
    SYMLINK,
    HARDLINK,
    LONG_NAME,
    LONG_LINK,
    PAX,
    OTHER
}

data class TarHeader(
    val name: String,
    val mode: Int,
    val size: Long,
    val modTime: Long,
    val type: TarEntryType,
    val linkName: String
)

class TarExtractor {

    companion object {
        private const val TAR_BLOCK_SIZE = 512
    }

    fun extractGzipTar(
        inputStream: InputStream,
        destinationDir: File,
        onProgress: ((entriesCount: Int, currentPath: String) -> Unit)? = null
    ) {
        val gzipStream = GZIPInputStream(BufferedInputStream(inputStream, 65536))
        extractTar(gzipStream, destinationDir, onProgress)
    }

    fun extractTar(
        input: InputStream,
        destinationDir: File,
        onProgress: ((entriesCount: Int, currentPath: String) -> Unit)? = null
    ) {
        val destCanonical = destinationDir.canonicalFile
        if (!destCanonical.exists()) {
            destCanonical.mkdirs()
        }

        var entriesCount = 0
        var pendingName: String? = null
        var pendingLinkName: String? = null

        while (true) {
            val rawHeader = input.readTarHeader() ?: break
            val header = rawHeader.copy(
                name = pendingName ?: rawHeader.name,
                linkName = pendingLinkName ?: rawHeader.linkName
            )
            pendingName = null
            pendingLinkName = null

            if (header.name.isBlank()) {
                input.skipFully(paddedSize(header.size))
                continue
            }

            if (header.type == TarEntryType.LONG_NAME) {
                pendingName = input.readString(header.size).trimEnd('\u0000', '\n')
                input.skipFully(paddingSize(header.size))
                continue
            }

            if (header.type == TarEntryType.LONG_LINK) {
                pendingLinkName = input.readString(header.size).trimEnd('\u0000', '\n')
                input.skipFully(paddingSize(header.size))
                continue
            }

            if (header.type == TarEntryType.PAX) {
                val pax = parsePax(input.readString(header.size))
                pendingName = pax["path"]
                pendingLinkName = pax["linkpath"]
                input.skipFully(paddingSize(header.size))
                continue
            }

            val target = safeResolve(destCanonical, header.name)
            target.parentFile?.mkdirs()

            when (header.type) {
                TarEntryType.DIRECTORY -> {
                    target.mkdirs()
                }
                TarEntryType.SYMLINK -> {
                    createSymlink(destCanonical, target, header.linkName)
                }
                TarEntryType.HARDLINK -> {
                    createHardLink(destCanonical, target, header.linkName)
                }
                TarEntryType.FILE -> {
                    target.outputStream().use { output ->
                        input.copyExactly(output, header.size)
                    }
                    applyPermissions(target, header.mode)
                }
                TarEntryType.LONG_NAME,
                TarEntryType.LONG_LINK,
                TarEntryType.PAX,
                TarEntryType.OTHER -> Unit
            }

            if (header.type != TarEntryType.FILE) {
                input.skipFully(header.size)
            }
            input.skipFully(paddingSize(header.size))

            if (header.modTime > 0 && header.type != TarEntryType.SYMLINK) {
                target.setLastModified(header.modTime * 1000)
            }

            entriesCount++
            onProgress?.invoke(entriesCount, header.name)
        }
    }

    private fun safeResolve(baseDir: File, relativePath: String): File {
        val normalized = relativePath.trimStart('/')
        val resolved = File(baseDir, normalized).canonicalFile
        val basePath = baseDir.path
        val resolvedPath = resolved.path
        require(resolvedPath == basePath || resolvedPath.startsWith(basePath + File.separator)) {
            "Path traversal detected in tar entry: $relativePath"
        }
        return resolved
    }

    private fun createSymlink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val linkTarget = if (File(linkName).isAbsolute) {
            File(linkName)
        } else {
            val resolved = File(target.parentFile ?: root, linkName).canonicalFile
            val rootPath = root.path
            val resolvedPath = resolved.path
            if (resolvedPath == rootPath || resolvedPath.startsWith(rootPath + File.separator)) {
                (target.parentFile ?: root).toPath().relativize(resolved.toPath()).toFile()
            } else {
                File(linkName)
            }
        }
        target.delete()
        try {
            Files.createSymbolicLink(target.toPath(), linkTarget.toPath())
        } catch (e: Exception) {
            // Ignore symlink creation failure on filesystems that disallow it
        }
    }

    private fun createHardLink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val source = safeResolve(root, linkName)
        if (!source.exists()) return
        target.delete()
        try {
            Files.createLink(target.toPath(), source.toPath())
        } catch (e: Exception) {
            // Fallback to file copy if hard links are not supported
            try {
                source.copyTo(target, overwrite = true)
                target.setReadable(source.canRead(), false)
                target.setWritable(source.canWrite(), true)
                target.setExecutable(source.canExecute(), false)
            } catch (ignored: Exception) {}
        }
    }

    private fun applyPermissions(file: File, mode: Int) {
        if (mode <= 0) return
        val canExec = (mode and 0b001_000_000) != 0 || (mode and 0b000_001_000) != 0 || (mode and 0b000_000_001) != 0
        val canWrite = (mode and 0b010_000_000) != 0
        val canRead = (mode and 0b100_000_000) != 0
        file.setExecutable(canExec, false)
        file.setReadable(canRead, false)
        file.setWritable(canWrite, true)
    }

    private fun InputStream.readTarHeader(): TarHeader? {
        val header = ByteArray(TAR_BLOCK_SIZE)
        val read = readFullyOrEnd(header)
        if (read == 0) return null
        if (read < TAR_BLOCK_SIZE) throw EOFException("Unexpected EOF while reading tar header")
        if (header.all { it == 0.toByte() }) return null

        val name = header.parseString(0, 100)
        val prefix = header.parseString(345, 155)
        val fullName = if (prefix.isNotBlank()) "$prefix/$name" else name

        return TarHeader(
            name = fullName.trim('/'),
            mode = header.parseOctal(100, 8).toInt(),
            size = header.parseOctal(124, 12),
            modTime = header.parseOctal(136, 12),
            type = when (header[156].toInt().toChar()) {
                '0', '\u0000' -> TarEntryType.FILE
                '5' -> TarEntryType.DIRECTORY
                '2' -> TarEntryType.SYMLINK
                '1' -> TarEntryType.HARDLINK
                'L' -> TarEntryType.LONG_NAME
                'K' -> TarEntryType.LONG_LINK
                'x' -> TarEntryType.PAX
                else -> TarEntryType.OTHER
            },
            linkName = header.parseString(157, 100)
        )
    }

    private fun parsePax(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            val spaceIndex = text.indexOf(' ', index)
            if (spaceIndex < 0) break
            val length = text.substring(index, spaceIndex).toIntOrNull() ?: break
            val entryEnd = index + length
            val equalsIndex = text.indexOf('=', spaceIndex + 1)
            if (equalsIndex in (spaceIndex + 1) until entryEnd) {
                val key = text.substring(spaceIndex + 1, equalsIndex)
                val value = text.substring(equalsIndex + 1, entryEnd - 1)
                result[key] = value
            }
            index = entryEnd
        }
        return result
    }

    private fun ByteArray.parseString(offset: Int, length: Int): String {
        var end = offset
        val max = offset + length
        while (end < max && this[end] != 0.toByte()) {
            end++
        }
        return String(this, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun ByteArray.parseOctal(offset: Int, length: Int): Long {
        var result = 0L
        for (i in offset until (offset + length)) {
            val b = this[i]
            if (b in '0'.code.toByte()..'7'.code.toByte()) {
                result = (result shl 3) or (b - '0'.code.toByte()).toLong()
            } else if (b == 0.toByte() || b == ' '.code.toByte()) {
                if (result > 0) break
            }
        }
        return result
    }

    private fun InputStream.readString(length: Long): String {
        val bytes = ByteArray(length.toInt())
        var readTotal = 0
        while (readTotal < bytes.size) {
            val r = read(bytes, readTotal, bytes.size - readTotal)
            if (r < 0) throw EOFException("Unexpected EOF reading string")
            readTotal += r
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun InputStream.copyExactly(output: OutputStream, totalBytes: Long) {
        val buffer = ByteArray(8192)
        var remaining = totalBytes
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val r = read(buffer, 0, toRead)
            if (r < 0) throw EOFException("Unexpected EOF copying tar file content")
            output.write(buffer, 0, r)
            remaining -= r
        }
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException("Unexpected EOF skipping tar padding")
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var readTotal = 0
        while (readTotal < buffer.size) {
            val r = read(buffer, readTotal, buffer.size - readTotal)
            if (r < 0) break
            readTotal += r
        }
        return readTotal
    }

    private fun paddedSize(size: Long): Long {
        val rem = size % TAR_BLOCK_SIZE
        return if (rem == 0L) size else size + (TAR_BLOCK_SIZE - rem)
    }

    private fun paddingSize(size: Long): Long {
        val rem = size % TAR_BLOCK_SIZE
        return if (rem == 0L) 0L else TAR_BLOCK_SIZE - rem
    }
}
