package com.term.dev.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalParserTest {

    @Test
    fun testBasicTextAndCursorMovement() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        val parser = TerminalParser(buffer)

        val input = "Hello World!\r\nLine 2".toByteArray(Charsets.UTF_8)
        parser.feed(input)

        assertEquals("Hello World!", buffer.getLineText(0))
        assertEquals("Line 2", buffer.getLineText(1))
        assertEquals(1, buffer.cursorRow)
        assertEquals(6, buffer.cursorCol)
    }

    @Test
    fun testCursorPositioningCSI() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        val parser = TerminalParser(buffer)

        // Move to row 5, col 10 (1-based: \e[5;10H)
        val cup = "\u001B[5;10HTest".toByteArray(Charsets.UTF_8)
        parser.feed(cup)

        assertEquals(4, buffer.cursorRow)
        assertEquals(13, buffer.cursorCol)
        assertEquals("Test", buffer.getLineText(4).substring(9))
    }

    @Test
    fun testSgrColorsAndStyles() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        val parser = TerminalParser(buffer)

        // Bold + Red FG (\e[1;31m)
        parser.feed("\u001B[1;31mB".toByteArray(Charsets.UTF_8))
        val row = buffer.activeScreen()[0]
        assertEquals('B'.code, row.codePoints[0])
        assertEquals(TerminalStyle.STYLE_BOLD, row.styles[0])
        assertEquals(TerminalStyle.encodeIndexedColor(1), row.fgColors[0])

        // 256 color FG (\e[38;5;123m)
        parser.feed("\u001B[38;5;123mC".toByteArray(Charsets.UTF_8))
        assertEquals('C'.code, row.codePoints[1])
        assertEquals(TerminalStyle.encodeIndexedColor(123), row.fgColors[1])

        // TrueColor 24-bit FG (\e[38;2;255;128;64m)
        parser.feed("\u001B[38;2;255;128;64mD".toByteArray(Charsets.UTF_8))
        assertEquals('D'.code, row.codePoints[2])
        assertEquals(TerminalStyle.encodeTrueColor(255, 128, 64), row.fgColors[2])
    }

    @Test
    fun testAlternateScreenBuffer() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        val parser = TerminalParser(buffer)

        parser.feed("Main Screen".toByteArray(Charsets.UTF_8))
        assertEquals("Main Screen", buffer.getLineText(0))

        // Switch to alternate screen (\e[?1049h)
        parser.feed("\u001B[?1049hAlt Screen".toByteArray(Charsets.UTF_8))
        assertEquals("Alt Screen", buffer.getLineText(0))

        // Switch back to main screen (\e[?1049l)
        parser.feed("\u001B[?1049l".toByteArray(Charsets.UTF_8))
        assertEquals("Main Screen", buffer.getLineText(0))
    }

    @Test
    fun testCursorPositionReportCPR() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        var response = ""
        val parser = TerminalParser(buffer, responseCallback = { bytes ->
            response = String(bytes, Charsets.UTF_8)
        })

        // Position at row 4, col 8 (1-based: 4;8), then request CPR (\e[6n)
        parser.feed("\u001B[4;8H\u001B[6n".toByteArray(Charsets.UTF_8))
        assertEquals("\u001B[4;8R", response)
    }

    @Test
    fun testEraseDisplayAndLine() {
        val buffer = TerminalBuffer(rows = 24, columns = 80)
        val parser = TerminalParser(buffer)

        parser.feed("ABCDEF".toByteArray(Charsets.UTF_8))
        parser.feed("\u001B[1;3H\u001B[K".toByteArray(Charsets.UTF_8)) // Position at col 3 (1-based), erase to end of line
        assertEquals("AB", buffer.getLineText(0))
    }
}
