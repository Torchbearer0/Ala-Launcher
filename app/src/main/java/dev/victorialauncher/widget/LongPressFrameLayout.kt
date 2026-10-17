// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** How much more vertical than sideways a drag must be before the widget takes it for a scroll. */
private const val DIRECTION_MARGIN = 1.25f

/**
 * Wraps an embedded AppWidgetHostView so a genuine long-press (finger held still) opens our
 * edit menu, while an ordinary tap or drag still reaches the widget underneath untouched.
 *
 * A plain Compose pointerInput long-press detector can't do this: once it starts tracking a
 * gesture it owns the whole touch stream, so the widget's own buttons (play/pause, a weather
 * tap-to-open) would stop working. This mirrors how scrollable containers arbitrate gestures
 * with their children: don't intercept on ACTION_DOWN, keep watching via onInterceptTouchEvent,
 * and only steal the stream once our own long-press timer actually fires.
 */
class LongPressFrameLayout(context: Context) : FrameLayout(context) {

    /** x/y are the press position in this view's local pixel coordinates. */
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null

    /**
     * Fired once when a press that began here is dragged to the right.
     *
     * Here rather than in a Compose handler around the widget because a hosted widget is an
     * Android view that handles its own touch stream: a pointerInput waiting behind it for an
     * unconsumed drag is never given one. This class already sits between the widget and the
     * home screen and already decides which gestures it keeps, so the page on the left is read
     * here alongside the long press and the vertical scroll.
     */
    var onSwipeRight: (() -> Unit)? = null

    private var longPressFired = false

    /** How far this gesture has been to the right, and whether it has already been reported. */
    private var swipeRightPx = 0f
    private var swipeRightFired = false

    /** Where the current gesture began, so its direction can be judged as it moves. */
    private var downX = 0f
    private var downY = 0f

    /** The furthest right this gesture has reached, so the test is about the whole drag. */
    private var furthest = 0f

    /** Set once the widget has taken this gesture, so the claim is made once and not per move. */
    private var claimed = false

    /** Read at the press rather than per move: a view-tree walk is not a per-event cost. */
    private var canScroll = false

    /** Half a touch slop: enough to read a direction, short enough to beat the home screen. */
    private val decisionSlop get() = touchSlop / 2
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                longPressFired = true
                onLongPress?.invoke(e.x, e.y)
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                claimed = false
                swipeRightPx = 0f
                swipeRightFired = false
                downX = ev.x
                downY = ev.y
                canScroll = hasScrollableContent()
            }

            MotionEvent.ACTION_MOVE -> {
                // Read every move until it fires, rather than settling on the first sample that
                // looks rightwards: a thumb that comes down across and then carries on to the
                // right should still count, and twelve pixels of noise should not decide it.
                if (!swipeRightFired) {
                    val dx = ev.x - downX
                    if (dx > furthest) {
                        furthest = dx
                        // Read with the same margin the vertical claim uses, so a drag that is
                        // really a scroll does not get taken sideways on a wobble.
                        if (dx > touchSlop && dx > abs(ev.y - downY) * DIRECTION_MARGIN) {
                            swipeRightFired = true
                            onSwipeRight?.invoke()
                        }
                    }
                }

                if (!claimed && canScroll) {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                // Claimed early, and only for up and down.
                //
                // Claiming on the press instead took sideways with it, and handing it back
                // once the drag turned out to be sideways does not give the pager its chance
                // again — so a widget that scrolls could never be swiped past. Waiting for a
                // full touch slop was the other way round: the home screen decides at exactly
                // that distance, and whichever ran first won, which is how a list inside a
                // widget lost its scroll to the notification shade.
                //
                // Half a slop is far enough to tell a direction and short enough to answer
                // before the home screen does. Sideways is never claimed, so the pager keeps
                // it, and a widget with nothing to scroll is never claimed for either.
                //
                // Nothing is written off, though. This used to settle on the first sample to
                // pass that distance and stand by it for the rest of the gesture, including
                // when it settled on sideways — and twelve pixels in, a thumb that has just
                // come back down to carry on scrolling is mostly noise. A swipe that started a
                // little across and then went down had already given the drag away, which is
                // what made it "hit or miss whether it's recognized on the widget or the app
                // scroller". Only the claim is final now; until one is made every move is
                // another chance to read the direction.
                //
                // And read with a margin rather than by a hair, or the same wobble in the
                // other direction claims a sideways swipe for a scroll before the pager can
                // have it — which is the stack needing "the perfect angle".
                if (dy > decisionSlop && dy > dx * DIRECTION_MARGIN) {
                    claimed = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                }
            }
        }
        gestureDetector.onTouchEvent(ev)
        if (longPressFired) {
            // Our own menu is taking over, so the hold on the parent is no longer wanted.
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return longPressFired
    }

    /**
     * Whether anything inside the widget can actually be scrolled right now.
     *
     * Asked of the view tree rather than the widget's declaration: a list that is too short to
     * scroll answers no, which is the right answer — there is nothing to take the gesture for.
     */
    private fun hasScrollableContent(): Boolean {
        fun scrollable(view: View): Boolean {
            if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return true
            if (view !is ViewGroup) return false
            for (i in 0 until view.childCount) {
                if (scrollable(view.getChildAt(i))) return true
            }
            return false
        }
        for (i in 0 until childCount) {
            if (scrollable(getChildAt(i))) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            longPressFired = false
            claimed = false
            swipeRightPx = 0f
            swipeRightFired = false
            furthest = 0f
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}