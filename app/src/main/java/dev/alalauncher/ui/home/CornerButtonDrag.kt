// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.home

/**
 * Tracks one press on the corner button, so the swipe can be told from the tap and the hold.
 *
 * Free of Android types, and the whole of it, so the decision can be tested on the JVM. This is
 * three actions competing for one small target, and a mistake here is invisible until someone
 * taps the wrong thing on a real phone.
 *
 * The rule that matters is [onDown]. An earlier version tracked "a drag happened" and left it
 * set after a swipe, expecting the click to clear it — but a drag that consumed its movement
 * produces no click at all, because the tap recogniser cancels it. Nothing cleared the flag and
 * the next genuine tap was swallowed, so the button read as dead after every swipe. It is
 * reset where it is guaranteed to run instead: as each press begins.
 *
 * Only the swipe is decided here. The tap and the hold are left to the clickable modifier,
 * which is what carries the accessibility semantics a launcher cannot do without; this says
 * whether the release belongs to the tap, and that is all it claims.
 */
class CornerButtonDrag(private val swipeSlopPx: Float) {

    private var travelled = 0f

    /** Everything starts from zero here, which is what keeps a flag from outliving its gesture. */
    fun onDown() {
        travelled = 0f
    }

    /**
     * Feeds one frame of vertical movement, positive downward.
     *
     * @return whether this frame should be consumed as part of the swipe. Consuming upward
     *   travel is what stops the drag from also reaching the gesture that pulls the app list
     *   open; downward is left alone so that gesture still gets it.
     */
    fun onMove(dy: Float): Boolean {
        travelled += dy
        return isSwipe()
    }

    /**
     * Whether the press travelled far enough up to be the swipe.
     *
     * Strictly past the slop, not at it: at exactly the slop the finger has not yet been
     * accepted as a drag by the platform, and treating it as one would fire the swipe a frame
     * before the gesture is really distinguishable from a tap.
     */
    fun isSwipe(): Boolean = travelled < -swipeSlopPx

    /**
     * Whether the press moved at all, in either direction.
     *
     * Not the same question as [isSwipe]. A drag downward is not the swipe, but the finger
     * still travelled, so the release is not a tap either and must not launch anything.
     */
    fun moved(): Boolean = travelled != 0f
}