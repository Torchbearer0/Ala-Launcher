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

/** How far a drag has to travel before it is moving the widget rather than pressing it. */
private const val MOVE_SLOP_PX = 12f

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
     * Set only while the page is arranging this widget, so a drag moves it rather than the
     * widget. Null the rest of the time, and the widget's own scrolling is left alone.
     *
     * [dragMove] is given the finger's position in the window, not a delta - see handleDrag.
     */
    var dragStart: ((grabFraction: Float) -> Unit)? = null
    var dragMove: ((rootY: Float) -> Unit)? = null
    var dragEnd: (() -> Unit)? = null

    private var moving = false
    private var locationInWindow = IntArray(2)

    private var longPressFired = false

    /** Where the current gesture began, so its direction can be judged as it moves. */
    private var downX = 0f
    private var downY = 0f

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

    /**
     * Reads the arranging drag here, not in [onInterceptTouchEvent].
     *
     * A ViewGroup only consults onInterceptTouchEvent for the press and while it has no touch
     * target; once the widget inside has taken the gesture, the moves go straight to it. So a
     * drag read from there never ran at all - the press landed, the long press fired, and the
     * widget could be dragged by nobody. dispatchTouchEvent is called for every event that
     * reaches this view whichever child ends up handling it, so this is the only place a drag
     * can be seen whole.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dragMove != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moving = false
                    downX = ev.x
                    downY = ev.y
                    // Hold the gesture off the ancestors from the press, not from the slop.
                    // The page is a scrolling column, and a drag that is still inside its own
                    // touch slop has not yet told handleDrag it is a drag - so waiting for that
                    // meant the column saw the slop cross first, took the gesture, and the
                    // widget got exactly one move before it was being carried off-screen.
                    // While the page is arranging this entry it is not scrolling.
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> handleDrag(ev)

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (moving) {
                        moving = false
                        dragEnd?.invoke()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                claimed = false
                moving = false
                downX = ev.x
                downY = ev.y
                canScroll = hasScrollableContent()
            }

            MotionEvent.ACTION_MOVE -> {
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
        return longPressFired || moving
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
            moving = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    /**
     * Reports an arranging drag, in whichever pass this event was delivered. True once the drag
     * is ours, which is the signal to intercept.
     *
     * Reports where the finger is in window coordinates, not how far it has travelled. The page
     * arranges against where the finger actually is: with a travel delta it can only ever step
     * over a neighbour on some fixed count, which feels like a shuffle rather than a lift. An
     * absolute position can be compared against the other entries' own positions, so they move
     * out of the way continuously as the finger passes them.
     */
    private fun handleDrag(ev: MotionEvent): Boolean {
        if (dragMove == null) return false
        val total = ev.y - downY
        if (!moving) {
            if (abs(total) <= MOVE_SLOP_PX) return false
            moving = true
            // Where this view sits in the window, read once. A view-tree walk is not a
            // per-event cost, and the finger's position within it plus this is where it is
            // on screen - which is what has to be compared against the other entries.
            locationInWindow = IntArray(2).also { getLocationInWindow(it) }
            // Where in its own height the drag was taken hold, as a fraction. Passed on so the
            // lifted entry stays under the finger where it was grabbed rather than jumping so
            // its middle is under the finger.
            val h = height
            dragStart?.invoke(if (h > 0) (ev.y / h).coerceIn(0f, 1f) else 0.5f)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        dragMove?.invoke(locationInWindow[1] + ev.y)
        return true
    }
}