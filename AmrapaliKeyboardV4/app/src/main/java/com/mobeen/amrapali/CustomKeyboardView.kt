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
import android.view.accessibility.AccessibilityManager
import androidx.core.view.ViewCompat

/**
 * Draws the keyboard and turns touches into key presses.
 *
 * Interaction model (V4 — split by whether TalkBack's touch exploration is running):
 *
 *  - TalkBack OFF: this is a completely ordinary keyboard. A single tap on a key
 *    types it immediately. Nothing is announced, nothing is spoken, there is no
 *    double-tap step. The only special case is the language-switch key, where a
 *    normal tap cycles the language but a long-press opens the emoji panel —
 *    exactly like a normal keyboard's "hold a key for more options" gesture.
 *
 *  - TalkBack ON (touch exploration active): this view's own touch handling gets
 *    out of the way entirely (see onTouchEvent) and [KeyboardAccessibilityHelper]
 *    takes over completely via Android's standard accessibility APIs:
 *      - moving a finger from key to key (explore-by-touch) moves accessibility
 *        focus, which is what makes TalkBack itself SPEAK the key -- the app does
 *        not do any of its own text-to-speech for this,
 *      - TalkBack's own double-tap gesture on the focused key performs the type,
 *      - a light haptic tick fires on every hover-enter (finger arriving on a new
 *        key), and a stronger one fires when a key is actually activated --
 *        matching the feel of Samsung Keyboard under TalkBack.
 *
 *  These two modes never run at the same time, which is what fixes the earlier bug:
 *  previously the plain-touch state machine below kept running even while TalkBack
 *  was exploring, and the synthetic touch TalkBack's double-tap gesture generates
 *  would land in both systems at once and corrupt each other's state.
 */
class CustomKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onKey(key: Key)
        fun onEmojiPanelRequested()
    }

    var listener: Listener? = null

    // ---- TalkBack support ----
    // Exposes each hand-drawn key as a virtual accessibility view so TalkBack can
    // announce it on explore-by-touch and "type" it via TalkBack's own double-tap
    // gesture, and long-press it (language-switch key only) to open the emoji
    // panel. See KeyboardAccessibilityHelper for details.
    private val accessibilityHelper = KeyboardAccessibilityHelper(
        host = this,
        keyProvider = { keyRects },
        onActivate = { key ->
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            listener?.onKey(key)
        },
        onLongActivate = { key ->
            if (key.code == KeyCode.LANGUAGE_SWITCH) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener?.onEmojiPanelRequested()
            }
        },
        onHoverEnter = {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    )

    private val accessibilityManager: AccessibilityManager? by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    /** True while TalkBack (or another screen reader) is actively exploring by touch. */
    private fun isTouchExplorationActive(): Boolean =
        accessibilityManager?.let { it.isEnabled && it.isTouchExplorationEnabled } == true

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
        downKey = null
        requestLayout()
        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    // ---- plain-touch (non-TalkBack) state ----
    // Ordinary "press this key down, release over it, it types" tracking, plus a
    // long-press timer used only for the language-switch key's hold-for-emoji
    // gesture. This whole block is skipped entirely while TalkBack is exploring
    // (see onTouchEvent / isTouchExplorationActive).
    private var downKey: Key? = null
    private var awaitingLangHoldCheck = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        awaitingLangHoldCheck = false
        downKey = null
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onEmojiPanelRequested()
        invalidate()
    }

    companion object {
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
        val isPressed = key === downKey
        canvas.drawRoundRect(rect, 12f, 12f, if (isPressed) activeFillPaint else keyFillPaint)
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
        // While TalkBack is exploring by touch, the system already delivers real
        // finger movement to us as hover events (-> accessibilityHelper, via
        // dispatchHoverEvent) and turns TalkBack's own double-tap gesture into an
        // accessibility click on the focused virtual view (-> onActivate below).
        // Any raw MotionEvent that still reaches onTouchEvent while exploring is
        // part of that same gesture; running our own press/release logic on it as
        // well would race the accessibility path and break both. So: hands off.
        if (isTouchExplorationActive()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? =
        keyRects.firstOrNull { (rect, _) ->
            x >= rect.left - HIT_SLOP_PX && x <= rect.right + HIT_SLOP_PX &&
                y >= rect.top - HIT_SLOP_PX && y <= rect.bottom + HIT_SLOP_PX
        }?.second

    private fun commit(key: Key) {
        downKey = null
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        listener?.onKey(key)
        invalidate()
    }

    private fun handleDown(event: MotionEvent) {
        val key = keyAt(event.x, event.y)
        downKey = key
        invalidate()
        // Ordinary keyboard "hold this key for more options" gesture, currently
        // only wired up for the language-switch key -> emoji panel.
        if (key?.code == KeyCode.LANGUAGE_SWITCH) {
            awaitingLangHoldCheck = true
            mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
    }

    private fun handleUp(event: MotionEvent) {
        if (awaitingLangHoldCheck) {
            mainHandler.removeCallbacks(longPressRunnable)
            awaitingLangHoldCheck = false
        }
        val key = downKey ?: keyAt(event.x, event.y)
        downKey = null
        invalidate()
        if (key != null) commit(key)
    }

    private fun handleCancel() {
        mainHandler.removeCallbacks(longPressRunnable)
        awaitingLangHoldCheck = false
        downKey = null
        invalidate()
    }
}
