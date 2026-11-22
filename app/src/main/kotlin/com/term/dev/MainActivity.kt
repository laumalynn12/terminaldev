package com.term.dev

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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

    override fun onResume() {
        super.onResume()
        terminalView.postDelayed({
            terminalView.showSoftKeyboard()
        }, 300)
    }

    private fun updateModifierButtonState(button: Button, active: Boolean) {
        val bgColor = if (active) Color.parseColor("#484848") else Color.parseColor("#242424")
        val textColor = if (active) Color.parseColor("#33FF33") else Color.parseColor("#E0E0E0")
        button.backgroundTintList = ColorStateList.valueOf(bgColor)
        button.setTextColor(textColor)
    }

    private fun setupRepeatButton(button: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var isRepeating = false
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (isRepeating) {
                    action()
                    handler.postDelayed(this, 50)
                }
            }
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    isRepeating = true
                    handler.removeCallbacks(repeatRunnable)
                    handler.postDelayed(repeatRunnable, 350)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    isRepeating = false
                    handler.removeCallbacks(repeatRunnable)
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupExtraKeys() {
        findViewById<Button>(R.id.btnKbd).setOnClickListener {
            terminalView.toggleSoftKeyboard()
        }

        val btnCtrl = findViewById<Button>(R.id.btnCtrl)
        val btnAlt = findViewById<Button>(R.id.btnAlt)

        btnCtrl.setOnClickListener {
            ctrlActive = !ctrlActive
            updateModifierButtonState(btnCtrl, ctrlActive)
        }

        btnAlt.setOnClickListener {
            altActive = !altActive
            updateModifierButtonState(btnAlt, altActive)
        }

        findViewById<Button>(R.id.btnEsc).setOnClickListener { sendBytesToPty(byteArrayOf(27)) }
        findViewById<Button>(R.id.btnTab).setOnClickListener { sendBytesToPty(byteArrayOf(9)) }

        // Arrow buttons with auto-repeat on hold
        setupRepeatButton(findViewById(R.id.btnUp)) {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOA" else "\u001B[A"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        setupRepeatButton(findViewById(R.id.btnDown)) {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOB" else "\u001B[B"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        setupRepeatButton(findViewById(R.id.btnLeft)) {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOD" else "\u001B[D"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }
        setupRepeatButton(findViewById(R.id.btnRight)) {
            val seq = if (terminalView.buffer.applicationCursorKeys) "\u001BOC" else "\u001B[C"
            sendBytesToPty(seq.toByteArray(Charsets.UTF_8))
        }

        // Navigation keys
        findViewById<Button>(R.id.btnHome).setOnClickListener {
            sendBytesToPty("\u001B[H".toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnEnd).setOnClickListener {
            sendBytesToPty("\u001B[F".toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnPgUp).setOnClickListener {
            sendBytesToPty("\u001B[5~".toByteArray(Charsets.UTF_8))
        }
        findViewById<Button>(R.id.btnPgDn).setOnClickListener {
            sendBytesToPty("\u001B[6~".toByteArray(Charsets.UTF_8))
        }

        // Symbol buttons
        findViewById<Button>(R.id.btnPipe).setOnClickListener { sendBytesToPty("|".toByteArray()) }
        findViewById<Button>(R.id.btnTilde).setOnClickListener { sendBytesToPty("~".toByteArray()) }
        findViewById<Button>(R.id.btnSlash).setOnClickListener { sendBytesToPty("/".toByteArray()) }
        findViewById<Button>(R.id.btnDash).setOnClickListener { sendBytesToPty("-".toByteArray()) }
        findViewById<Button>(R.id.btnBackslash).setOnClickListener { sendBytesToPty("\\".toByteArray()) }
        findViewById<Button>(R.id.btnQuote).setOnClickListener { sendBytesToPty("\"".toByteArray()) }
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
            updateModifierButtonState(findViewById(R.id.btnCtrl), false)
        }

        if (altActive) {
            result = byteArrayOf(27) + result
            altActive = false
            updateModifierButtonState(findViewById(R.id.btnAlt), false)
        }

        return result
    }

    private fun sendBytesToPty(bytes: ByteArray) {
        ptySession?.let { session ->
            thread {
                try {
                    session.outputStream.write(bytes)
                    session.outputStream.flush()
                } catch (e: Exception) {}
            }
        }
    }

    private fun startShellSession() {
        thread {
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val prootFile = File(nativeLibDir, "libproot.so")

            // Initial banner
            val banner = "TerminalDev v0.2.2\r\n" +
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
