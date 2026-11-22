package com.term.dev.core

class TerminalParser(
    val buffer: TerminalBuffer,
    var responseCallback: ((ByteArray) -> Unit)? = null,
    var titleCallback: ((String) -> Unit)? = null
) {
    private enum class State {
        GROUND,
        ESCAPE,
        CSI_ENTRY,
        CSI_PARAM,
        OSC_STRING
    }

    private var state = State.GROUND
    private val params = IntArray(16)
    private var paramCount = 0
    private var isPrivateMode = false
    private val oscBuffer = StringBuilder()

    // UTF-8 state decoder
    private var utf8CodePoint = 0
    private var utf8ExpectedBytes = 0

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        val end = offset + length
        var i = offset
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            i++

            // UTF-8 decoding
            if (utf8ExpectedBytes == 0) {
                when {
                    b < 0x80 -> processByte(b)
                    b in 0xC0..0xDF -> {
                        utf8CodePoint = b and 0x1F
                        utf8ExpectedBytes = 1
                    }
                    b in 0xE0..0xEF -> {
                        utf8CodePoint = b and 0x0F
                        utf8ExpectedBytes = 2
                    }
                    b in 0xF0..0xF7 -> {
                        utf8CodePoint = b and 0x07
                        utf8ExpectedBytes = 3
                    }
                    else -> processByte('?'.code)
                }
            } else {
                if (b in 0x80..0xBF) {
                    utf8CodePoint = (utf8CodePoint shl 6) or (b and 0x3F)
                    utf8ExpectedBytes--
                    if (utf8ExpectedBytes == 0) {
                        processByte(utf8CodePoint)
                    }
                } else {
                    utf8ExpectedBytes = 0
                    processByte('?'.code)
                }
            }
        }
    }

    private fun processByte(codePoint: Int) {
        when (state) {
            State.GROUND -> {
                when (codePoint) {
                    0x1B -> {
                        state = State.ESCAPE
                    }
                    0x0A, 0x0B, 0x0C -> buffer.newLine()
                    0x0D -> buffer.carriageReturn()
                    0x08 -> buffer.backspace()
                    0x09 -> buffer.tab()
                    0x07 -> { /* Bell */ }
                    else -> {
                        if (codePoint >= 32) {
                            buffer.writeChar(codePoint)
                        }
                    }
                }
            }

            State.ESCAPE -> {
                when (codePoint) {
                    '['.code -> {
                        state = State.CSI_ENTRY
                        resetParams()
                    }
                    ']'.code -> {
                        state = State.OSC_STRING
                        oscBuffer.setLength(0)
                    }
                    '7'.code -> {
                        buffer.saveCursor()
                        state = State.GROUND
                    }
                    '8'.code -> {
                        buffer.restoreCursor()
                        state = State.GROUND
                    }
                    'M'.code -> {
                        buffer.scrollDown(1)
                        state = State.GROUND
                    }
                    'E'.code -> {
                        buffer.newLine()
                        state = State.GROUND
                    }
                    else -> {
                        state = State.GROUND
                    }
                }
            }

            State.CSI_ENTRY, State.CSI_PARAM -> {
                when {
                    codePoint == '?'.code && state == State.CSI_ENTRY -> {
                        isPrivateMode = true
                        state = State.CSI_PARAM
                    }
                    codePoint in '0'.code..'9'.code -> {
                        state = State.CSI_PARAM
                        if (paramCount == 0) paramCount = 1
                        params[paramCount - 1] = params[paramCount - 1] * 10 + (codePoint - '0'.code)
                    }
                    codePoint == ';'.code -> {
                        state = State.CSI_PARAM
                        if (paramCount < params.size) {
                            paramCount++
                        }
                    }
                    codePoint in 0x40..0x7E -> {
                        dispatchCsi(codePoint.toChar())
                        state = State.GROUND
                    }
                    else -> {
                        state = State.GROUND
                    }
                }
            }

            State.OSC_STRING -> {
                when (codePoint) {
                    0x07 -> {
                        dispatchOsc()
                        state = State.GROUND
                    }
                    0x1B -> {
                        dispatchOsc()
                        state = State.ESCAPE
                    }
                    else -> {
                        oscBuffer.appendCodePoint(codePoint)
                    }
                }
            }
        }
    }

    fun reset() {
        state = State.GROUND
        resetParams()
        oscBuffer.setLength(0)
        utf8Remaining = 0
        utf8CodePoint = 0
    }

    private fun resetParams() {
        params.fill(0)
        paramCount = 0
        isPrivateMode = false
    }

    private fun getParam(index: Int, default: Int): Int {
        return if (index < paramCount && params[index] > 0) params[index] else default
    }

    private fun dispatchCsi(finalChar: Char) {
        if (isPrivateMode) {
            val mode = getParam(0, 0)
            when (finalChar) {
                'h' -> setPrivateMode(mode, true)
                'l' -> setPrivateMode(mode, false)
            }
            return
        }

        when (finalChar) {
            'A' -> buffer.moveCursor(-getParam(0, 1), 0)
            'B' -> buffer.moveCursor(getParam(0, 1), 0)
            'C' -> buffer.moveCursor(0, getParam(0, 1))
            'D' -> buffer.moveCursor(0, -getParam(0, 1))
            'E' -> {
                buffer.moveCursor(getParam(0, 1), 0)
                buffer.carriageReturn()
            }
            'F' -> {
                buffer.moveCursor(-getParam(0, 1), 0)
                buffer.carriageReturn()
            }
            'G' -> buffer.setCursor(buffer.cursorRow, getParam(0, 1) - 1)
            'H', 'f' -> {
                val r = getParam(0, 1) - 1
                val c = getParam(1, 1) - 1
                buffer.setCursor(r, c)
            }
            'J' -> buffer.eraseInDisplay(getParam(0, 0))
            'K' -> buffer.eraseInLine(getParam(0, 0))
            'S' -> buffer.scrollUp(getParam(0, 1))
            'T' -> buffer.scrollDown(getParam(0, 1))
            'm' -> dispatchSgr()
            'r' -> {
                val top = getParam(0, 1) - 1
                val bottom = if (paramCount >= 2 && params[1] > 0) params[1] - 1 else buffer.rows - 1
                buffer.setScrollMargins(top, bottom)
            }
            'n' -> {
                if (getParam(0, 0) == 6) {
                    val report = "\u001B[${buffer.cursorRow + 1};${buffer.cursorCol + 1}R"
                    responseCallback?.invoke(report.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun setPrivateMode(mode: Int, enable: Boolean) {
        when (mode) {
            1 -> buffer.applicationCursorKeys = enable
            25 -> buffer.cursorVisible = enable
            1000 -> buffer.mouseReportingMode = if (enable) 1000 else 0
            1002 -> buffer.mouseReportingMode = if (enable) 1002 else 0
            1003 -> buffer.mouseReportingMode = if (enable) 1003 else 0
            1006 -> buffer.sgrMouseMode = enable
            1049 -> buffer.useAlternateScreen(enable)
            2004 -> buffer.bracketedPasteMode = enable
        }
    }

    private fun dispatchSgr() {
        if (paramCount == 0) {
            buffer.currentStyle = TerminalStyle.STYLE_NONE
            buffer.currentFg = TerminalStyle.COLOR_DEFAULT_FG
            buffer.currentBg = TerminalStyle.COLOR_DEFAULT_BG
            return
        }

        var idx = 0
        while (idx < paramCount) {
            val p = params[idx]
            when (p) {
                0 -> {
                    buffer.currentStyle = TerminalStyle.STYLE_NONE
                    buffer.currentFg = TerminalStyle.COLOR_DEFAULT_FG
                    buffer.currentBg = TerminalStyle.COLOR_DEFAULT_BG
                }
                1 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_BOLD.toInt()).toByte()
                2 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_FAINT.toInt()).toByte()
                3 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_ITALIC.toInt()).toByte()
                4 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_UNDERLINE.toInt()).toByte()
                5 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_BLINK.toInt()).toByte()
                7 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_INVERSE.toInt()).toByte()
                8 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_INVISIBLE.toInt()).toByte()
                9 -> buffer.currentStyle = (buffer.currentStyle.toInt() or TerminalStyle.STYLE_STRIKETHROUGH.toInt()).toByte()
                22 -> buffer.currentStyle = (buffer.currentStyle.toInt() and (TerminalStyle.STYLE_BOLD.toInt() or TerminalStyle.STYLE_FAINT.toInt()).inv()).toByte()
                23 -> buffer.currentStyle = (buffer.currentStyle.toInt() and TerminalStyle.STYLE_ITALIC.toInt().inv()).toByte()
                24 -> buffer.currentStyle = (buffer.currentStyle.toInt() and TerminalStyle.STYLE_UNDERLINE.toInt().inv()).toByte()
                27 -> buffer.currentStyle = (buffer.currentStyle.toInt() and TerminalStyle.STYLE_INVERSE.toInt().inv()).toByte()
                in 30..37 -> buffer.currentFg = TerminalStyle.encodeIndexedColor(p - 30)
                38 -> {
                    if (idx + 1 < paramCount) {
                        if (params[idx + 1] == 5 && idx + 2 < paramCount) {
                            buffer.currentFg = TerminalStyle.encodeIndexedColor(params[idx + 2])
                            idx += 2
                        } else if (params[idx + 1] == 2 && idx + 4 < paramCount) {
                            buffer.currentFg = TerminalStyle.encodeTrueColor(params[idx + 2], params[idx + 3], params[idx + 4])
                            idx += 4
                        }
                    }
                }
                39 -> buffer.currentFg = TerminalStyle.COLOR_DEFAULT_FG
                in 40..47 -> buffer.currentBg = TerminalStyle.encodeIndexedColor(p - 40)
                48 -> {
                    if (idx + 1 < paramCount) {
                        if (params[idx + 1] == 5 && idx + 2 < paramCount) {
                            buffer.currentBg = TerminalStyle.encodeIndexedColor(params[idx + 2])
                            idx += 2
                        } else if (params[idx + 1] == 2 && idx + 4 < paramCount) {
                            buffer.currentBg = TerminalStyle.encodeTrueColor(params[idx + 2], params[idx + 3], params[idx + 4])
                            idx += 4
                        }
                    }
                }
                49 -> buffer.currentBg = TerminalStyle.COLOR_DEFAULT_BG
                in 90..97 -> buffer.currentFg = TerminalStyle.encodeIndexedColor(p - 90 + 8)
                in 100..107 -> buffer.currentBg = TerminalStyle.encodeIndexedColor(p - 100 + 8)
            }
            idx++
        }
    }

    private fun dispatchOsc() {
        val s = oscBuffer.toString()
        val semicolonIdx = s.indexOf(';')
        if (semicolonIdx > 0) {
            val cmd = s.substring(0, semicolonIdx).toIntOrNull()
            val arg = s.substring(semicolonIdx + 1)
            if (cmd == 0 || cmd == 2) {
                titleCallback?.invoke(arg)
            }
        }
    }
}
