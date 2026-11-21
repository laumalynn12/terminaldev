package com.term.dev.view

import android.view.KeyEvent

object KeyMapper {

    fun mapKeyEvent(event: KeyEvent, appCursorKeys: Boolean = false): ByteArray? {
        if (event.action == KeyEvent.ACTION_UP) return null

        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed
        val shift = event.isShiftPressed

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> return byteArrayOf(13) // \r
            KeyEvent.KEYCODE_DEL -> return byteArrayOf(127) // DEL
            KeyEvent.KEYCODE_TAB -> return if (shift) "\u001B[Z".toByteArray() else byteArrayOf(9)
            KeyEvent.KEYCODE_ESCAPE -> return byteArrayOf(27)

            KeyEvent.KEYCODE_DPAD_UP -> return if (appCursorKeys) "\u001BOA".toByteArray() else "\u001B[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN -> return if (appCursorKeys) "\u001BOB".toByteArray() else "\u001B[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT -> return if (appCursorKeys) "\u001BOC".toByteArray() else "\u001B[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT -> return if (appCursorKeys) "\u001BOD".toByteArray() else "\u001B[D".toByteArray()

            KeyEvent.KEYCODE_MOVE_HOME -> return "\u001B[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END -> return "\u001B[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> return "\u001B[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN -> return "\u001B[6~".toByteArray()
            KeyEvent.KEYCODE_FORWARD_DEL -> return "\u001B[3~".toByteArray()
            KeyEvent.KEYCODE_INSERT -> return "\u001B[2~".toByteArray()

            KeyEvent.KEYCODE_F1 -> return "\u001BOP".toByteArray()
            KeyEvent.KEYCODE_F2 -> return "\u001BOQ".toByteArray()
            KeyEvent.KEYCODE_F3 -> return "\u001BOR".toByteArray()
            KeyEvent.KEYCODE_F4 -> return "\u001BOS".toByteArray()
            KeyEvent.KEYCODE_F5 -> return "\u001B[15~".toByteArray()
            KeyEvent.KEYCODE_F6 -> return "\u001B[17~".toByteArray()
            KeyEvent.KEYCODE_F7 -> return "\u001B[18~".toByteArray()
            KeyEvent.KEYCODE_F8 -> return "\u001B[19~".toByteArray()
            KeyEvent.KEYCODE_F9 -> return "\u001B[20~".toByteArray()
            KeyEvent.KEYCODE_F10 -> return "\u001B[21~".toByteArray()
            KeyEvent.KEYCODE_F11 -> return "\u001B[23~".toByteArray()
            KeyEvent.KEYCODE_F12 -> return "\u001B[24~".toByteArray()
        }

        // Handle Ctrl combinations
        if (ctrl) {
            val unicode = event.unicodeChar
            val char = unicode.toChar()
            if (char in 'a'..'z') {
                return byteArrayOf((char - 'a' + 1).toByte())
            } else if (char in 'A'..'Z') {
                return byteArrayOf((char - 'A' + 1).toByte())
            }
            when (char) {
                '@', ' ' -> return byteArrayOf(0)
                '[' -> return byteArrayOf(27)
                '\\' -> return byteArrayOf(28)
                ']' -> return byteArrayOf(29)
                '^' -> return byteArrayOf(30)
                '_' -> return byteArrayOf(31)
            }
        }

        val cp = event.keyCharacterMap.get(event.keyCode, event.metaState)
        if (cp > 0) {
            val text = String(Character.toChars(cp))
            return if (alt) {
                ("\u001B" + text).toByteArray(Charsets.UTF_8)
            } else {
                text.toByteArray(Charsets.UTF_8)
            }
        }

        return null
    }

    fun formatMouseSgr(button: Int, col: Int, row: Int, press: Boolean): ByteArray {
        val finalChar = if (press) 'M' else 'm'
        return "\u001B[<${button};${col + 1};${row + 1}$finalChar".toByteArray(Charsets.UTF_8)
    }
}
