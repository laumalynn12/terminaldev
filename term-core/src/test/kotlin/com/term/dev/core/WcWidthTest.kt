package com.term.dev.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WcWidthTest {

    @Test
    fun testAsciiWidth() {
        assertEquals(1, WcWidth.width('A'.code))
        assertEquals(1, WcWidth.width('z'.code))
        assertEquals(1, WcWidth.width('9'.code))
        assertEquals(1, WcWidth.width('$'.code))
    }

    @Test
    fun testWideCharacters() {
        // CJK characters
        assertEquals(2, WcWidth.width('中'.code))
        assertEquals(2, WcWidth.width('文'.code))
        assertEquals(2, WcWidth.width('日'.code))
        assertEquals(2, WcWidth.width('本'.code))

        // Emojis
        assertEquals(2, WcWidth.width(0x1F600)) // Grinning face
        assertEquals(2, WcWidth.width(0x1F680)) // Rocket
    }

    @Test
    fun testControlAndCombiningCharacters() {
        assertEquals(0, WcWidth.width(0))
        assertEquals(0, WcWidth.width(0x1B)) // Escape
        assertEquals(0, WcWidth.width(0x0300)) // Combining grave accent
    }
}
