package com.term.dev.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProotLauncherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBuildLaunchCommand() {
        val nativeLibDir = tempFolder.newFolder("lib").absolutePath
        val ubuntuDir = tempFolder.newFolder("ubuntu")
        val tmpDir = tempFolder.newFolder("tmp")

        val launcher = ProotLauncher()
        val cmd = launcher.buildLaunchCommand(
            nativeLibDir = nativeLibDir,
            ubuntuDir = ubuntuDir,
            tmpDir = tmpDir,
            guestCommand = listOf("/bin/bash", "-l")
        )

        assertEquals(File(nativeLibDir, "libproot.so").absolutePath, cmd.executable)

        val argsList = cmd.args.toList()
        assertTrue(argsList.contains("--kill-on-exit"))
        assertTrue(argsList.contains("-0"))
        assertTrue(argsList.contains("--link2symlink"))
        assertTrue(argsList.contains("--sysvipc"))
        assertTrue(argsList.contains("-r"))
        assertTrue(argsList.contains(ubuntuDir.absolutePath))
        assertTrue(argsList.contains("-w"))
        assertTrue(argsList.contains("/root"))
        assertTrue(argsList.contains("/bin/bash"))
        assertTrue(argsList.contains("-l"))

        val envList = cmd.envVars.toList()
        val expectedLoader = File(nativeLibDir, "libloader.so").absolutePath
        assertTrue(envList.contains("PROOT_LOADER=$expectedLoader"))
        assertTrue(envList.contains("TERM=xterm-256color"))
        assertTrue(envList.contains("COLORTERM=truecolor"))
        assertTrue(envList.contains("USER=root"))
        assertTrue(envList.contains("HOME=/root"))
        assertTrue(envList.contains("SHELL=/bin/bash"))
    }
}
