package com.term.dev.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.term.dev.core.TerminalBuffer
import com.term.dev.core.TerminalStyle
import java.util.concurrent.atomic.AtomicBoolean

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    var buffer: TerminalBuffer = TerminalBuffer(rows = 24, columns = 80)
    var onInput: ((ByteArray) -> Unit)? = null
    var onResize: ((rows: Int, cols: Int) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val bgPaint = Paint()
    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#33FF33")
    }

    var textSizeSp: Float = 13f
        set(value) {
            field = value.coerceIn(8f, 36f)
            updateFontMetrics()
            recalculateTerminalDimensions()
            requestRedraw()
        }

    var charWidth: Float = 0f
        private set
    var charHeight: Float = 0f
        private set
    private var charAscent: Float = 0f

    var scrollOffsetY: Int = 0
        set(value) {
            field = value.coerceIn(0, buffer.scrollback.size)
            requestRedraw()
        }

    private val pendingRedraw = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val colorPalette = IntArray(256)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val linesDelta = (distanceY / charHeight).toInt()
            if (linesDelta != 0) {
                scrollOffsetY = (scrollOffsetY + linesDelta).coerceIn(0, buffer.scrollback.size)
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            textSizeSp *= detector.scaleFactor
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        initColorPalette()
        updateFontMetrics()
    }

    private fun initColorPalette() {
        // 0..15 ANSI colors
        val standardColors = intArrayOf(
            0x000000, 0xCD3131, 0x0DBC79, 0xE5E510, 0x2472C8, 0xBC3FBC, 0x11A8CD, 0xE5E5E5,
            0x666666, 0xF14C4C, 0x23D18B, 0xF5F543, 0x3B8EEA, 0xD670D6, 0x29B8DB, 0xFFFFFF
        )
        for (i in 0..15) {
            colorPalette[i] = 0xFF000000.toInt() or standardColors[i]
        }
        // 16..231: 6x6x6 color cube
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    val idx = 16 + (r * 36) + (g * 6) + b
                    colorPalette[idx] = Color.rgb(steps[r], steps[g], steps[b])
                }
            }
        }
        // 232..255: Grayscale ramp
        for (i in 0..23) {
            val gray = 8 + i * 10
            colorPalette[232 + i] = Color.rgb(gray, gray, gray)
        }
    }

    private fun updateFontMetrics() {
        val density = resources.displayMetrics.scaledDensity
        textPaint.textSize = textSizeSp * density
        val fm = textPaint.fontMetrics
        charWidth = textPaint.measureText("M")
        charHeight = fm.bottom - fm.top
        charAscent = -fm.top
    }

    private fun recalculateTerminalDimensions() {
        if (width <= 0 || height <= 0 || charWidth <= 0f || charHeight <= 0f) return
        val newCols = maxOf(10, (width / charWidth).toInt())
        val newRows = maxOf(4, (height / charHeight).toInt())
        if (newCols != buffer.columns || newRows != buffer.rows) {
            buffer.resize(newRows, newCols)
            onResize?.invoke(newRows, newCols)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateTerminalDimensions()
    }

    fun requestRedraw() {
        if (pendingRedraw.compareAndSet(false, true)) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                mainHandler.post {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        pendingRedraw.set(false)
        invalidate()
    }

    private fun resolveColor(encodedColor: Int, isBackground: Boolean): Int {
        return when {
            encodedColor == TerminalStyle.COLOR_DEFAULT_FG -> Color.parseColor("#E5E5E5")
            encodedColor == TerminalStyle.COLOR_DEFAULT_BG -> Color.parseColor("#0C0C0C")
            TerminalStyle.isIndexedColor(encodedColor) -> {
                val idx = TerminalStyle.getIndex(encodedColor)
                colorPalette[idx]
            }
            TerminalStyle.isTrueColor(encodedColor) -> {
                0xFF000000.toInt() or encodedColor
            }
            else -> if (isBackground) Color.parseColor("#0C0C0C") else Color.parseColor("#E5E5E5")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0C0C0C"))

        val totalRows = buffer.rows
        val totalCols = buffer.columns
        val activeRows = buffer.activeScreen()
        val scrollbackSize = buffer.scrollback.size

        for (screenRowIdx in 0 until totalRows) {
            val y = screenRowIdx * charHeight

            val row = if (scrollOffsetY > 0) {
                val historyIdx = scrollbackSize - scrollOffsetY + screenRowIdx
                if (historyIdx in 0 until scrollbackSize) {
                    buffer.scrollback.get(historyIdx)
                } else {
                    val activeIdx = historyIdx - scrollbackSize
                    if (activeIdx in 0 until totalRows) activeRows[activeIdx] else null
                }
            } else {
                activeRows[screenRowIdx]
            } ?: continue

            var col = 0
            while (col < totalCols) {
                val startCol = col
                val fg = row.fgColors[col]
                val bg = row.bgColors[col]
                val style = row.styles[col]
                val spanBuilder = StringBuilder()

                while (col < totalCols &&
                    row.fgColors[col] == fg &&
                    row.bgColors[col] == bg &&
                    row.styles[col] == style
                ) {
                    val cp = row.codePoints[col]
                    val w = row.widths[col].toInt()
                    if (w > 0) {
                        spanBuilder.appendCodePoint(cp)
                    }
                    col++
                }

                val spanLength = col - startCol
                val x1 = startCol * charWidth
                val x2 = col * charWidth

                val resolvedBg = resolveColor(bg, isBackground = true)
                if (resolvedBg != Color.parseColor("#0C0C0C")) {
                    bgPaint.color = resolvedBg
                    canvas.drawRect(x1, y, x2, y + charHeight, bgPaint)
                }

                val resolvedFg = resolveColor(fg, isBackground = false)
                textPaint.color = resolvedFg
                textPaint.isFakeBoldText = (style.toInt() and TerminalStyle.STYLE_BOLD.toInt()) != 0
                textPaint.isUnderlineText = (style.toInt() and TerminalStyle.STYLE_UNDERLINE.toInt()) != 0

                canvas.drawText(spanBuilder.toString(), x1, y + charAscent, textPaint)
            }

            // Draw cursor
            if (scrollOffsetY == 0 && buffer.cursorVisible && buffer.cursorRow == screenRowIdx) {
                val cx = buffer.cursorCol * charWidth
                canvas.drawRect(cx, y, cx + charWidth, y + charHeight, cursorPaint)
                if (buffer.cursorCol in 0 until totalCols) {
                    val cp = row.codePoints[buffer.cursorCol]
                    if (cp != ' '.code) {
                        textPaint.color = Color.BLACK
                        canvas.drawText(String(Character.toChars(cp)), cx, y + charAscent, textPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) return true

        if (buffer.mouseReportingMode > 0) {
            val col = (event.x / charWidth).toInt().coerceIn(0, buffer.columns - 1)
            val row = (event.y / charHeight).toInt().coerceIn(0, buffer.rows - 1)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val sgr = KeyMapper.formatMouseSgr(0, col, row, press = true)
                    onInput?.invoke(sgr)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val sgr = KeyMapper.formatMouseSgr(0, col, row, press = false)
                    onInput?.invoke(sgr)
                    return true
                }
            }
        }

        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val bytes = KeyMapper.mapKeyEvent(event, buffer.applicationCursorKeys)
        if (bytes != null) {
            scrollOffsetY = 0
            onInput?.invoke(bytes)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.let {
                    scrollOffsetY = 0
                    onInput?.invoke(it.toString().toByteArray(Charsets.UTF_8))
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    scrollOffsetY = 0
                    onInput?.invoke(byteArrayOf(127)) // DEL / Backspace
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return onKeyDown(event.keyCode, event)
            }
        }
    }
}
