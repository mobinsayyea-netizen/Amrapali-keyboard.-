package com.mobeen.amrapali

import android.graphics.Rect
import android.graphics.RectF
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Makes [CustomKeyboardView]'s hand-drawn keys visible to TalkBack.
 *
 * Without this, TalkBack intercepts every touch for its own "explore by touch"
 * gesture system-wide, so the raw onTouchEvent()-based tap logic in
 * CustomKeyboardView never even sees the touches while TalkBack is on -- nothing
 * gets announced, nothing gets typed.
 *
 * With this helper registered as the view's accessibility delegate, each key
 * becomes a "virtual view" that TalkBack can:
 *  - move accessibility focus onto as the user explores by touch (which is what
 *    makes TalkBack SPEAK the key -- via [onPopulateNodeForVirtualView]'s
 *    contentDescription),
 *  - and "click" (type) via TalkBack's own double-tap gesture, which arrives here
 *    as [onPerformActionForVirtualView] with ACTION_CLICK.
 *
 * This runs independently of -- not instead of -- the plain-touch double-tap
 * logic already in CustomKeyboardView: that logic is for sighted/low-vision use
 * *without* TalkBack running. When TalkBack's touch exploration is active, Android
 * routes touches as hover events through this helper instead, so the two paths
 * don't fight each other.
 */
class KeyboardAccessibilityHelper(
    private val host: CustomKeyboardView,
    private val keyProvider: () -> List<Pair<RectF, Key>>,
    private val onActivate: (Key) -> Unit
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
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
            val key = keys.getOrNull(virtualViewId)?.second ?: return false
            onActivate(key)
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
        return false
    }
}
