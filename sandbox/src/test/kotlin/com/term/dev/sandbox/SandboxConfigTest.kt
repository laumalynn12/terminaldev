package com.term.dev.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxConfigTest {

    @Test
    fun testPackageForArm64() {
        val pkg = SandboxConfig.getPackageForAbi("arm64-v8a")
        assertEquals("arm64-v8a", pkg.abi)
        assertTrue(pkg.url.contains("arm64.tar.gz"))
        assertEquals(SandboxConfig.ARM64_SHA256, pkg.sha256)
    }

    @Test
    fun testPackageForX86_64() {
        val pkg = SandboxConfig.getPackageForAbi("x86_64")
        assertEquals("x86_64", pkg.abi)
        assertTrue(pkg.url.contains("amd64.tar.gz"))
        assertEquals(SandboxConfig.AMD64_SHA256, pkg.sha256)
    }

    @Test
    fun testPackageForArmhf() {
        val pkg = SandboxConfig.getPackageForAbi("armeabi-v7a")
        assertEquals("armeabi-v7a", pkg.abi)
        assertTrue(pkg.url.contains("armhf.tar.gz"))
        assertEquals(SandboxConfig.ARMHF_SHA256, pkg.sha256)
    }

    @Test
    fun testSha256ChecksumFormat() {
        val hexRegex = Regex("^[a-f0-9]{64}$")
        assertTrue("ARM64 checksum must be 64 lowercase hex chars", hexRegex.matches(SandboxConfig.ARM64_SHA256))
        assertTrue("AMD64 checksum must be 64 lowercase hex chars", hexRegex.matches(SandboxConfig.AMD64_SHA256))
        assertTrue("ARMHF checksum must be 64 lowercase hex chars", hexRegex.matches(SandboxConfig.ARMHF_SHA256))
    }
}
