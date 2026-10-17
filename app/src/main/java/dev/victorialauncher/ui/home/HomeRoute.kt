// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.app.SearchManager
import android.content.Intent
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.victorialauncher.TypedKey
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.restoreConcealed
import dev.victorialauncher.data.AzStripVisibility
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.FavoritesSource
import dev.victorialauncher.data.ShortcutSwipe
import dev.victorialauncher.data.HomeAlignment
import dev.victorialauncher.data.IconSide
import dev.victorialauncher.data.Folder
import dev.victorialauncher.data.HomePaddings
import dev.victorialauncher.data.QuickLaunchSlot
import dev.victorialauncher.data.PaddingSlot
import dev.victorialauncher.data.folderToken
import dev.victorialauncher.media.NowPlayingBus
import dev.victorialauncher.media.NotificationBus
import dev.victorialauncher.media.NotificationActionEntry
import dev.victorialauncher.media.NowPlayingListenerService
import dev.victorialauncher.ui.notifications.NotificationPanel
import dev.victorialauncher.ui.common.AppPanel
import dev.victorialauncher.media.isListenerEnabled
import dev.victorialauncher.service.SystemUi
import dev.victorialauncher.ui.applist.AppListModel
import dev.victorialauncher.ui.applist.AppListScreen
import dev.victorialauncher.ui.applist.BandEditOverlay
import android.appwidget.AppWidgetProviderInfo
import dev.victorialauncher.ui.applist.EdgeScrubber
import dev.victorialauncher.ui.applist.EdgeTouchZone
import dev.victorialauncher.ui.applist.ScrubBand
import dev.victorialauncher.ui.applist.ScrubberGeometry
import dev.victorialauncher.ui.applist.ScrubState
import dev.victorialauncher.ui.applist.buildAppListModel
import dev.victorialauncher.ui.common.FolderPickerDialog
import dev.victorialauncher.ui.common.newestPreviewsByPackage
import dev.victorialauncher.widget.WidgetSlotActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Immutable
import android.content.res.Configuration
import android.net.Uri
import dev.victorialauncher.R

/** Width of the invisible strip at each screen edge that opens the app list. */
/** Android only honors this much gesture exclusion per side, so spend it on the strip. */
private const val GESTURE_EXCLUSION_CAP_DP = 200

/** How long a launch is given to take us off screen before the overlay closes itself. */
private const val LAUNCH_CLOSE_TIMEOUT_MS = 2000L

/** Long enough to read as a settle, short enough not to stand between you and the icons. */
/** Room each letter wants on the strip before it stops being something anyone can hit. */
private val MIN_LETTER_HEIGHT = 15.dp

private const val EDGE_GAP_DP = 5f
private const val OVERLAY_BLUR_MS = 220
const val HOME_FADE_MS = 220

/**
 * How far a swipe up carries the app list in. The same distance a pull collapses it over, so
 * opening is literally the closing animation run backwards.
 */
private val SWIPE_OPEN_DISTANCE = 150.dp

/**
 * The home destination: the home screen itself, the app-list overlay layered over it, and the
 * edge zones that move between them.
 *
 * The overlay is kept composed and measured even while hidden — just never placed — because
 * building the whole list from scratch on every open is what made it take a beat to appear.
 */
@Composable
fun HomeRoute(
    app: VictoriaApp,
    homeIntentTick: Int,
    /**
     * The app a corner button opens, or null for no button.
     *
     * Passed beside [settings] rather than inside it: HomeSettings is at the size a data
     * class constructor will still resolve — one more field and every argument to it fails
     * to typecheck against itself — and this is a resolved row rather than a stored value.
     */
    cornerButtonApp: AppInfo?,
    /** True when the corner opens the search instead of launching an app. */
    cornerButtonIsSearch: Boolean,
    /**
     * What the corner button opens when held, and when swiped up. Resolved to rows here rather
     * than passed as keys because the lookup needs the app map, which lives one layer up; a null
     * either way leaves that gesture unbound.
     */
    cornerLongPressApp: AppInfo?,
    cornerLongPressIsSearch: Boolean,
    cornerSwipeUpApp: AppInfo?,
    cornerSwipeUpIsSearch: Boolean,
    /** Whether opening the app list puts the cursor in its search box. */
    autoKeyboard: Boolean,
    /** Whether the last letters reach the same line as the first. */
    lastLetterToLine: Boolean,
    typedToSearch: List<TypedKey>,
    onTypedToSearchHandled: (List<TypedKey>) -> Unit,
    settings: HomeSettings,
    homePaddings: HomePaddings,
    favorites: List<FavoriteEntry>,
    appsByKey: Map<String, AppInfo>,
    folders: List<Folder>,
    favoriteKeys: List<String>,
    hiddenApps: Set<String>,
    nameOverrides: Map<String, String>,
    widgetIds: List<Int>,
    widgetPosition: Int,
    widgetHeightDp: Int,
    widgetActions: WidgetSlotActions,
    launchCounts: Map<String, Int>,
    showWelcome: Boolean,
    onWelcomeDismissed: () -> Unit,
    scrubBandFractions: Pair<Float, Float>?,
    onSetScrubBand: (Float, Float) -> Unit,
    onClearScrubBand: () -> Unit,
    onPeekStatusBar: () -> Unit,
    onAppListVisibleChange: (Boolean) -> Unit,
    /** Locks an open private space or asks for a locked one to be opened, off this thread. */
    onTogglePrivateSpace: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    // Read here because the models are built off the main thread, where a string resource
    // cannot be asked for.
    val privateSectionLabel = stringResource(R.string.applist_private_space_section)
        .takeIf { settings.privateSpaceSection }
    val recentSectionArg = if (settings.recentSection) {
        stringResource(R.string.applist_recent_section) to settings.recentSectionCount
    } else {
        null
    }
    // Asked once: whether anything on this phone handles a web search at all.
    val webSearchAvailable = remember(context) {
        runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_WEB_SEARCH), 0) != null
        }.getOrDefault(false)
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Hidden apps are left out of the model the list draws from, so reaching them by name
    // needs a model of its own. Only built when the setting asks for it, and it falls back to
    // the list's own model otherwise, so nothing is grouped and sorted twice for nothing.
    val includeHiddenInSearch = settings.appListSearchHidden && hiddenApps.isNotEmpty()

    // Read here rather than beside the other settings because the model below is built in a
    // coroutine that has to see it.
    val foldersInAppList by app.prefs.foldersInAppList.collectAsState(initial = false)

    // Grouping, sorting and flattening every installed app is too much to do in composition,
    // and it re-runs whenever a name override changes.
    val listModel by produceState(
        initialValue = AppListModel(emptyList(), emptyList()),
        appsByKey,
        hiddenApps,
        nameOverrides,
        // Empty unless the sort is on, so an ordinary launch doesn't rebuild the whole list.
        if (settings.sortByUsage) launchCounts else emptyMap(),
        settings.showListHeaders,
        // Both of these, or the model is built once with whatever they were at launch and never
        // rebuilt: a folder created afterwards, or the setting turned on later, would change
        // nothing until the process restarted.
        folders,
        foldersInAppList,
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = withContext(Dispatchers.Default) {
            buildAppListModel(
                apps,
                hiddenApps,
                { nameOverrides[it.key] ?: it.label },
                counts,
                englishName = { app.appRepository.englishLabel(it) },
                showHeaders = settings.showListHeaders,
                renamed = { it.key in nameOverrides },
                privateSectionLabel = privateSectionLabel,
                recentSection = recentSectionArg,
                folders = folders,
                showFolders = foldersInAppList,
            )
        }
    }

    val hiddenSearchModel by produceState<AppListModel?>(
        initialValue = null,
        appsByKey,
        nameOverrides,
        includeHiddenInSearch,
        if (settings.sortByUsage) launchCounts else emptyMap(),
        settings.showListHeaders,
    ) {
        val apps = appsByKey.values.toList()
        val counts = if (settings.sortByUsage) launchCounts else emptyMap()
        value = if (!includeHiddenInSearch) {
            null
        } else {
            withContext(Dispatchers.Default) {
                buildAppListModel(
                    apps,
                    emptySet(),
                    { nameOverrides[it.key] ?: it.label },
                    counts,
                    englishName = { app.appRepository.englishLabel(it) },
                    showHeaders = settings.showListHeaders,
                    renamed = { it.key in nameOverrides },
                    privateSectionLabel = privateSectionLabel,
                    recentSection = recentSectionArg,
                )
            }
        }
    }
    val searchModel = hiddenSearchModel ?: listModel

    var appListVisible by remember { mutableStateOf(false) }
    var notificationPanelVisible by remember { mutableStateOf(false) }
    // Which app's panel the swipe opened, or null. Held by the destination because the strip
    // and the edge zones are drawn here, after HomeScreen, and would cover a panel drawn there.
    var panelFor by remember { mutableStateOf<Triple<AppInfo, Dp, Dp>?>(null) }
    var homeMenuOpen by remember { mutableStateOf(false) }
    val notificationListOnPull by app.prefs.notificationListOnPull.collectAsState(initial = false)
    val notifications by NotificationBus.notifications.collectAsState()

    // Keyed by package because a notification names a package, not an activity, and several
    // activities of one app would otherwise fight over which of them names the group. The
    // personal profile wins where both profiles have the same app, because the panel is
    // showing the phone's own notifications first.
    val appsByPackage = remember(appsByKey) {
        val byPackage = HashMap<String, AppInfo>()
        appsByKey.values.forEach { info ->
            if (info.kind == EntryKind.APP && !info.work) byPackage[info.componentName.packageName] = info
        }
        appsByKey.values.forEach { info ->
            if (info.kind == EntryKind.APP) byPackage.putIfAbsent(info.componentName.packageName, info)
        }
        byPackage
    }
    val scrub = remember { ScrubState() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var favBand by remember { mutableStateOf<ScrubBand?>(null) }
    // Enough height for every letter to be worth aiming at. Counted per letter rather than set
    // as one number, since an alphabet with accents in it has more letters to find room for.
    val minBandHeightPx = with(LocalDensity.current) {
        (listModel.letters.size * MIN_LETTER_HEIGHT.toPx())
    }
    var homeEditMode by remember { mutableStateOf(false) }
    var folderPickerFor by remember { mutableStateOf<AppInfo?>(null) }

    val nowPlaying by NowPlayingBus.state.collectAsState()
    val listenerGranted = remember(homeIntentTick) { isListenerEnabled(context) }
    // Don't reserve the block (or its padding) unless there is something to render:
    // no live session means the whole thing collapses, padding included.
    val nowPlayingHasContent = settings.nowPlayingEnabled && (!listenerGranted || nowPlaying != null)
    // While the band is being edited the live value wins; otherwise a range the user set by
    // hand wins over the measured favorites, which is what makes it stop following them.
    var bandEditMode by remember { mutableStateOf(false) }
    val homeContentAlpha by animateFloatAsState(
        if (bandEditMode) 0f else 1f,
        label = "homeContentAlpha",
    )
    // Edit mode borrows the app list's dim. Its controls are small text over whatever the
    // wallpaper happens to be, and on a busy one they were barely readable — the home screen's
    // own dim is usually off, since nothing normally sits there needing to be read.
    val homeDim =
        if (homeEditMode) maxOf(settings.dimHomeAlpha, settings.dimWallpaperAlpha) else settings.dimHomeAlpha
    var liveBand by remember { mutableStateOf<ScrubBand?>(null) }
    // Clamped on the way back in as well as on the way out: a range stored from a bad
    // measurement would otherwise put the strip off screen for good, with no gesture left to
    // reach it and fix it.
    val storedBand = scrubBandFractions
        ?.takeIf { (top, height) -> top.isFinite() && height.isFinite() && height > 0f }
        ?.let { (top, height) ->
            val bandHeight = (viewportHeightPx * height).coerceIn(0f, viewportHeightPx.toFloat())
            val bandTop = (viewportHeightPx * top)
                .coerceIn(0f, (viewportHeightPx - bandHeight).coerceAtLeast(0f))
            ScrubBand(topPx = bandTop, heightPx = bandHeight)
        }
    val band = liveBand ?: storedBand ?: favBand ?: ScrubBand.fallbackFor(viewportHeightPx)

    /** Locking is a strip gesture now, so the toast that explains it lives with the strip. */
    fun lockOrExplain() {
        if (!SystemUi.lockScreen()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_enable_accessibility_lock),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // How far in the overlay is: 0 sitting off the bottom, the full distance fully open. The
    // edge zones jump it straight to open, a swipe drags it there by hand.
    val openDistancePx = with(LocalDensity.current) { SWIPE_OPEN_DISTANCE.toPx() }
    val openAnim = remember { Animatable(0f) }
    val appListState = rememberLazyListState()
    // Set once a swipe has carried past fully open and started scrolling the list, so the
    // flick that ends it can be handed on rather than stopping dead.
    var swipeScrolledList by remember { mutableStateOf(false) }
    val flingDecay = rememberSplineBasedDecay<Float>()

    // Set while a launched app is expected to take over the screen; see closeAfterLaunch.
    var launchClose by remember { mutableStateOf<Job?>(null) }

    // The home screen fades back in when we put the list away ourselves, but not when the
    // system did it for us: laying our own fade over Android's home transition — or running it
    // on resume for a close that happened while we were backgrounded — just reads as noise.
    var snapHome by remember { mutableStateOf(false) }

    // Hoisted so closing the list clears it; the overlay stays composed while hidden, so a
    // query left behind would still be filtering the next time it opened.
    var appListQuery by remember { mutableStateOf("") }
    // Bumped to ask the list for the cursor, for the openings that are a search rather than
    // a look — the corner button set to it. A count because the same request can come twice.
    var searchFocusTick by remember { mutableIntStateOf(0) }

    // A keystroke on the home screen opens the list already searching for it. Only when there
    // is a search field to type into: without one the query filters a list showing no sign of
    // why, and there would be nothing on screen to clear it with.
    LaunchedEffect(typedToSearch) {
        if (typedToSearch.isEmpty()) return@LaunchedEffect
        val taken = typedToSearch
        onTypedToSearchHandled(taken)
        if (!settings.appListSearch || homeEditMode || bandEditMode) return@LaunchedEffect
        if (!appListVisible) {
            appListVisible = true
            openAnim.snapTo(openDistancePx)
        }
        appListQuery = taken.fold(appListQuery) { text, key ->
            if (key.char == null) text.dropLast(1) else text + key.char
        }
    }

    fun closeAppList(snap: Boolean = false) {
        appListQuery = ""
        scope.launch { openAnim.snapTo(0f) }
        launchClose?.cancel()
        launchClose = null
        snapHome = snap
        appListVisible = false
        // A panel opened from a drawer row belongs to that drawer. Leaving it up once the
        // drawer is gone is a panel over the home screen explaining a row that is not there.
        panelFor = null
        scrub.cancel()
    }

    // Closing the overlay the moment an app is launched puts the home screen on screen for
    // the few frames before that app's window arrives, which reads as a jolt back to home.
    // Leave the list up and let it go when we are actually backgrounded instead: by then
    // nothing of ours is visible, so the close costs nothing and can snap.
    fun closeAfterLaunch() {
        launchClose?.cancel()
        launchClose = scope.launch {
            // Nothing ever came to the foreground. Rather than leave the list stuck open,
            // put it away the ordinary animated way.
            delay(LAUNCH_CLOSE_TIMEOUT_MS)
            launchClose = null
            closeAppList()
        }
    }

    // The one place a row press is turned into something happening. Every row in the launcher
    // arrives here, so a row that is not an app is recognised once, here, rather than in each
    // of the four places a press is wired up.
    fun launchEntry(entry: AppInfo): Boolean = when (entry.kind) {
        // Handed off rather than done here: locking or unlocking is several binder calls and
        // possibly the system's own authentication screen, which is not a press's work to do
        // on the main thread. True because the press was acted on — there is simply nothing
        // coming to the foreground to wait for.
        EntryKind.PRIVATE_SPACE -> {
            onTogglePrivateSpace()
            true
        }
        else -> app.appRepository.launch(entry)
    }

    // Parked on the right whenever the list is up, for the same reason the left zone is dropped
    // above: otherwise a strip configured for the left would be sitting there, unscrubbed and
    // blocking BACK, for as long as the drawer stayed open.
    LaunchedEffect(settings.edgeSide, appListVisible) {
        if (appListVisible) {
            // Forcing, not syncing: the swipe that opens the list may still have a finger down,
            // and syncRestingSide deliberately stands down mid-gesture.
            scrub.forceRestingSide(EdgeSide.RIGHT)
        } else {
            scrub.syncRestingSide(settings.edgeSide)
        }
    }

    // BACK on the home screen must do nothing whatsoever.
    //
    // Without this it reaches the activity and finishes it, which is the default. A home
    // activity that finishes leaves the system to show whatever home-ish task is underneath
    // — and when the default launcher was switched *to* Victoria, the previous launcher's
    // task is still sitting there, so it comes back to the front. That looks like Victoria
    // handing control over, but it is simply this one exiting. A reboot clears the old task,
    // which is why it seemed to come and go, and why killing that launcher's process drops
    // to the system one instead.
    //
    // Registered before the handlers below so it stays the lowest priority: the dispatcher
    // runs the most recently added enabled callback first, so the overlay, edit mode and the
    // band editor all still get their turn at BACK before this swallows it.
    BackHandler(enabled = true) {}

    LaunchedEffect(appListVisible) { onAppListVisibleChange(appListVisible) }
    // Leaving the home destination entirely takes the overlay with it.
    DisposableEffect(Unit) { onDispose { onAppListVisibleChange(false) } }

    BackHandler(enabled = appListVisible) { closeAppList() }

    // Registered after the catch-all above so it outranks it, and before the edit-mode
    // handlers below only in the sense that it is enabled at the same time as the panel —
    // the dispatcher runs the most recently added enabled callback first, so whichever of
    // the two is up gets BACK. Without this the panel had no way out but tapping the scrim,
    // and BACK fell through to the empty handler and did nothing at all.
    BackHandler(enabled = notificationPanelVisible) { notificationPanelVisible = false }

    // The app panel is an overlay like the other two, and BACK is the way out of an overlay.
    // Registered after the catch-all so it outranks it.
    BackHandler(enabled = panelFor != null) { panelFor = null }

    // Every stepper commits as it is tapped, so there is nothing to save on the way out —
    // but leaving edit mode had to be done through the Done button, and BACK simply escaped
    // to the system and left the home screen stuck in it.
    BackHandler(enabled = homeEditMode) { homeEditMode = false }

    fun commitBand() {
        liveBand?.let { edited ->
            if (viewportHeightPx > 0) {
                onSetScrubBand(edited.topPx / viewportHeightPx, edited.heightPx / viewportHeightPx)
            }
        }
        liveBand = null
        bandEditMode = false
    }

    BackHandler(enabled = bandEditMode) { commitBand() }

    // Set for the length of a private space unlock that was asked to keep the list open.
    var keepListThroughPause by remember { mutableStateOf(false) }

    // Leaving the launcher (screen off, another app) should always drop us back to the home
    // screen rather than reopening onto the overlay.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The one pause we caused ourselves is let through: unlocking the private
                // space puts the system's own lock screen in front, and that is not leaving
                // the launcher. Spent as soon as we are back, so it can never excuse the next
                // real departure.
                // Used up by the pause it was set for, so it excuses that one and never a
                // later departure.
                Lifecycle.Event.ON_PAUSE ->
                    if (keepListThroughPause) keepListThroughPause = false else {
                        // The notification panel is an overlay too, and `remember` survives a
                        // stop/start: without this, leaving the launcher with it open and
                        // coming back reopened straight onto it.
                        notificationPanelVisible = false
                        closeAppList(snap = true)
                    }
                Lifecycle.Event.ON_RESUME -> keepListThroughPause = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(homeIntentTick) {
        if (homeIntentTick > 0) closeAppList(snap = true)
    }

    // The gap left for the back gesture, per side. Followed from the system when automatic,
    // since it reports how far its gesture reaches on each edge and reports nothing with
    // button navigation; the set number otherwise.
    val gestureInsets = WindowInsets.systemGestures
    val layoutDirection = LocalLayoutDirection.current
    val densityNow = LocalDensity.current
    val insetLeftDp = if (settings.edgeZoneInsetAuto) {
        with(densityNow) { gestureInsets.getLeft(this, layoutDirection).toDp().value.roundToInt() }
    } else {
        settings.edgeZoneInsetDp
    }
    val insetRightDp = if (settings.edgeZoneInsetAuto) {
        with(densityNow) { gestureInsets.getRight(this, layoutDirection).toDp().value.roundToInt() }
    } else {
        settings.edgeZoneInsetDp
    }
    // The widest of the live sides, for the content that has to clear whichever it is near.
    val insetLiveDp = when (settings.edgeSide) {
        EdgeSide.LEFT -> insetLeftDp
        EdgeSide.RIGHT -> insetRightDp
        EdgeSide.BOTH -> maxOf(insetLeftDp, insetRightDp)
    }

    LaunchedEffect(settings.edgeSide, settings.edgeZoneWidthDp, insetLeftDp, insetRightDp, view, band) {
        view.post {
            val density = view.resources.displayMetrics.density
            val widthPx = (settings.edgeZoneWidthDp * density).toInt()
            val insetLeftPx = (insetLeftDp * density).toInt()
            val insetRightPx = (insetRightDp * density).toInt()
            val h = view.height
            val w = view.width
            if (h > 0 && w > 0) {
                // Android's back-gesture claims the outer edges, and only honors 200dp of
                // exclusion per side — so spend it on the scrub band rather than spreading it
                // uselessly over the whole screen height.
                val capPx = (GESTURE_EXCLUSION_CAP_DP * density).toInt()
                val top = band.topPx.toInt().coerceIn(0, h)
                val bottom = band.bottomPx.toInt().coerceIn(top, h)
                // The band is usually taller than the cap, and whatever is left over stays
                // the system's: a touch there waits on the back-gesture detector before it
                // reaches us, which is the delay at the very edge. Centering what we are
                // given splits the unprotected remainder between the two ends rather than
                // leaving all of it below the halfway mark.
                val height = bottom - top
                val exTop = if (height > capPx) top + (height - capPx) / 2 else top
                val exBottom = if (height > capPx) exTop + capPx else bottom
                // Starting where the strip now starts. Excluding from the very edge while the
                // strip itself has been held inward is what made the setting do nothing: the
                // touch area moved, and the system was still being told to keep its back
                // gesture out of the pixels the setting had just handed back to it.
                // Only while the list is open. With the list up, the strip sits on the edge of a screen the
                // user is about to swipe back from, and holding the outermost pixels back for the
                // system is what makes BACK work out of the drawer. On the home screen the strip
                // is against the edge of nothing in particular and stays where it always was —
                // pulling it in there only made it harder to reach for no gain.
                val edgeGapPx = if (appListVisible) (EDGE_GAP_DP * density).toInt() else 0
                val rects = buildList {
                    if (settings.edgeSide != EdgeSide.RIGHT) {
                        add(Rect(insetLeftPx + edgeGapPx, exTop, insetLeftPx + widthPx, exBottom))
                    }
                    if (settings.edgeSide != EdgeSide.LEFT) {
                        add(
                            Rect(
                                w - widthPx - insetRightPx,
                                exTop,
                                w - insetRightPx - edgeGapPx,
                                exBottom,
                            )
                        )
                    }
                }
                ViewCompat.setSystemGestureExclusionRects(view, rects)
            }
        }
    }
    DisposableEffect(view) {
        // Settings and the other screens must get the system back-gesture back.
        onDispose { ViewCompat.setSystemGestureExclusionRects(view, emptyList()) }
    }

    // Worked out once, because two things need it: the strip itself, and the home content
    // that has to keep clear of a strip which is always there. A widget runs the full width,
    // so without this the strip simply sits on top of one.
    val stripAlwaysVisible = when (settings.azStripVisibility) {
        AzStripVisibility.NEVER -> false
        AzStripVisibility.ALWAYS -> true
        AzStripVisibility.LANDSCAPE ->
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        // Hidden entirely while the list is up, so only the wallpaper sits behind it — and
        // kept composed the same way, so coming back is instant.
        // Opening answers a finger on the edge and must not lag behind it, so it snaps. Coming
        // back gets a short decelerating fade: the wallpaper is already there, so this is only
        // the icons settling in, and cutting them in on a single frame is what read as a jolt.
        val homeAlpha by animateFloatAsState(
            targetValue = if (appListVisible) 0f else 1f,
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "homeAlpha",
        )

        // One dim for the wallpaper, belonging to neither screen and drawn under both.
        //
        // Each screen used to carry its own, which meant a handover rather than a change: on
        // the way back the list's faded out with the list while the home screen's was still
        // faded in, so a home dim of 80% and a list dim of 25% went 25 to nothing to 80. One
        // layer moving between the two values has nothing to hand over.
        val wallpaperDim by animateFloatAsState(
            targetValue = when {
                // The range is set against the wallpaper itself.
                bandEditMode -> 0f
                appListVisible -> settings.dimWallpaperAlpha
                else -> homeDim
            },
            animationSpec = if (appListVisible || snapHome) {
                snap()
            } else {
                tween(HOME_FADE_MS, easing = LinearOutSlowInEasing)
            },
            label = "wallpaperDim",
        )
        if (wallpaperDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(settings.dimColor).copy(alpha = wallpaperDim)),
            )
        }

        // Rounded up a little with the panel opening rather than snapping: the blur is what
        // makes the panel read as being in front of the home screen, and a value that jumps
        // reads as the home screen glitching instead.
        val overlayBlur by animateDpAsState(
            // Two levels, because the two panels ask for different things. The app panel is
            // beside its own row with the wallpaper still around it, so it wants a light touch —
            // a big radius reads as "gone" rather than "out of focus". The notification panel
            // covers the whole screen and has the most to hide behind it, so it takes the
            // heavier one and the home screen all but disappears behind it.
            targetValue = when {
                panelFor != null -> 10.dp
                notificationPanelVisible -> 18.dp
                // The long-press menu is translucent like the AppPanel, so the screen
                // behind it has to blur for the text to stay readable on it.
                homeMenuOpen -> 10.dp
                else -> 0.dp
            },
            animationSpec = tween(OVERLAY_BLUR_MS),
            label = "overlayBlur",
        )
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = homeAlpha }
                // Blurred when something is drawn over it, so the panel sits on a softened
                // home screen rather than on a flat grey one. The wallpaper itself is not in
                // this layer — the window draws it — so this softens the icons and labels
                // behind, which is what a grey wash was standing in for anyway.
                .blur(overlayBlur)
                .then(
                    // Hidden, but still laid out: an AppWidgetHostView that is never placed
                    // loses its layout and comes back with its text collapsed.
                    if (appListVisible) {
                        // The system says how wide its own gesture insets are, and those are
                        // exactly the pixels BACK is recognised in. Asking beats guessing at 24dp,
                        // which matches most devices by coincidence and would be too narrow on a
                        // skin that widens the region — an app quietly eating the edge of the
                        // gesture it was meant to be leaving alone.
                        val density = LocalDensity.current
                        val gesturePadding = WindowInsets.systemGestures.asPaddingValues()
                        val leftPx = with(density) { gesturePadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx() }
                        val rightPx = with(density) { gesturePadding.calculateRightPadding(LayoutDirection.Ltr).roundToPx() }
                        val topPx = with(density) { gesturePadding.calculateTopPadding().roundToPx() }
                        val bottomPx = with(density) { gesturePadding.calculateBottomPadding().roundToPx() }
                        Modifier.pointerInput(leftPx, rightPx, topPx, bottomPx) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val width = size.width
                                    val height = size.height
                                    event.changes.forEach { change ->
                                        val nearEdge = change.position.x < leftPx ||
                                            change.position.x > width - rightPx ||
                                            change.position.y < topPx ||
                                            change.position.y > height - bottomPx
                                        if (!nearEdge) change.consume()
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                // Setting the range takes the home screen out of the picture entirely, edit
                // layout's own controls with it. The band is judged against the wallpaper it
                // will sit on, not against the chrome it happens to overlap.
                .graphicsLayer { alpha = homeContentAlpha },
        ) {
            // Opened the whole way, as tapping the edge does. Marking it visible is not opening
            // it: the list is drawn against this, so without it the button produced the
            // half-faded list of a swipe abandoned part way.
            // Typed as Unit explicitly because the last line below is an increment, and without
            // it this is inferred as a function returning Int rather than one returning nothing.
            val openAppListForCorner: () -> Unit = {
                appListVisible = true
                appListQuery = ""
                scope.launch { openAnim.snapTo(openDistancePx) }
                searchFocusTick++
                Unit
            }
            // The search is reachable by tap, by hold and by swipe, and three copies of that
            // block would be three places for it to fall out of step.
            val longPressAction: (() -> Unit)? = when {
                cornerLongPressIsSearch -> openAppListForCorner
                else -> cornerLongPressApp?.let { app -> { launchEntry(app); Unit } }
            }
            val swipeUpAction: (() -> Unit)? = when {
                cornerSwipeUpIsSearch -> openAppListForCorner
                else -> cornerSwipeUpApp?.let { app -> { launchEntry(app); Unit } }
            }
            HomeScreen(
                // The strip's own side, not the edge setting's. Those are different questions: the edge
                // can be on both sides at once, while there is only ever one scrubber, and it
                // sits right unless the edge is specifically left. Passing BOTH through made
                // the strip reserve its full width on the left as well, which is what pushed
                // left-aligned favourites in from the edge by an amount no setting explained.
                stripInsetSide = if (!stripAlwaysVisible) null
                else if (settings.edgeSide == EdgeSide.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT,
                edgeZoneSide = settings.edgeSide,
                edgeZoneExtentDp = settings.edgeZoneWidthDp + insetLiveDp,
                homeIntentTick = homeIntentTick,
                closeFolderOnLaunch = settings.closeFolderOnLaunch,
                showFavoriteIcons = settings.showFavoriteIcons,
                // Only while it would still be telling someone something they do not know.
                showEmptyHint = !settings.favoritesEverSet &&
                    settings.favoritesSource == FavoritesSource.MANUAL,
                markWorkApps = settings.markWorkApps,
                textShadow = settings.textShadow,
                textShadowStrength = settings.textShadowStrength,
                compactTapArea = settings.compactTapArea,
                cornerButtonApp = cornerButtonApp,
                onOpenCornerApp = { launchEntry(it) },
                cornerButtonIsSearch = cornerButtonIsSearch,
                onOpenCornerSearch = openAppListForCorner,
                cornerLongPressAction = longPressAction,
                cornerSwipeUpAction = swipeUpAction,
                favoritesReorderable = settings.favoritesSource == FavoritesSource.MANUAL,
                shortcutSwipe = settings.shortcutSwipe,
                favorites = favorites,
                notificationPreviewByPackage = remember(notifications) {
                    newestPreviewsByPackage(notifications)
                },
                nameOverrides = nameOverrides,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                itemSpacingDp = settings.itemSpacingDp,
                sidePaddingDp = settings.sidePaddingDp,
                widgetSidePaddingDp = settings.widgetSidePaddingDp,
                widgetOffsetXDp = settings.widgetOffsetXDp,
                onSetSidePadding = { scope.launch { app.prefs.setSidePaddingDp(it) } },
                onSetWidgetSidePadding = { scope.launch { app.prefs.setWidgetSidePaddingDp(it) } },
                onSetWidgetOffsetX = { scope.launch { app.prefs.setWidgetOffsetXDp(it) } },
                paddings = homePaddings,
                widgetIds = widgetIds,
                widgetPosition = widgetPosition,
                widgetHeightDp = widgetHeightDp,
                hapticsEnabled = settings.hapticsEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                nowPlayingHeightDp = settings.nowPlayingHeightDp,
                onResizeNowPlaying = { scope.launch { app.prefs.setNowPlayingHeightDp(it) } },
                widgetActions = widgetActions,
                onLaunch = { launchEntry(it) },
                onOpenPanel = { app, top, left -> panelFor = Triple(app, top, left) },
                // Remove means the same thing to whoever taps it either way: take this off my
                // home screen. Which list it writes to depends on which list is being shown —
                // against the computed favorites it used to edit the manual list nobody could
                // see, so the row stayed put and the menu item did nothing at all.
                onRemoveFavorite = {
                    scope.launch {
                        if (settings.favoritesSource == FavoritesSource.FREQUENT) {
                            app.prefs.setFrequentExcluded(it.key, excluded = true)
                        } else {
                            app.prefs.removeFavorite(it.key)
                        }
                    }
                },
                onOpenFolderApp = { launchEntry(it) },
                onRenameFolder = { folder, name ->
                    scope.launch { app.prefs.upsertFolder(folder.copy(name = name)) }
                },
                onDeleteFolder = { folder -> scope.launch { app.prefs.deleteFolder(folder.id) } },
                onChangeFolderIcon = { folder -> onNavigate(iconPickerRoute(folderToken(folder.id))) },
                onResetFolderIcon = { folder -> scope.launch { app.prefs.setFolderIcon(folder.id, null) } },
                onManageFolder = { folder -> onNavigate("folder/" + folder.id) },
                onRemoveFromFolder = { folder, appInfo ->
                    scope.launch { app.prefs.removeAppFromFolder(folder.id, appInfo.key) }
                },
                appsByKey = appsByKey,
                onMoveToFolder = { folderPickerFor = it },
                onReorderHome = { newFavKeys, newWidgetPos ->
                    scope.launch {
                        // The home screen draws only the favorites that resolve to a row, and
                        // this hands back the order of what it drew. Written as it stands, that
                        // would replace the stored list and delete every favorite that drew
                        // nothing — which is exactly what a locked private space's favorites
                        // do. Whatever drew no row goes back where it was.
                        val drawn = newFavKeys.toSet()
                        app.prefs.setFavorites(restoreConcealed(favoriteKeys, newFavKeys) { it !in drawn })
                        app.prefs.setWidgetPosition(newWidgetPos)
                    }
                },
                onCommitPadding = { slot: PaddingSlot, value: Int ->
                    scope.launch { app.prefs.setHomePadding(slot, value) }
                },
                onFavoritesBoundsChanged = { top, bottom ->
                    // Measured in window space, so a home screen still travelling under the
                    // open overlay would drag the alphabet along with it. Whatever the band
                    // was when the list opened is what it stays.
                    //
                    // Edit mode is ignored for the same reason and a better one: it lays the
                    // favorites out differently, and the strip should match where they
                    // actually sit rather than where they sit while being rearranged.
                    if (appListVisible || homeEditMode) return@HomeScreen
                    // Not the favorites' own span when that is too short to divide between
                    // the letters — see ScrubberGeometry.bandForFavorites.
                    favBand = ScrubberGeometry.bandForFavorites(
                        topPx = top,
                        bottomPx = bottom,
                        viewportPx = viewportHeightPx.toFloat(),
                        minHeightPx = minBandHeightPx,
                    )
                },
                nowPlayingHasContent = nowPlayingHasContent,
                contentColor = settings.contentColor,
                showFavoriteLabels = settings.showFavoriteLabels,
                onMenuOpen = { homeMenuOpen = it },
                alignment = settings.alignment,
                iconSide = settings.iconSide,
                editMode = homeEditMode,
                onEditModeChange = { homeEditMode = it },
                onEditScrubBand = { liveBand = band; bandEditMode = true },
                centerFavorites = settings.centerFavorites,
                swipeUpOpensAppList = settings.swipeUpOpensAppList,
                onSwipeUpDrag = { total, delta ->
                    appListVisible = true
                    scope.launch { openAnim.snapTo(total.coerceAtMost(openDistancePx)) }
                    // Once it is all the way in the finger is usually still moving, so the
                    // rest of the drag goes to the list rather than stopping dead against it.
                    if (total > openDistancePx) {
                        swipeScrolledList = true
                        appListState.dispatchRawDelta(delta)
                    }
                },
                onSwipeUpEnd = { velocity ->
                    scope.launch {
                        val settled = openAnim.value > openDistancePx * 0.4f || velocity < -1200f
                        if (settled) {
                            openAnim.animateTo(
                                targetValue = openDistancePx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                            // Asked for once it has settled, not on the way: a keyboard
                            // arriving mid-animation fights the list for the same space.
                            if (settings.swipeUpOpensSearch && !swipeScrolledList) {
                                searchFocusTick++
                            }
                        } else {
                            openAnim.animateTo(0f, tween(160, easing = FastOutLinearInEasing))
                            closeAppList()
                        }
                    }
                    // The scrolling half of this gesture was fed the drag raw, which carries
                    // no momentum of its own — so a flick that starts after the list is
                    // already moving has to have its velocity handed over explicitly, or the
                    // list stops the instant the finger leaves.
                    if (swipeScrolledList && velocity < 0f) {
                        scope.launch {
                            appListState.scroll {
                                var travelled = 0f
                                AnimationState(initialValue = 0f, initialVelocity = -velocity)
                                    .animateDecay(flingDecay) {
                                        scrollBy(value - travelled)
                                        travelled = value
                                    }
                            }
                        }
                    }
                    swipeScrolledList = false
                },
                quickLaunchEnabled = settings.quickLaunchLeft != null || settings.quickLaunchRight != null,
                onQuickLaunch = { slot ->
                    val target = when (slot) {
                        QuickLaunchSlot.LEFT -> settings.quickLaunchLeft
                        QuickLaunchSlot.RIGHT -> settings.quickLaunchRight
                    }
                    target?.let { launchEntry(it) }
                },
                onPeekStatusBar = onPeekStatusBar,
                onExpandShade = {
                    // The notification list takes the deep pull only when it is switched on;
                    // otherwise this is the system shade, which is what it always was.
                    if (notificationListOnPull) {
                        notificationPanelVisible = true
                    } else if (!SystemUi.expandNotificationShade(context)) {
                        // Say so rather than failing silently — that way a pull that does nothing
                        // tells you whether the gesture or the permission is at fault.
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_enable_accessibility_shade),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onManageFavorites = { onNavigate("favorites") },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo -> onNavigate(iconPickerRoute(appInfo.key)) },
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onUnpinShortcut = { app.appRepository.unpin(it) },
                onOpenSettings = { onNavigate("settings") },
            )
        }

        // Kept composed and measured even while hidden, just never placed. Not placing it
        // means it neither draws nor receives touches.
        val overlayAlpha = if (appListVisible) 1f else 0f
        // A panel can be opened from a row in here, and until this existed the blur behind
        // it was applied to the home screen only - which is under the drawer. The panel was
        // therefore drawn over a sharp, unblurred list and its near-clear fill had nothing
        // behind it to read against. Same treatment, applied to whichever layer the panel
        // actually has in front of it.
        val drawerBlur by animateDpAsState(
            targetValue = if (panelFor != null) 10.dp else 0.dp,
            animationSpec = tween(OVERLAY_BLUR_MS),
            label = "drawerBlur",
        )
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = overlayAlpha }
                .blur(drawerBlur)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (appListVisible) {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    } else {
                        layout(0, 0) {}
                    }
                },
        ) {
            AppListScreen(
                model = listModel,
                nameOverrides = nameOverrides,
                scrub = scrub,
                iconSizeDp = settings.iconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                band = band,
                viewportHeightPx = viewportHeightPx,
                visible = appListVisible,
                favoriteKeys = remember(favoriteKeys) { favoriteKeys.toSet() },
                onLaunch = { appInfo ->
                    // A launch that never got off the ground leaves nothing to wait for, and
                    // the private-space row has nothing coming to the foreground either — it
                    // changes what this very list holds, so it wants the list out of the way.
                    // Unless asked to stay, in which case the list is where the space's apps
                    // are about to appear, and closing it means opening it again to see them.
                    val stay = appInfo.kind == EntryKind.PRIVATE_SPACE && settings.stayAfterPrivateUnlock
                    if (stay) {
                        keepListThroughPause = true
                        // A phone with no screen lock unlocks without showing anything, so no
                        // pause comes to use this up. Left standing it would let the next real
                        // departure keep the list open, so it lapses if the lock screen has not
                        // appeared by now — which it does at once when there is one.
                        scope.launch {
                            delay(PRIVATE_UNLOCK_PROMPT_MS)
                            keepListThroughPause = false
                        }
                    }
                    val acted = launchEntry(appInfo)
                    when {
                        stay -> Unit
                        acted && appInfo.kind == EntryKind.APP -> closeAfterLaunch()
                        else -> closeAppList()
                    }
                },
                onSetFavorite = { appInfo, add ->
                    scope.launch {
                        if (add) app.prefs.addFavorite(appInfo.key) else app.prefs.removeFavorite(appInfo.key)
                    }
                },
                onSetName = { appInfo, name -> scope.launch { app.prefs.setNameOverride(appInfo.key, name) } },
                onChangeIcon = { appInfo ->
                    closeAppList()
                    onNavigate(iconPickerRoute(appInfo.key))
                },
                onAppInfo = { app.appRepository.openAppInfo(it) },
                onUnpinShortcut = { appInfo -> closeAppList(); app.appRepository.unpin(appInfo) },
                onHideApp = { appInfo, hide -> scope.launch { app.prefs.setHidden(appInfo.key, hide) } },
                hiddenApps = hiddenApps,
                onMoveToFolder = { appInfo -> closeAppList(); folderPickerFor = appInfo },
                onOpenFolder = { id -> closeAppList(); onNavigate("folder/" + id) },
                onOpenSettings = { closeAppList(); onNavigate("settings") },
                // The same panel the home screen opens, from the same gesture on a row. The
                // drawer is an overlay the destination owns, so it cannot draw a full-screen
                // panel over itself and reports the row it was opened from instead.
                onOpenPanel = { app, top, left -> panelFor = Triple(app, top, left) },
                onDismiss = { closeAppList() },
                contentColor = settings.contentColor,
                notificationPreviewByPackage = remember(notifications) {
                    newestPreviewsByPackage(notifications)
                },
                hapticsEnabled = settings.hapticsEnabled,
                showAlphabet = settings.showAlphabet,
                edgeSide = settings.edgeSide,
                statusBarHidden = settings.hideStatusBarAppList,
                searchModel = searchModel,
                shortcutSwipe = settings.shortcutSwipe,
                favoritesEditable = settings.favoritesSource == FavoritesSource.MANUAL,
                showSettingsRow = settings.showSettingsRow,
                autoKeyboard = autoKeyboard,
                searchFocusTick = searchFocusTick,
                holdScrollSpeed = settings.holdScrollSpeed,
                dimOthers = settings.dimOthersScrubbing,
                markWorkApps = settings.markWorkApps,
                textShadow = settings.textShadow,
                textShadowStrength = settings.textShadowStrength,
                edgeZoneWidthDp = settings.edgeZoneWidthDp + insetLiveDp,
                // Offered only where something can take it, or the row is a dead end.
                webSearchFallback = settings.webSearchFallback && webSearchAvailable,
                onWebSearch = { term ->
                    val intent = Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, term)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    closeAppList()
                },
                lastLetterToLine = lastLetterToLine,
                searchEnabled = settings.appListSearch,
                searchAtBottom = settings.appListSearchBottom,
                sectionTopPercent = settings.sectionTopPercent,
                listState = appListState,
                // Distance still to travel, which is exactly what the collapse transform
                // takes: the list arrives scaled down and faded, and grows into place.
                enterPullPx = openDistancePx - openAnim.value,
                query = appListQuery,
                onQueryChange = { appListQuery = it },
                alignment = settings.appListAlignment,
                iconSide = settings.iconSide,
            )
        }

        // Above everything else, and last, so a pull that opens it cannot be mistaken for a
        // pull that opened the list underneath. Only drawn while open: keeping a full-screen
        // scrim composed behind the home screen would sit over every tap on it.
        if (notificationPanelVisible) {
            NotificationPanel(
                visible = true,
                notifications = notifications,
                appsByPackage = appsByPackage,
                contentColor = settings.contentColor,
                onDismissEntry = { NowPlayingListenerService.dismiss(it) },
                onPerformAction = { action, text ->
                    // The text travels with the intent: a reply action carries the message in
                    // the bundle RemoteInput puts on it, and the posting app reads it back from
                    // there. Sending with no text is never reached — the panel opens the field.
                    NowPlayingListenerService.performAction(action.intent, action.remoteInputs, text)
                },
                onOpenApp = { entry ->
                    notificationPanelVisible = false
                    launchEntry(entry)
                },
                // Everything the system will actually clear, and nothing else: an ongoing
                // notification has no clear in the system shade either, and cancelling one
                // would stop a download or a playing track rather than tidy a list.
                onClearAll = {
                    notifications.filter { it.clearable }.forEach {
                        NowPlayingListenerService.dismiss(it.key)
                    }
                },
                onClose = { notificationPanelVisible = false },
            )
        }

        // The band editor draws no letters of its own: the strip is what shows the range
        // being dragged, so it is given whether or not the always-on setting asked for it.
        // Edit layout is the other way round — its own controls sit where the strip does, and
        // the two were drawn on top of each other.
        // Sideways the strip is worth the room it takes; upright the same setting can be too
        // much, so a phone in a car mount can have it there without carrying it everywhere.
        val showIdleStripNow = when {
            appListVisible -> false
            bandEditMode -> true
            homeEditMode -> false
            else -> stripAlwaysVisible
        }
        if (showIdleStripNow) {
            EdgeScrubber(
                letters = listModel.letters,
                // Reading the holder rather than reporting nothing, so the strip bows around a
                // fingertip that lands on it here exactly as it does in the drawer.
                scrubY = { scrub.currentY() },
                pullPx = { scrub.currentPull() },
                band = band,
                side = scrub.side,
                // Tappable here too: pointing at an M on the home screen is the same intention
                // as pointing at one in the drawer, and should land in the same place.
                state = scrub,
                listOpen = false,
                onOpen = {
                    appListVisible = true
                    scope.launch { openAnim.snapTo(openDistancePx) }
                },
                hapticsEnabled = settings.hapticsEnabled,
                // Just off the edge on the home screen. Not flush: right against the bezel the
                // letters sit under the system's own edge inset and read as clipped, and the
                // couple of dp of daylight keeps them off the curve of the screen.
                edgeInsetDp = 2f,
                modifier = Modifier.align(
                    if (scrub.side == EdgeSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                ),
            )
        }

        if (showWelcome) {
            WelcomeDialog(onDismiss = onWelcomeDismissed)
        }

        folderPickerFor?.let { target ->
            FolderPickerDialog(
                appLabel = nameOverrides[target.key] ?: target.label,
                folders = folders,
                // The rows this launcher can actually draw, which is what the home screen's
                // own badge counts. A folder's stored keys include whatever a locked private
                // space is hiding.
                memberCount = { folder -> folder.apps.count { it in appsByKey } },
                onPickFolder = { folder ->
                    scope.launch { app.prefs.addAppToFolder(folder.id, target.key) }
                    folderPickerFor = null
                },
                onCreateFolder = { name ->
                    scope.launch {
                        val folder = Folder(
                            id = System.currentTimeMillis().toString(36),
                            name = name,
                            apps = listOf(target.key),
                        )
                        app.prefs.upsertFolder(folder)
                        // Drop the app's own row and give the folder one in its place.
                        app.prefs.removeFavorite(target.key)
                        app.prefs.addFavorite(folderToken(folder.id))
                    }
                    folderPickerFor = null
                },
                onDismiss = { folderPickerFor = null },
            )
        }

        if (bandEditMode) {
            BandEditOverlay(
                band = liveBand ?: band,
                side = scrub.side,
                viewportHeightPx = viewportHeightPx,
                letters = listModel.letters,
                onBandChange = { liveBand = it },
                // Stays open rather than exiting: dropping the stored range hands the
                // strip back to the favorites, and the point of a reset is watching it land
                // there. Done then leaves without storing anything, since there is nothing
                // being edited any more.
                onReset = { liveBand = null; onClearScrubBand() },
                onDone = { commitBand() },
            )
        }

        // Edge zones sit on top of everything, so one unbroken touch opens the list and then
        // scrubs it as the finger moves.
        if (!homeEditMode && !bandEditMode && appListQuery.isEmpty()) {
            val sides = remember(settings.edgeSide, appListVisible) {
                when {
                    // With the list open the left edge belongs to the system. Android reads a
                    // swipe from there as BACK, and a strip sitting on top of it wins some of
                    // those, so BACK stops working in the drawer — which is where someone reaches
                    // for it. The strip goes on the right in the drawer whatever opened it, and
                    // the left zone goes away entirely rather than half-working.
                    appListVisible -> listOf(EdgeSide.RIGHT)
                    else -> when (settings.edgeSide) {
                        EdgeSide.LEFT -> listOf(EdgeSide.LEFT)
                        EdgeSide.RIGHT -> listOf(EdgeSide.RIGHT)
                        EdgeSide.BOTH -> listOf(EdgeSide.LEFT, EdgeSide.RIGHT)
                    }
                }
            }
            sides.forEach { side ->
                EdgeTouchZone(
                    side = side,
                    widthDp = settings.edgeZoneWidthDp.dp,
                    insetDp = (if (side == EdgeSide.LEFT) insetLeftDp else insetRightDp).dp,
                    letters = listModel.letters,
                    band = band,
                    bandOnly = settings.edgeZoneBandOnly,
                    holdScroll = settings.holdScroll,
                    hapticsEnabled = settings.hapticsEnabled,
                    state = scrub,
                    listOpen = appListVisible,
                    onDismiss = { closeAppList() },
                    onOpen = {
                        appListVisible = true
                        // Opened by touching the edge, so there is nothing to animate in.
                        scope.launch { openAnim.snapTo(openDistancePx) }
                    },
                    onDoubleTap = if (settings.doubleTapToLock) ({ lockOrExplain() }) else null,
                    // Aligned to the top when it is confined, because the offset that puts it
                    // against the band is measured from there. Centred, it would be placed by
                    // half the leftover height and then pushed down again by the whole of the
                    // band's top.
                    modifier = Modifier.align(
                        when {
                            settings.edgeZoneBandOnly && side == EdgeSide.LEFT -> Alignment.TopStart
                            settings.edgeZoneBandOnly -> Alignment.TopEnd
                            side == EdgeSide.LEFT -> Alignment.CenterStart
                            else -> Alignment.CenterEnd
                        }
                    ),
                )
            }

        // Very last of everything. The strip, the edge zones and the app list are all drawn
        // over the home screen, so a full-screen panel put anywhere above them shows through
        // the alphabet down its right-hand edge.
        panelFor?.let { (panelApp, topDp, leftDp) ->
            AppPanel(
                app = panelApp,
                labelSizeSp = settings.labelSizeSp,
                topDp = topDp,
                leftDp = leftDp,
                onDismiss = { panelFor = null },
                onOpenApp = {
                    panelFor = null
                    launchEntry(panelApp)
                },
                onOpenWidgetPicker = { focusPackage ->
                    panelFor = null
                    widgetActions.onAddWidget(focusPackage)
                },
                onAddWidgetDirect = { info ->
                    panelFor = null
                    widgetActions.onAddWidgetDirect(info)
                },
            )
        }
        }
    }
}

private fun iconPickerRoute(key: String) = "iconpicker/" + Uri.encode(key)

/** Display and behavior settings the home destination reads, grouped so they travel as one. */
@Immutable
data class HomeSettings(
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val itemSpacingDp: Int,
    val sidePaddingDp: Int,
    val nowPlayingHeightDp: Int,
    val nowPlayingEnabled: Boolean,
    val edgeSide: EdgeSide,
    val azStripVisibility: AzStripVisibility,
    val shortcutSwipe: ShortcutSwipe,
    val showAlphabet: Boolean,
    val alignment: HomeAlignment,
    val appListAlignment: HomeAlignment,
    val iconSide: IconSide,
    val widgetSidePaddingDp: Int,
    val widgetOffsetXDp: Int,
    val edgeZoneWidthDp: Int,
    val swipeUpOpensAppList: Boolean,
    val appListSearch: Boolean,
    val appListSearchBottom: Boolean,
    val sectionTopPercent: Int,
    val closeFolderOnLaunch: Boolean,
    val showListHeaders: Boolean,
    val showSettingsRow: Boolean,
    val showFavoriteIcons: Boolean,
    val favoritesEverSet: Boolean,
    val edgeZoneBandOnly: Boolean,
    val holdScroll: Boolean,
    val holdScrollSpeed: Int,
    val dimOthersScrubbing: Boolean,
    val markWorkApps: Boolean,
    val textShadow: Boolean,
    val textShadowStrength: Int,
    val compactTapArea: Boolean,
    val privateSpaceSection: Boolean,
    val recentSection: Boolean,
    val recentSectionCount: Int,
    val edgeZoneInsetDp: Int,
    val edgeZoneInsetAuto: Boolean,
    val stayAfterPrivateUnlock: Boolean,
    val webSearchFallback: Boolean,
    val swipeUpOpensSearch: Boolean,
    val favoritesSource: FavoritesSource,
    val appListSearchHidden: Boolean,
    val hideStatusBarAppList: Boolean,
    val sortByUsage: Boolean,
    val quickLaunchLeft: AppInfo?,
    val quickLaunchRight: AppInfo?,
    /** Place the favorites by measurement, until the user sets a padding of their own. */
    val centerFavorites: Boolean,
    val dimWallpaperAlpha: Float,
    val dimHomeAlpha: Float,
    val dimColor: Int,
    val hapticsEnabled: Boolean,
    val showFavoriteLabels: Boolean,
    val doubleTapToLock: Boolean,
    val contentColor: Color,
)

/** How long a private space unlock has to put the system's lock screen up before giving up. */
private const val PRIVATE_UNLOCK_PROMPT_MS = 1500L
