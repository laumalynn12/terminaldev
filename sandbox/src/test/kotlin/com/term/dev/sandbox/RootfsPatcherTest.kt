package com.term.dev.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsPatcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPatchRootfsConfiguration() {
        val rootfsDir = tempFolder.newFolder("ubuntu_root")
        val patcher = RootfsPatcher()

        patcher.patch(rootfsDir, "terminaldev")

        // 1. Verify resolv.conf
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        assertTrue("resolv.conf must exist", resolvConf.isFile)
        val resolvText = resolvConf.readText()
        assertTrue("resolv.conf must contain Google DNS", resolvText.contains("nameserver 8.8.8.8"))
        assertTrue("resolv.conf must contain Cloudflare DNS", resolvText.contains("nameserver 1.1.1.1"))

        // 2. Verify hosts
        val hosts = File(rootfsDir, "etc/hosts")
        assertTrue("hosts must exist", hosts.isFile)
        val hostsText = hosts.readText()
        assertTrue("hosts must map localhost", hostsText.contains("127.0.0.1 localhost terminaldev"))

        // 3. Verify hostname
        val hostname = File(rootfsDir, "etc/hostname")
        assertTrue("hostname must exist", hostname.isFile)
        assertEquals("terminaldev\n", hostname.readText())

        // 4. Verify group
        val group = File(rootfsDir, "etc/group")
        assertTrue("group must exist", group.isFile)
        val groupText = group.readText()
        assertTrue("group must contain inet GID 3003", groupText.contains("inet:x:3003:root"))
        assertTrue("group must contain everybody GID 9997", groupText.contains("everybody:x:9997:root"))
        assertTrue("group must contain android_external_storage", groupText.contains("android_external_storage:x:1077:root"))

        // 5. Verify /tmp
        val tmpDir = File(rootfsDir, "tmp")
        assertTrue("tmp directory must exist", tmpDir.isDirectory)
    }
}
