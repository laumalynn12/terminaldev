package com.term.dev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.term.dev.core.TerminalParser
import com.term.dev.pty.PtySession
import com.term.dev.sandbox.ProotLauncher
import com.term.dev.sandbox.RootfsInstaller
import com.term.dev.sandbox.SandboxConfig
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
        checkStoragePermissions()
        startShellSession()
    }

    override fun onResume() {
        super.onResume()
        terminalView.postDelayed({
            terminalView.showSoftKeyboard()
        }, 300)
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (ignored: Exception) {}
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePerm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (readPerm != PackageManager.PERMISSION_GRANTED || writePerm != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            }
        }
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
                    terminalView.onUserAction()
                    action()
                    handler.postDelayed(this, 50)
                }
            }
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    terminalView.onUserAction()
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
            terminalView.onUserAction()
            terminalView.toggleSoftKeyboard()
        }

        val btnCtrl = findViewById<Button>(R.id.btnCtrl)
        val btnAlt = findViewById<Button>(R.id.btnAlt)

        btnCtrl.setOnClickListener {
            terminalView.onUserAction()
            ctrlActive = !ctrlActive
            updateModifierButtonState(btnCtrl, ctrlActive)
        }

        btnAlt.setOnClickListener {
            terminalView.onUserAction()
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

    private fun processModifiers(bytes: ByteArray): ByteArray {
        if (!ctrlActive && !altActive) return bytes

        var result = bytes
        if (ctrlActive && bytes.size == 1) {
            val c = bytes[0].toInt().toChar()
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
        terminalView.onUserAction()
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
            val ubuntuDir = File(filesDir, "ubuntu")
            val tmpDir = File(filesDir, "tmp")
            if (!tmpDir.exists()) tmpDir.mkdirs()

            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val pkg = SandboxConfig.getPackageForAbi(primaryAbi)
            val installer = RootfsInstaller()

            if (!installer.isInstalled(ubuntuDir)) {
                val initBanner = "\u001B[1;36mTerminalDev v0.3.0\u001B[0m (Ubuntu 24.04.5 LTS Sandbox)\r\n" +
                    "Architecture: $primaryAbi | targetSdk: 35\r\n\r\n" +
                    "\u001B[33mGuest rootfs not found. Initializing Ubuntu sandbox...\u001B[0m\r\n"
                parser.feed(initBanner.toByteArray(Charsets.UTF_8))
                terminalView.requestRedraw()

                try {
                    installer.install(pkg, ubuntuDir, tmpDir) { progress ->
                        val line = "\r\u001B[K[setup] ${progress.message}\r\n"
                        parser.feed(line.toByteArray(Charsets.UTF_8))
                        terminalView.requestRedraw()
                    }
                    parser.feed("\r\n\u001B[1;32m[setup] Rootfs successfully installed.\u001B[0m\r\n\r\n".toByteArray(Charsets.UTF_8))
                    terminalView.requestRedraw()
                } catch (e: Exception) {
                    val errBanner = "\r\n\u001B[1;31m[setup] Installation failed: ${e.message}\u001B[0m\r\n" +
                        "Falling back to Android host shell /system/bin/sh...\r\n\r\n"
                    parser.feed(errBanner.toByteArray(Charsets.UTF_8))
                    terminalView.requestRedraw()
                    startHostShellFallback()
                    return@thread
                }
            }

            // Launch Ubuntu guest via PRoot
            val prootFile = File(nativeLibDir, "libproot.so")
            if (!prootFile.exists()) {
                val errBanner = "\r\n\u001B[1;31m[error] libproot.so not found in $nativeLibDir\u001B[0m\r\n" +
                    "Falling back to Android host shell /system/bin/sh...\r\n\r\n"
                parser.feed(errBanner.toByteArray(Charsets.UTF_8))
                terminalView.requestRedraw()
                startHostShellFallback()
                return@thread
            }

            val launcher = ProotLauncher()
            val prootCmd = launcher.buildLaunchCommand(
                nativeLibDir = nativeLibDir,
                ubuntuDir = ubuntuDir,
                tmpDir = tmpDir,
                guestCommand = listOf("/bin/bash", "-l")
            )

            try {
                val session = PtySession.create(
                    cmd = prootCmd.executable,
                    cwd = prootCmd.cwd,
                    args = prootCmd.args,
                    envVars = prootCmd.envVars,
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
                val errBanner = "\r\n\u001B[1;31m[error] Failed to launch PRoot: ${e.message}\u001B[0m\r\n" +
                    "Falling back to Android host shell...\r\n\r\n"
                parser.feed(errBanner.toByteArray(Charsets.UTF_8))
                terminalView.requestRedraw()
                startHostShellFallback()
            }
        }
    }

    private fun startHostShellFallback() {
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
            parser.feed("\r\n[Host shell completed: exit $status]\r\n".toByteArray())
            terminalView.requestRedraw()
        } catch (e: Exception) {
            parser.feed("\r\n[Error starting shell: ${e.message}]\r\n".toByteArray())
            terminalView.requestRedraw()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ptySession?.close()
    }
}
