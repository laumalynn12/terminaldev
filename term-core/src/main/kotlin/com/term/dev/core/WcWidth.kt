package com.term.dev.core

object WcWidth {
    fun width(codePoint: Int): Int {
        if (codePoint < 32 || (codePoint in 0x7f..0x9f)) {
            return 0
        }

        // Combining characters
        if (codePoint in 0x0300..0x036f ||
            codePoint in 0x1dc0..0x1dff ||
            codePoint in 0x20d0..0x20ff ||
            codePoint in 0xfe20..0xfe2f
        ) {
            return 0
        }

        // Fast path for ASCII
        if (codePoint in 32..126) {
            return 1
        }

        // East Asian Wide and Fullwidth characters
        if (codePoint in 0x1100..0x115f ||
            codePoint == 0x2329 || codePoint == 0x232a ||
            codePoint in 0x2e80..0x303e ||
            codePoint in 0x3040..0xa4cf ||
            codePoint in 0xac00..0xd7a3 ||
            codePoint in 0xf900..0xfaff ||
            codePoint in 0xfe10..0xfe19 ||
            codePoint in 0xfe30..0xfe6f ||
            codePoint in 0xff00..0xff60 ||
            codePoint in 0xffe0..0xffe6 ||
            codePoint in 0x1f300..0x1f64f || // Miscellaneous Symbols and Pictographs, Emoticons
            codePoint in 0x1f900..0x1f9ff || // Supplemental Symbols and Pictographs
            codePoint in 0x20000..0x2fffd ||
            codePoint in 0x30000..0x3fffd
        ) {
            return 2
        }

        return 1
    }
}
