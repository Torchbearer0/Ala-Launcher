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
     * Everything about the long press and the drag is read here, not in
     * [onInterceptTouchEvent].
     *
     * A ViewGroup only consults onInterceptTouchEvent for the press and while it has no touch
     * target - once the widget inside has taken the gesture the moves go straight to it. Two
     * things followed from that, and both were felt:
     *
     * A drag read there never ran at all, because the press landed, the long press fired, and
     * every move had already gone to the widget.
     *
     * Worse, the flag saying the long press had fired was only ever cleared in onTouchEvent -
     * which never runs, for the same reason: without an intercept there is nothing for this
     * view to receive the UP. So the flag stayed true, the next press returned true from
     * onInterceptTouchEvent and was swallowed whole, and the widget's own buttons went dead
     * until the next gesture happened to clear it. That is what a long press that is
     * "sometimes dropped" looks like from the outside.
     *
     * dispatchTouchEvent is called for every event that reaches this view whichever child ends
     * up handling it, so the whole gesture can be read from one place - and clearing the flag
     * here is what makes it reliable rather than incidental.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        /**
         * Whether the UP or CANCEL itself has to be swallowed, decided before the state is
         * cleared below.
         *
         * This has to be captured first. Clearing [longPressFired] and [moving] and *then*
         * asking whether to consume meant the answer was always no on the one event that
         * mattered: the flag was already false by the time the check ran, so the UP fell through
         * to the widget, its PendingIntent fired, and a long press on a news widget opened the
         * article instead of arranging the widget. The state must describe the gesture that is
         * ending while the decision about it is still being made.
         */
        var swallowFinalEvent = false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                claimed = false
                moving = false
                downX = ev.x
                downY = ev.y
                canScroll = hasScrollableContent()
                // Hold the gesture off the ancestors from the press while the page is arranging
                // this entry. The page is a scrolling column, and a drag still inside its own
                // touch slop has not yet told handleDrag it is a drag - so waiting for that meant
                // the column saw the slop cross first, took the gesture, and the widget got
                // exactly one move before it was being carried off-screen.
                if (dragMove != null) parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragMove != null) handleDrag(ev)
                if (!longPressFired && !claimed && canScroll) claimForScroll(ev)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swallowFinalEvent = longPressFired || moving
                if (moving) {
                    moving = false
                    dragEnd?.invoke()
                }
                // Unconditional. Whatever happened, this gesture is over, and anything that
                // describes it has to be cleared here or it describes the next one as well.
                longPressFired = false
                claimed = false
                moving = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        gestureDetector.onTouchEvent(ev)

        // Once the long press has fired or a drag has begun, the rest of the gesture is ours.
        // Consuming it here is what stops the widget underneath also acting on the press - the
        // UP never reaches it, so the long press is an edit and not also a tap.
        if (swallowFinalEvent || longPressFired || moving) return true

        return super.dispatchTouchEvent(ev)
    }

    /**
     * Claims a mostly-vertical drag for the widget's own scroll, once and early.
     *
     * Claimed at half a slop, because the home screen decides at exactly a slop and whichever
     * ran first won. Sideways is never claimed, so a swipe past the widget still belongs to
     * whatever is behind. Nothing is written off: until a claim is made every move is another
     * chance to read the direction, which is what stopped a drag that started a little across
     * and then went down being given away.
     */
    private fun claimForScroll(ev: MotionEvent) {
        val dx = abs(ev.x - downX)
        val dy = abs(ev.y - downY)
        if (dy > decisionSlop && dy > dx * DIRECTION_MARGIN) {
            claimed = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    /**
     * Only ever true for the press itself.
     *
     * Everything else - the long press, the scroll claim, the arranging drag - is read in
     * [dispatchTouchEvent], which sees the whole gesture. All that has to happen here is the
     * one thing that cannot be done from there: taking the stream *away* from the widget, and
     * only possible on a gesture this view was handed from the start.
     *
     * The gesture detector is deliberately NOT fed from here. dispatchTouchEvent feeds it
     * before dispatching, so an event that reaches both callbacks arrives twice - and a second
     * press on the detector starts a second gesture, which restarts the long-press timer and
     * means the press never matures.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = longPressFired || moving

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

    /**
     * Nothing to do but claim the stream.
     *
     * No gesture detector here either, for the same reason as in [onInterceptTouchEvent] -
     * dispatchTouchEvent has already seen the event, and a detector fed twice never matures a
     * long press.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean = true

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