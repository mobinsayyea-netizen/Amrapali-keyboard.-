package com.mobeen.amrapali

import android.graphics.Rect
import android.graphics.RectF
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Makes [CustomKeyboardView]'s hand-drawn keys visible to TalkBack, and is the
 * ONLY thing that runs while TalkBack's touch exploration is active (see the
 * guard at the top of [CustomKeyboardView.onTouchEvent]).
 *
 * With this helper registered as the view's accessibility delegate, each key
 * becomes a "virtual view" that TalkBack can:
 *  - move accessibility focus onto as the user explores by touch, moving from key
 *    to key -- this is what makes TalkBack itself SPEAK the key, via
 *    [onPopulateNodeForVirtualView]'s contentDescription. The app does none of
 *    its own text-to-speech for this; the announcement is entirely TalkBack's.
 *  - "click" (type) via TalkBack's own double-tap gesture, delivered here as
 *    [onPerformActionForVirtualView] with ACTION_CLICK,
 *  - "long click" (open the emoji panel, language-switch key only) via
 *    TalkBack's double-tap-and-hold gesture, delivered as ACTION_LONG_CLICK.
 *
 * [onHoverEnter] fires a light haptic tick every time exploration lands on a new
 * key, independent of TalkBack's own audio feedback -- matching how Samsung
 * Keyboard feels under TalkBack.
 */
class KeyboardAccessibilityHelper(
    private val host: CustomKeyboardView,
    private val keyProvider: () -> List<Pair<RectF, Key>>,
    private val onActivate: (Key) -> Unit,
    private val onLongActivate: (Key) -> Unit,
    private val onHoverEnter: () -> Unit
) : ExploreByTouchHelper(host) {

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val keys = keyProvider()
        keys.forEachIndexed { index, (rect, _) ->
            if (rect.contains(x, y)) return index
        }
        return HOST_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val keys = keyProvider()
        for (i in keys.indices) {
            virtualViewIds.add(i)
        }
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val keys = keyProvider()
        if (virtualViewId < 0 || virtualViewId >= keys.size) {
            // Defensive default so we never hand back a node with no bounds set.
            node.contentDescription = ""
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            return
        }
        val (rect, key) = keys[virtualViewId]
        node.contentDescription = key.announcement(host.shiftOn)
        node.className = "android.widget.Button"
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        if (key.code == KeyCode.LANGUAGE_SWITCH) {
            node.isLongClickable = true
            node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK)
        }
        val bounds = Rect()
        rect.round(bounds)
        node.setBoundsInParent(bounds)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: android.os.Bundle?
    ): Boolean {
        val keys = keyProvider()
        val key = keys.getOrNull(virtualViewId)?.second ?: return false
        return when (action) {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK -> {
                onActivate(key)
                sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                true
            }
            android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                onLongActivate(key)
                sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
                true
            }
            else -> false
        }
    }

    override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
        super.onPopulateEventForVirtualView(virtualViewId, event)
        val keys = keyProvider()
        val key = keys.getOrNull(virtualViewId)?.second
        // contentDescription must always be set, even for the defensive
        // out-of-range case, or the accessibility framework throws.
        event.contentDescription = key?.announcement(host.shiftOn) ?: ""
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
            onHoverEnter()
        }
    }
}
