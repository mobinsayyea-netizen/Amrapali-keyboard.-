package com.mobeen.amrapali

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat

/**
 * Draws the keyboard and turns touches into key presses.
 *
 * Interaction model (this is the whole point of the app, so it's worth spelling out):
 *  - A single tap on a key SPEAKS it (via the [Listener.onAnnounce] callback) but does
 *    NOT type it. This lets a visually-impaired user find a key safely before committing
 *    to it — the same idea as touch-exploration, just built directly into the keyboard
 *    so it does not depend on TalkBack being on.
 *  - Confirming that tap types it (via [Listener.onKey]): either a fast double-tap
 *    anywhere on the keyboard within [FAST_CONFIRM_MS], or a slower re-tap on the
 *    exact same key within [SAME_KEY_CONFIRM_MS].
 *  - The language-switch key is special: a normal double-tap cycles the language, but
 *    if the *second* tap is held down for longer than [LONG_PRESS_MS] instead of being
 *    released quickly, the emoji panel opens instead ("double-tap-hold").
 */
class CustomKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onAnnounce(key: Key)
        fun onKey(key: Key)
        fun onEmojiPanelRequested()
    }

    var listener: Listener? = null

    // ---- TalkBack support ----
    // Exposes each hand-drawn key as a virtual accessibility view so TalkBack can
    // announce it on explore-by-touch and "type" it via TalkBack's own double-tap
    // gesture. See KeyboardAccessibilityHelper for why this is needed.
    private val accessibilityHelper = KeyboardAccessibilityHelper(
        host = this,
        keyProvider = { keyRects },
        onActivate = { key ->
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            listener?.onKey(key)
        }
    )

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        return accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    // The rows currently on screen. Rebuilt by the IME whenever the language,
    // shift state, or layer (letters / symbols / emoji) changes.
    private var rows: List<List<Key>> = emptyList()
    var shiftOn: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val keyRects = mutableListOf<Pair<RectF, Key>>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2B2B")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0E8FF")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }

    fun setRows(newRows: List<List<Key>>) {
        rows = newRows
        pendingKey = null
        requestLayout()
        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    // ---- double-tap / long-press-hold state ----
    // NOTE on the interaction model (revised): the ORIGINAL version required the
    // second tap to land inside the exact same key rect within 400ms. In practice
    // (especially for the low-vision/blind users this keyboard is built for) a
    // finger almost never lands on the identical pixel twice, so nothing ever
    // typed. This is fixed the same way TalkBack itself handles "double-tap to
    // activate": a genuinely fast second tap confirms the last-announced key no
    // matter where on the keyboard it lands. A slower, deliberate second tap on
    // the same key still also works, with a much more generous window.
    private var pendingKey: Key? = null
    private var pendingSetAt = 0L
    private var lastTapUpAt = 0L
    private val clearPendingRunnable = Runnable { pendingKey = null; invalidate() }
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracks whether the *current* finger-down might turn into the confirming tap
    // for the language-switch key, so we can decide long-press-hold (emoji panel)
    // vs a normal quick confirm (cycle language) on lift.
    private var awaitingLangHoldCheck = false
    private val longPressRunnable = Runnable {
        if (awaitingLangHoldCheck) {
            awaitingLangHoldCheck = false
            pendingKey = null
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onEmojiPanelRequested()
        }
    }

    companion object {
        // A real "double tap" gesture -- confirms the pending key from ANYWHERE
        // on the keyboard, to tolerate finger drift between the two taps.
        private const val FAST_CONFIRM_MS = 350L
        // A slower, deliberate re-tap still confirms, but only if it lands back
        // on the exact same key.
        private const val SAME_KEY_CONFIRM_MS = 1500L
        // Extra touch tolerance (px) around each key's drawn rect when hit-testing,
        // so a tap that's a little off the key's edge still registers.
        private const val HIT_SLOP_PX = 24f
        private const val LONG_PRESS_MS = 500L
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowHeight = (width / 9f).coerceAtMost(140f)
        val height = (rowHeight * (rows.size + 1.4f)).toInt() // +1.4 for the bottom row
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        keyRects.clear()
        val width = width.toFloat()
        val rowCount = rows.size + 1 // +1 for space-bar row
        val rowHeight = height / rowCount.toFloat()
        textPaint.textSize = rowHeight * 0.42f

        rows.forEachIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.flexWeight.toDouble() }.toFloat()
            var x = 0f
            val y = rowIndex * rowHeight
            row.forEach { key ->
                val keyWidth = width * (key.flexWeight / totalWeight)
                val rect = RectF(x + 4, y + 4, x + keyWidth - 4, y + rowHeight - 4)
                drawKey(canvas, rect, key)
                keyRects.add(rect to key)
                x += keyWidth
            }
        }

        // Bottom function row: symbols-toggle, language-switch, comma, space, period, backspace/enter
        drawBottomRow(canvas, rowCount, rowHeight)
    }

    private var bottomRowKeys: List<Key> = emptyList()

    fun setBottomRow(keys: List<Key>) {
        bottomRowKeys = keys
        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    private fun drawBottomRow(canvas: Canvas, rowCount: Int, rowHeight: Float) {
        if (bottomRowKeys.isEmpty()) return
        val width = width.toFloat()
        val y = (rowCount - 1) * rowHeight
        val totalWeight = bottomRowKeys.sumOf { it.flexWeight.toDouble() }.toFloat()
        var x = 0f
        bottomRowKeys.forEach { key ->
            val keyWidth = width * (key.flexWeight / totalWeight)
            val rect = RectF(x + 4, y + 4, x + keyWidth - 4, y + rowHeight - 4)
            drawKey(canvas, rect, key)
            keyRects.add(rect to key)
            x += keyWidth
        }
    }

    private fun drawKey(canvas: Canvas, rect: RectF, key: Key) {
        val isPending = key === pendingKey
        canvas.drawRoundRect(rect, 12f, 12f, if (isPending) activeFillPaint else keyFillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, keyPaint)
        val label = labelFor(key)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), textY, textPaint)
    }

    private fun labelFor(key: Key): String = when (key.code) {
        KeyCode.BACKSPACE -> "⌫"
        KeyCode.SPACE -> "␣"
        KeyCode.ENTER -> "⏎"
        KeyCode.SHIFT -> if (shiftOn) "⇪" else "⇧"
        KeyCode.LANGUAGE_SWITCH -> "🌐"
        KeyCode.SYMBOLS_TOGGLE -> "?123"
        KeyCode.LETTERS_TOGGLE -> "ABC"
        KeyCode.MIC -> "🎤"
        KeyCode.EMOJI_BACK -> "⌨"
        else -> key.output(shiftOn)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_UP -> handleUp(event)
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? =
        keyRects.firstOrNull { (rect, _) ->
            x >= rect.left - HIT_SLOP_PX && x <= rect.right + HIT_SLOP_PX &&
                y >= rect.top - HIT_SLOP_PX && y <= rect.bottom + HIT_SLOP_PX
        }?.second

    private fun commit(key: Key) {
        pendingKey = null
        mainHandler.removeCallbacks(clearPendingRunnable)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        listener?.onKey(key)
        lastTapUpAt = 0L
        invalidate()
    }

    private fun handleDown(event: MotionEvent) {
        val now = System.currentTimeMillis()
        // If a language-switch key is currently pending (i.e. it was just
        // announced), this new touch-down might be its confirming tap. Start the
        // long-press timer so we can tell a hold (emoji panel) apart from a quick
        // confirm (cycle language) -- regardless of exactly where this touch is.
        if (pendingKey?.code == KeyCode.LANGUAGE_SWITCH && (now - pendingSetAt) <= SAME_KEY_CONFIRM_MS) {
            awaitingLangHoldCheck = true
            mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
    }

    private fun handleUp(event: MotionEvent) {
        val now = System.currentTimeMillis()

        if (awaitingLangHoldCheck) {
            // Released before the long-press fired -> quick confirm, not a hold.
            mainHandler.removeCallbacks(longPressRunnable)
            awaitingLangHoldCheck = false
            val key = pendingKey ?: return
            commit(key)
            return
        }

        val tappedKey = keyAt(event.x, event.y)

        // A genuinely fast double-tap, anywhere on the keyboard, confirms whatever
        // was last announced. This is what makes typing actually work for a finger
        // that doesn't land back on the exact same spot.
        if (pendingKey != null && (now - lastTapUpAt) <= FAST_CONFIRM_MS) {
            commit(pendingKey!!)
            return
        }

        // A slower, deliberate second tap that lands back on the same key also confirms it.
        if (tappedKey != null && tappedKey === pendingKey) {
            commit(tappedKey)
            return
        }

        // Otherwise this is a fresh "explore" tap -- announce it and wait for a confirm.
        if (tappedKey != null) {
            pendingKey = tappedKey
            pendingSetAt = now
            listener?.onAnnounce(tappedKey)
            mainHandler.removeCallbacks(clearPendingRunnable)
            mainHandler.postDelayed(clearPendingRunnable, SAME_KEY_CONFIRM_MS)
        }
        lastTapUpAt = now
        invalidate()
    }
}
