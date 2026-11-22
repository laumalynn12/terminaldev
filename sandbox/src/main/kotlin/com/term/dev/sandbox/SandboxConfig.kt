package com.term.dev.sandbox

import android.os.Build

object SandboxConfig {
    const val UBUNTU_VERSION = "24.04.5 LTS"
    const val DEFAULT_HOSTNAME = "terminaldev"

    // Authoritative Canonical URLs and SHA-256 checksums
    const val ARM64_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"
    const val ARM64_SHA256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"

    const val AMD64_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-amd64.tar.gz"
    const val AMD64_SHA256 = "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86"

    const val ARMHF_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-armhf.tar.gz"
    const val ARMHF_SHA256 = "4fcee4d278f1c5232e085a021a85e4c6cef3853557a88d98ff380b5e5d5841bb"

    data class RootfsPackage(
        val abi: String,
        val url: String,
        val sha256: String
    )

    fun getPackageForAbi(abi: String): RootfsPackage {
        return when (abi) {
            "arm64-v8a", "aarch64" -> RootfsPackage("arm64-v8a", ARM64_URL, ARM64_SHA256)
            "x86_64" -> RootfsPackage("x86_64", AMD64_URL, AMD64_SHA256)
            "armeabi-v7a", "armeabi" -> RootfsPackage("armeabi-v7a", ARMHF_URL, ARMHF_SHA256)
            else -> {
                val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                when (primary) {
                    "arm64-v8a", "aarch64" -> RootfsPackage("arm64-v8a", ARM64_URL, ARM64_SHA256)
                    "x86_64" -> RootfsPackage("x86_64", AMD64_URL, AMD64_SHA256)
                    else -> RootfsPackage("arm64-v8a", ARM64_URL, ARM64_SHA256)
                }
            }
        }
    }
}
