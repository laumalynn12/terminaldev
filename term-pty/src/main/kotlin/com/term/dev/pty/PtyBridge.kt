package com.term.dev.pty

object PtyBridge {
    init {
        System.loadLibrary("pty")
    }

    @JvmStatic
    external fun openPtySubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>,
        envVars: Array<String>,
        pidOut: IntArray,
        rows: Int,
        cols: Int
    ): Int

    @JvmStatic
    external fun resizePty(fd: Int, rows: Int, cols: Int)

    @JvmStatic
    external fun waitForChild(pid: Int): Int

    @JvmStatic
    external fun closeFd(fd: Int)
}
