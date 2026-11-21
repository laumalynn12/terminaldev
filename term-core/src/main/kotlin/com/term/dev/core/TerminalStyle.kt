package com.term.dev.core

object TerminalStyle {
    const val STYLE_NONE: Byte = 0
    const val STYLE_BOLD: Byte = 1
    const val STYLE_FAINT: Byte = 2
    const val STYLE_ITALIC: Byte = 4
    const val STYLE_UNDERLINE: Byte = 8
    const val STYLE_BLINK: Byte = 16
    const val STYLE_INVERSE: Byte = 32
    const val STYLE_INVISIBLE: Byte = 64
    const val STYLE_STRIKETHROUGH: Byte = -128

    const val COLOR_DEFAULT_FG: Int = 0x1000000
    const val COLOR_DEFAULT_BG: Int = 0x1000001

    fun isTrueColor(color: Int): Boolean = (color and 0xFF000000.toInt()) == 0

    fun encodeTrueColor(r: Int, g: Int, b: Int): Int {
        return ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    fun encodeIndexedColor(index: Int): Int {
        return 0x02000000 or (index and 0xFF)
    }

    fun isIndexedColor(color: Int): Boolean = (color and 0xFF000000.toInt()) == 0x02000000

    fun getIndex(color: Int): Int = color and 0xFF
}
