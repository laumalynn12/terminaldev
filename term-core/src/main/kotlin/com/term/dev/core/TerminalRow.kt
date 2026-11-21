package com.term.dev.core

class TerminalRow(val columns: Int) {
    val codePoints: IntArray = IntArray(columns) { ' '.code }
    val fgColors: IntArray = IntArray(columns) { TerminalStyle.COLOR_DEFAULT_FG }
    val bgColors: IntArray = IntArray(columns) { TerminalStyle.COLOR_DEFAULT_BG }
    val styles: ByteArray = ByteArray(columns)
    val widths: ByteArray = ByteArray(columns) { 1 }

    fun clear(fg: Int = TerminalStyle.COLOR_DEFAULT_FG, bg: Int = TerminalStyle.COLOR_DEFAULT_BG) {
        codePoints.fill(' '.code)
        fgColors.fill(fg)
        bgColors.fill(bg)
        styles.fill(TerminalStyle.STYLE_NONE)
        widths.fill(1)
    }

    fun copyFrom(other: TerminalRow) {
        val count = minOf(columns, other.columns)
        System.arraycopy(other.codePoints, 0, codePoints, 0, count)
        System.arraycopy(other.fgColors, 0, fgColors, 0, count)
        System.arraycopy(other.bgColors, 0, bgColors, 0, count)
        System.arraycopy(other.styles, 0, styles, 0, count)
        System.arraycopy(other.widths, 0, widths, 0, count)
        if (columns > count) {
            codePoints.fill(' '.code, count, columns)
            fgColors.fill(TerminalStyle.COLOR_DEFAULT_FG, count, columns)
            bgColors.fill(TerminalStyle.COLOR_DEFAULT_BG, count, columns)
            styles.fill(TerminalStyle.STYLE_NONE, count, columns)
            widths.fill(1, count, columns)
        }
    }

    fun setCell(col: Int, cp: Int, fg: Int, bg: Int, style: Byte, width: Byte) {
        if (col in 0 until columns) {
            codePoints[col] = cp
            fgColors[col] = fg
            bgColors[col] = bg
            styles[col] = style
            widths[col] = width
        }
    }

    fun asString(): String {
        val sb = StringBuilder(columns)
        for (i in 0 until columns) {
            if (widths[i].toInt() != 0) {
                sb.appendCodePoint(codePoints[i])
            }
        }
        return sb.toString().trimEnd()
    }
}
