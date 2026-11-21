package com.term.dev.pty

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class PtySession(
    val masterFd: Int,
    val pid: Int,
    val inputStream: FileInputStream,
    val outputStream: FileOutputStream,
    private val pfd: ParcelFileDescriptor
) {
    fun resize(rows: Int, cols: Int) {
        if (masterFd >= 0) {
            PtyBridge.resizePty(masterFd, rows, cols)
        }
    }

    fun waitFor(): Int {
        return PtyBridge.waitForChild(pid)
    }

    fun close() {
        try {
            pfd.close()
        } catch (e: Exception) {}
        PtyBridge.closeFd(masterFd)
    }

    companion object {
        fun create(
            cmd: String,
            cwd: String,
            args: Array<String>,
            envVars: Array<String>,
            rows: Int = 24,
            cols: Int = 80
        ): PtySession {
            val pidOut = IntArray(1)
            val fd = PtyBridge.openPtySubprocess(cmd, cwd, args, envVars, pidOut, rows, cols)
            if (fd < 0) {
                throw RuntimeException("Failed to spawn PTY subprocess for: $cmd")
            }
            val pfd = ParcelFileDescriptor.adoptFd(fd)
            val inStream = FileInputStream(pfd.fileDescriptor)
            val outStream = FileOutputStream(pfd.fileDescriptor)
            return PtySession(fd, pidOut[0], inStream, outStream, pfd)
        }
    }
}
