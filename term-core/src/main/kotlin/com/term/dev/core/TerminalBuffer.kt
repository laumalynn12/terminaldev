package com.term.dev.core

class TerminalBuffer(
    var rows: Int,
    var columns: Int,
    val maxScrollback: Int = 2000
) {
    private var screen: Array<TerminalRow> = Array(rows) { TerminalRow(columns) }
    private var altScreen: Array<TerminalRow> = Array(rows) { TerminalRow(columns) }
    private var isAltActive = false

    val scrollback: CircularBuffer<TerminalRow> = CircularBuffer(maxScrollback)

    var cursorRow: Int = 0
    var cursorCol: Int = 0

    var savedCursorRow: Int = 0
    var savedCursorCol: Int = 0

    var currentFg: Int = TerminalStyle.COLOR_DEFAULT_FG
    var currentBg: Int = TerminalStyle.COLOR_DEFAULT_BG
    var currentStyle: Byte = TerminalStyle.STYLE_NONE

    var topMargin: Int = 0
    var bottomMargin: Int = rows - 1

    var applicationCursorKeys: Boolean = false
    var bracketedPasteMode: Boolean = false
    var mouseReportingMode: Int = 0
    var sgrMouseMode: Boolean = false
    var cursorVisible: Boolean = true

    fun activeScreen(): Array<TerminalRow> = if (isAltActive) altScreen else screen

    fun useAlternateScreen(enable: Boolean) {
        if (isAltActive != enable) {
            isAltActive = enable
            if (enable) {
                for (row in altScreen) {
                    row.clear(currentFg, currentBg)
                }
            }
            cursorRow = 0
            cursorCol = 0
        }
    }

    fun writeChar(codePoint: Int) {
        val w = WcWidth.width(codePoint)
        if (w == 0) return

        if (cursorCol + w > columns) {
            newLine()
        }

        val row = activeScreen()[cursorRow]
        row.setCell(cursorCol, codePoint, currentFg, currentBg, currentStyle, w.toByte())

        if (w == 2 && cursorCol + 1 < columns) {
            row.setCell(cursorCol + 1, ' '.code, currentFg, currentBg, currentStyle, 0)
        }

        cursorCol += w
        if (cursorCol >= columns) {
            cursorCol = columns - 1
        }
    }

    fun newLine() {
        cursorCol = 0
        if (cursorRow == bottomMargin) {
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    fun carriageReturn() {
        cursorCol = 0
    }

    fun backspace() {
        if (cursorCol > 0) {
            cursorCol--
        }
    }

    fun tab() {
        val nextTab = (cursorCol / 8 + 1) * 8
        cursorCol = minOf(nextTab, columns - 1)
    }

    fun scrollUp(n: Int) {
        val target = activeScreen()
        repeat(n) {
            if (!isAltActive && topMargin == 0 && bottomMargin == rows - 1) {
                val scrolledOut = TerminalRow(columns)
                scrolledOut.copyFrom(target[0])
                scrollback.push(scrolledOut)
            }
            for (r in topMargin until bottomMargin) {
                target[r].copyFrom(target[r + 1])
            }
            target[bottomMargin].clear(currentFg, currentBg)
        }
    }

    fun scrollDown(n: Int) {
        val target = activeScreen()
        repeat(n) {
            for (r in bottomMargin downTo topMargin + 1) {
                target[r].copyFrom(target[r - 1])
            }
            target[topMargin].clear(currentFg, currentBg)
        }
    }

    fun eraseInDisplay(mode: Int) {
        val target = activeScreen()
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in (cursorRow + 1) until rows) {
                    target[r].clear(currentFg, currentBg)
                }
            }
            1 -> {
                for (r in 0 until cursorRow) {
                    target[r].clear(currentFg, currentBg)
                }
                eraseInLine(1)
            }
            2 -> {
                for (r in 0 until rows) {
                    target[r].clear(currentFg, currentBg)
                }
            }
            3 -> {
                eraseInDisplay(2)
                scrollback.clear()
            }
        }
    }

    fun eraseInLine(mode: Int) {
        val row = activeScreen()[cursorRow]
        when (mode) {
            0 -> {
                for (c in cursorCol until columns) {
                    row.setCell(c, ' '.code, currentFg, currentBg, TerminalStyle.STYLE_NONE, 1)
                }
            }
            1 -> {
                for (c in 0..cursorCol) {
                    row.setCell(c, ' '.code, currentFg, currentBg, TerminalStyle.STYLE_NONE, 1)
                }
            }
            2 -> {
                row.clear(currentFg, currentBg)
            }
        }
    }

    fun setCursor(r: Int, c: Int) {
        cursorRow = r.coerceIn(0, rows - 1)
        cursorCol = c.coerceIn(0, columns - 1)
    }

    fun moveCursor(deltaRow: Int, deltaCol: Int) {
        setCursor(cursorRow + deltaRow, cursorCol + deltaCol)
    }

    fun setScrollMargins(top: Int, bottom: Int) {
        if (top in 0 until rows && bottom in top until rows) {
            topMargin = top
            bottomMargin = bottom
            setCursor(topMargin, 0)
        }
    }

    fun resetScrollMargins() {
        topMargin = 0
        bottomMargin = rows - 1
    }

    fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
    }

    fun restoreCursor() {
        setCursor(savedCursorRow, savedCursorCol)
    }

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == columns) return

        val oldScreen = screen
        val oldAlt = altScreen
        screen = Array(newRows) { r ->
            TerminalRow(newCols).apply {
                if (r < rows) copyFrom(oldScreen[r])
            }
        }
        altScreen = Array(newRows) { r ->
            TerminalRow(newCols).apply {
                if (r < rows) copyFrom(oldAlt[r])
            }
        }
        rows = newRows
        columns = newCols
        topMargin = 0
        bottomMargin = rows - 1
        setCursor(cursorRow, cursorCol)
    }

    fun getLineText(row: Int): String {
        return if (row in 0 until rows) activeScreen()[row].asString() else ""
    }
}
