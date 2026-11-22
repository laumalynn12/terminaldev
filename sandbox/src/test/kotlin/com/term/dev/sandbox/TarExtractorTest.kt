package com.term.dev.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class TarExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExtractSimpleTar() {
        val destDir = tempFolder.newFolder("dest")

        // Build a minimal TAR in memory:
        // Entry 1: "test.txt", content "Hello Ubuntu Sandbox!"
        val content = "Hello Ubuntu Sandbox!\n".toByteArray(Charsets.UTF_8)
        val tarBytes = ByteArrayOutputStream().apply {
            val header = ByteArray(512)
            // Name: "test.txt"
            System.arraycopy("test.txt".toByteArray(), 0, header, 0, 8)
            // Mode: 0644
            System.arraycopy("0000644\u0000".toByteArray(), 0, header, 100, 8)
            // Size: octal
            val sizeStr = String.format("%011o\u0000", content.size)
            System.arraycopy(sizeStr.toByteArray(), 0, header, 124, 12)
            // Typeflag: '0' (regular file)
            header[156] = '0'.code.toByte()

            write(header)
            write(content)
            // Padding
            val pad = (512 - (content.size % 512)) % 512
            if (pad > 0) write(ByteArray(pad))

            // Two 512 zero blocks marking EOF
            write(ByteArray(1024))
        }.toByteArray()

        val extractor = TarExtractor()
        var reportedCount = 0
        extractor.extractTar(ByteArrayInputStream(tarBytes), destDir) { count, _ ->
            reportedCount = count
        }

        assertEquals(1, reportedCount)
        val extractedFile = File(destDir, "test.txt")
        assertTrue("Extracted file must exist", extractedFile.exists())
        assertEquals("Hello Ubuntu Sandbox!\n", extractedFile.readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPathTraversalProtection() {
        val destDir = tempFolder.newFolder("dest_traversal")

        // Tar with evil path "../evil.txt"
        val content = "Malicious content".toByteArray()
        val tarBytes = ByteArrayOutputStream().apply {
            val header = ByteArray(512)
            System.arraycopy("../evil.txt".toByteArray(), 0, header, 0, 11)
            System.arraycopy("0000644\u0000".toByteArray(), 0, header, 100, 8)
            val sizeStr = String.format("%011o\u0000", content.size)
            System.arraycopy(sizeStr.toByteArray(), 0, header, 124, 12)
            header[156] = '0'.code.toByte()

            write(header)
            write(content)
            val pad = (512 - (content.size % 512)) % 512
            if (pad > 0) write(ByteArray(pad))
            write(ByteArray(1024))
        }.toByteArray()

        val extractor = TarExtractor()
        extractor.extractTar(ByteArrayInputStream(tarBytes), destDir)
    }
}
