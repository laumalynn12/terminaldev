package com.term.dev.core

import org.junit.Test
import kotlin.system.measureTimeMillis

class TerminalBenchmarkTest {

    // Milestone 2 emulator and streaming throughput benchmark
    @Test
    fun benchmarkStreamingThroughput() {
        val buffer = TerminalBuffer(rows = 40, columns = 120, maxScrollback = 10000)
        val parser = TerminalParser(buffer)

        // Generate synthetic colored terminal stream simulating `cat bigfile`
        val lineBuilder = StringBuilder()
        for (i in 0 until 100) {
            lineBuilder.append("\u001B[1;3").append(i % 8).append("m")
                .append("[Log Item #").append(i).append("] Processing transaction batch with status OK")
                .append("\u001B[0m\r\n")
        }
        val chunk = lineBuilder.toString().toByteArray(Charsets.UTF_8)

        val iterations = 500 // Total 50,000 lines
        var totalBytes = 0L

        // Warmup
        repeat(50) {
            parser.feed(chunk)
        }
        buffer.scrollback.clear()

        // Benchmark
        val elapsedMs = measureTimeMillis {
            repeat(iterations) {
                parser.feed(chunk)
                totalBytes += chunk.size
            }
        }

        val totalLines = iterations * 100
        val throughputMBs = (totalBytes.toDouble() / (1024 * 1024)) / (elapsedMs.toDouble() / 1000.0)
        val linesPerSec = (totalLines.toDouble() / (elapsedMs.toDouble() / 1000.0)).toLong()

        println("=== Terminal Core Throughput Benchmark ===")
        println("Processed: $totalLines lines ($totalBytes bytes) in ${elapsedMs}ms")
        println("Throughput: ${String.format("%.2f", throughputMBs)} MB/s")
        println("Speed: $linesPerSec lines/second")
    }
}
