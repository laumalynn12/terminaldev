package com.term.dev

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.term.dev.core.TerminalParser
import com.term.dev.pty.PtySession
import com.term.dev.view.TerminalView
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var ptySession: PtySession? = null
    private lateinit var parser: TerminalParser

    private var ctrlActive = false
    private var altActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminalView)
        parser = TerminalParser(terminalView.buffer, responseCallback = { bytes ->
            sendBytesToPty(bytes)
        }, titleCallback = { title ->
            runOnUiThread {
                supportActionBar?.title = title
            }
        })

        terminalView.onInput = { bytes ->
            val processedBytes = processModifiers(bytes)
            sendBytesToPty(processedBytes)
        }

        terminalView.onResize = { rows, cols ->
            ptySession?.resize(rows, cols)
        }

        setupExtraKeys()
        startShellSession()
    }

    private fun setupExtraKeys() {
        val btnCtrl = findViewById<Button>(R.id.btnCtrl)
        val btnAlt = findViewById<Button>(R.id.btnAlt)

        btnCtrl.setOnClickListener {
            ctrlActive = !ctrlActive
            btnCtrl.setBackgroundColor(if (ctrlActive) Color.parseColor("#0DBC79") else Color.parseColor("#242424"))
        }

        btnAlt.setOnClickListener {
            altActive = !altActive
            btnAlt.setBackgroundColor(if (altActive) Color.parseColor("#0DBC79") else Color.parseColor("#242424"))
        }

        findViewById<Button>(R.id.btnEsc).setOnClickListener { sendBytesToPty(byteArrayOf(27)) }
        findViewById<Button>(R.id.btnTab).setOnClickListener { sendBytesToPty(byteArrayOf(9)) }
        findViewById<Button>(R.id.btnUp).setOnClickListener {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOA" else "\u001B[A"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnDown).setOnClickListener {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOB" else "\u001B[B"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnLeft).setOnClickListener {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOD" else "\u001B[D"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnRight).setOnClickListener {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOC" else "\u001B[C"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnPipe).setOnClickListener { sendBytesToPty("|".toByteArray()) }
        findViewById<Button>(R.id.btnTilde).setOnClickListener { sendBytesToPty("~".toByteArray()) }
        findViewById<Button>(R.id.btnSlash).setOnClickListener { sendBytesToPty("/".toByteArray()) }
        findViewById<Button>(R.id.btnDash).setOnClickListener { sendBytesToPty("-".toByteArray()) }
    }

    private fun processModifiers(input: ByteArray): ByteArray {
        if (!ctrlActive && !altActive) return input

        var result = input
        if (ctrlActive && input.size == 1) {
            val b = input[0]
            val c = b.toInt().toChar()
            if (c in 'a'..'z') {
                result = byteArrayOf((c - 'a' + 1).toByte())
            } else if (c in 'A'..'Z') {
                result = byteArrayOf((c - 'A' + 1).toByte())
            }
            ctrlActive = false
            findViewById<Button>(R.id.btnCtrl).setBackgroundColor(Color.parseColor("#242424"))
        }

        if (altActive) {
            result = byteArrayOf(27) + result
            altActive = false
            findViewById<Button>(R.id.btnAlt).setBackgroundColor(Color.parseColor("#242424"))
        }

        return result
    }

    private fun sendBytesToPty(bytes: ByteArray) {
        ptySession?.let { session ->
            thread {
                try {
                    session.outputStream.write(bytes)
                    session.outputStream.flush()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startShellSession() {
        thread {
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val prootFile = File(nativeLibDir, "libproot.so")

            // Initial banner
            val banner = "TerminalDev v0.1.0\r\n" +
                "Native lib: ${prootFile.name} (verified on targetSdk 35)\r\n\r\n"
            parser.feed(banner.toByteArray(Charsets.UTF_8))
            terminalView.requestRedraw()

            try {
                val session = PtySession.create(
                    cmd = "/system/bin/sh",
                    cwd = filesDir.absolutePath,
                    args = emptyArray(),
                    envVars = arrayOf(
                        "TERM=xterm-256color",
                        "COLORTERM=truecolor",
                        "PATH=/system/bin:/system/xbin",
                        "HOME=${filesDir.absolutePath}"
                    ),
                    rows = terminalView.buffer.rows,
                    cols = terminalView.buffer.columns
                )
                ptySession = session

                val buffer = ByteArray(4096)
                while (true) {
                    val n = session.inputStream.read(buffer)
                    if (n <= 0) break
                    parser.feed(buffer, 0, n)
                    terminalView.requestRedraw()
                }

                val status = session.waitFor()
                parser.feed("\r\n[Process completed: exit $status]\r\n".toByteArray())
                terminalView.requestRedraw()
            } catch (e: Exception) {
                parser.feed("\r\n[Error starting shell: ${e.message}]\r\n".toByteArray())
                terminalView.requestRedraw()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ptySession?.close()
    }
}
