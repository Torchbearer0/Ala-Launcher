// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Whether a translucent menu is open anywhere on screen.
 *
 * A menu is drawn nearly clear and takes its legibility from the layer behind it being blurred,
 * so opening one is not on its own enough: something has to blur. That was wired per screen and
 * per row, which meant only the home screen's app rows blurred and nothing else did — the clean
 * area, the folders, the widget slot and every row in the drawer all opened a menu over a sharp
 * background and were hard to read.
 *
 * Counted here instead of threaded down from each caller, because [TouchAnchoredMenu] is already
 * the one place every menu in the launcher is built, and the layer that has to blur sits above
 * all of them. The alternative — a callback on each screen, then a parameter on each of the seven
 * menus inside it — puts the same decision in seven places, and the one that is forgotten is
 * invisible until someone long-presses that particular thing.
 *
 * A count rather than a flag because the menus are not exclusive in the tree: a folder's own menu
 * and a member row's can be open in the same frame, and closing one while the next opens would
 * otherwise lift the blur in between and flash the background.
 *
 * Paired acquire and release, with the release in `onDispose` rather than on the flag going false.
 * A menu that leaves composition while open — the row scrolled off, the screen replaced — never
 * gets to report its own close, and a flag stuck true is a home screen that stays blurred for the
 * rest of the session.
 */
object MenuBlur {

    private var open by mutableIntStateOf(0)

    val anyOpen: Boolean get() = open > 0

    fun acquire() {
        open++
    }

    fun release() {
        if (open > 0) open--
    }
}