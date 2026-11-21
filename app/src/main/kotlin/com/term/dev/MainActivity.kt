package com.term.dev

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.term.dev.pty.PtySession
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button

    private var currentSession: PtySession? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollView)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)

        btnSend.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                currentSession?.let { session ->
                    thread {
                        try {
                            session.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8))
                            session.outputStream.flush()
                        } catch (e: Exception) {
                            appendOutput("\n[ERROR] Write failed: ${e.message}\n")
                        }
                    }
                }
                etInput.text.clear()
            }
        }

        runInitialTestsAndStartShell()
    }

    private fun runInitialTestsAndStartShell() {
        thread {
            appendOutput("TerminalDev v0.1.0 initializing...\n")

            // Test 1: libproot.so execution from APK nativeLibraryDir on targetSdk 35
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val prootFile = File(nativeLibDir, "libproot.so")
            appendOutput("Checking libproot.so at: ${prootFile.absolutePath}\n")

            if (!prootFile.exists()) {
                appendOutput("[FAILED] libproot.so not found in nativeLibraryDir\n")
            } else {
                appendOutput("Testing execution of libproot.so from nativeLibraryDir...\n")
                try {
                    val testSession = PtySession.create(
                        cmd = prootFile.absolutePath,
                        cwd = filesDir.absolutePath,
                        args = arrayOf("--test-exec"),
                        envVars = arrayOf("TERM=xterm-256color")
                    )

                    val buffer = ByteArray(1024)
                    val bytesRead = testSession.inputStream.read(buffer)
                    val output = if (bytesRead > 0) String(buffer, 0, bytesRead, Charsets.UTF_8) else ""
                    testSession.waitFor()
                    testSession.close()

                    if (output.contains("SUCCESS")) {
                        appendOutput("[VERIFIED] libproot.so executed successfully from nativeLibraryDir!\n")
                        appendOutput("Output: $output\n")
                    } else {
                        appendOutput("[FAILED] libproot.so execution produced unexpected output:\n$output\n")
                    }
                } catch (e: Exception) {
                    appendOutput("[FAILED] Execution of libproot.so failed: ${e.message}\n")
                }
            }

            // Test 2: Interactive shell session
            appendOutput("\nStarting interactive shell /system/bin/sh...\n")
            try {
                val shellSession = PtySession.create(
                    cmd = "/system/bin/sh",
                    cwd = filesDir.absolutePath,
                    args = emptyArray(),
                    envVars = arrayOf(
                        "TERM=xterm-256color",
                        "PATH=/system/bin:/system/xbin",
                        "HOME=${filesDir.absolutePath}"
                    )
                )
                currentSession = shellSession

                val buffer = ByteArray(2048)
                while (true) {
                    val n = shellSession.inputStream.read(buffer)
                    if (n <= 0) break
                    val chunk = String(buffer, 0, n, Charsets.UTF_8)
                    appendOutput(chunk)
                }

                val exitCode = shellSession.waitFor()
                appendOutput("\n[Shell exited with status $exitCode]\n")
            } catch (e: Exception) {
                appendOutput("\n[ERROR] Shell session failed: ${e.message}\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        mainHandler.post {
            tvOutput.append(text)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentSession?.close()
    }
}
