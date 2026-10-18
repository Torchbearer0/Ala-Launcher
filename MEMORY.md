# Memory — Victoria/Ala Launcher fork

**Write this file after every message.** Keep it short, factual, and current. Delete
anything that is no longer true rather than annotating it. A wrong "still true" line is
worse than a missing one.

Last updated: after commit `f1e059a` (page edit mode + AMOLED settings).

---

## Project

- Path: `C:\Users\udayk\Documents\Default Project\victoria-launcher`
- Rebranded to **Ala Launcher**, version 1.0, on 2026-10-17.
- `applicationId` is **still `dev.victorialauncher`** — deliberately not renamed. It is
  Android's identity for the app; changing it makes a different app and orphans the granted
  notification-listener permission, the allocated widget ids, the DataStore and any backup.
  `versionCode` went 107 → 108 for the same reason (a downgrade fails to install).
- Upstream is `adelmonte/victoria-launcher` by **adelmonte**, GPL-3.0-or-later. Credits now
  live in the About section, not only the README.
- Remote to push to: `git push fork main` (fork = Torchbearer0).

## Backups

- **`C:\Users\udayk\Desktop\Ala-Launcher-backup-2026-10-17.zip`** — 10.3 MB, 280 entries, 98
  Kotlin files, made with `git archive --format=zip -o <dest> HEAD` from the repo root.
  Tracked files only, so no build output and no gradle caches, and it is exactly a commit.
  Check what is in one with `[System.IO.Compression.ZipFile]::OpenRead(...)`.
- `NEXT-SESSION.md` no longer exists — its content was folded into this file. Nothing is lost.

## Environment

- `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- Build: `.\gradlew.bat assembleDebug testDebugUnitTest --offline` from the project root.
- `git` is at `C:\Program Files\Git\cmd\git.exe`, not on PATH.
- adb is at `C:\Users\udayk\AppData\Local\Android\Sdk\platform-tools`, not on PATH.
- Device serial `963028779900070`. Screen 1080x2376, density 380.
- `git`/adb sometimes need `$env:PATH` prepended per shell invocation.
- 187 unit tests passing as of `0626cc2`.

## Hard-won rules — do not relearn these

1. **Never bulk-rewrite Kotlin with PowerShell `Set-Content`/`-replace`.** It has corrupted
   files here twice (once stripped a whole object body and left stray braces; once ate a KDoc
   block). Use the editor tool. Reading a file's lines via `[System.IO.File]::ReadAllLines`
   is fine; *writing* that way is the problem.
2. **`adb shell ... > file` corrupts binary.** PowerShell redirection mangles the bytes, so a
   DataStore read comes back looking like the key is missing when it is not. Use base64:
   `adb shell "run-as <pkg> base64 files/datastore/x.preferences_pb"`, then
   `[Convert]::FromBase64String` after stripping whitespace. This produced a whole round of
   false "NOT SET" readings before it was caught.
3. **The user is the ground truth and often right.** Twice they were right and I was wrong:
   a swipe that "didn't fire" was a broken *check*, and a gesture they wanted reverted was a
   gesture I had broken. Check my instrumentation before believing my own negative result.
4. **Diagnose with device evidence, not reasoning.** `adb shell uiautomator dump` + reading the
   XML gives text/content-desc and node bounds — enough to drive the UI blind and to see
   whether something is really on screen.
5. **A Compose gesture handler that covers a whole screen must only consume drags it actually
   acts on.** `detectHorizontalDragGestures` consumes at slop *regardless of direction*, so
   using it for "only if eligible" steals gestures from everything above it. This bit three
   times: the quick-launch detector, and then the -1 page's own swipe-to-dismiss, which both
   consumed every move it saw and treated any 72dp sideways travel as "dismiss".
6. **Measure drags by absolute position, not `positionChange()`** when the down may already be
   consumed — `positionChange()` returns 0 for a consumed change, so travel always reads 0.
7. **A hosted widget is an Android view handling its own touch stream.** Any `pointerInput`
   wrapped around it waits for an unconsumed drag it is never given. Gestures over a widget
   have to be read inside `LongPressFrameLayout`.
8. **An Android `ViewGroup.onInterceptTouchEvent` is not called again after it returns true.**
   Everything after that arrives at `onTouchEvent`. A drag handler written only in the intercept
   pass therefore fires exactly once, with a slop-sized delta, and then goes quiet. Read the
   drag in **both** passes.
9. **`onSizeChanged` measures where it is attached, not what the content got.** Attached above
   a `padding`, it reports the padded size and any arithmetic done from it produces something
   that does not fit. Attach it below the padding.
10. **A clickable Compose parent merges its children's semantics**, which is why a `contentDescription`
    on a child `Icon` never reaches the accessibility tree — and therefore never reaches
    `uiautomator dump` either. Put the label on the clickable box itself via
    `.semantics { contentDescription = ... }`.
11. **No `INTERNET` permission, ever.** Deliberate. This is why the Discover page hands off to
   the Google app instead of embedding a feed — a WebView would need the permission.
12. **Never change a background colour without changing the content colour with it.** Making the
   settings page true black while leaving the content on the system scheme gave a light-mode
   device near-black text on a black page. Measure it, don't eyeball it — see below.
13. **`AndroidView` builds its view once, in the factory, and never again.** Wrap it in
   `key(widgetId)` or a list that reorders will keep every slot drawing its old content. This
   is why the cross used to remove a widget that wasn't the one under the finger.
14. **To check colours on device without being able to see the screen:** `adb shell screencap -p`,
   `adb pull`, then `System.Drawing.Bitmap` + `GetPixel` in PowerShell. Sample the bounds of a
   text node from a `uiautomator` dump and report min/max luminance — that gives a real
   contrast ratio instead of an opinion. (Never `adb ... > file` for binary; `pull` is fine.)
   Beware: a scan can land on the app bar and read as "the effect is missing" when it is simply
   under an opaque header. Scan a column and look at the falloff, not a single point.
15. **Anything drawn *behind* a `Scaffold` needs the Scaffold's container to be transparent.**
   An opaque `containerColor` is a wall, and whatever the box underneath drew is invisible.
16. **The stock widget picker needs a press-hold-then-drag**, which `adb input swipe` cannot
   produce (it interpolates from the first move, so there is no hold). Don't burn turns trying
   to add a second widget to test reordering — test the logic as a pure function instead.
17. **`adb input swipe x y x y 900` is a long press.** And remember a widget's real bounds from
   the dump before pressing it — pressing at a stale coordinate lands outside the widget and
   silently does nothing, which reads as a broken gesture.
18. **`onInterceptTouchEvent` is NOT called for MOVE once a child has the gesture.** A ViewGroup
   only consults it for the press and while it has no touch target. To see a drag that a child
   is competing for, override **`dispatchTouchEvent`** — it is called for every event that
   reaches the view, whichever child handles it. This cost three rounds of dead-end debugging.
19. **Claim the gesture from ACTION_DOWN, not from your own slop.** If you ignore a drag until
   it clears your threshold, a scrolling ancestor sees the same threshold cross first and takes
   the gesture. You then get exactly one MOVE. Symptom: the gesture "half works".
20. **Never write state from a captured composition snapshot.** `updateEntries` applied each
   change to the list as it was at composition time, so several writes in a few hundred ms
   overwrote each other and the last won with stale data. Read the current value **inside the
   coroutine** (`app.prefs.flow.first()`). Symptom: the UI changes and nothing persists.
21. **NEVER bulk-edit Kotlin from PowerShell, not even "just a few exact token renames".** I
   did it once for 10 `Checkbox(`→`GlowingCheckbox(` lines and it happened to be fine — but
   only because every `String.Contains` was checked first. Use the editor tool.
22. **`SwitchDefaults.colors` / `CheckboxDefaults.colors` do not all take the same parameters.**
   `SwitchDefaults` has no `uncheckedBorderColor`; `CheckboxDefaults` has no
   `uncheckedBorderColor` either (only `disabledBorderColor`). Drop the param rather than
   guessing again.
23. **Material3 controls do not appear in a `uiautomator` dump as their platform class.**
   A Compose `Switch` is not an `android.widget.Switch` node. To check one exists and is
   coloured, screencap and count accent pixels — the pixel-sampling recipe above.

## The page to the left of home - REMOVED

**Gone, commit 65446c.** Do not try to restore or rebuild it. It went through six
attempts (stale AndroidView, one-way drag, writes to a stale snapshot, the gesture read
from the wrong callback, only one MOVE arriving, overlap on drop) and never worked well
enough to keep working on. The user asked for it to be taken out.

MinusOnePage.kt, its two test files, the overlay, the gap-band swipe, both BackHandlers,
the minusOneVisible blur, the picker branch and the settings row are all deleted.
data/MinusOneEntry.kt and the minus_one_* preferences were **left in place on purpose** -
nothing reads or writes them, so they are inert, and deleting a user's stored settings for
no gain is worse than leaving them.

Still there and unrelated: the home screen's own widget slot, the row swipe that opens an
app's panel, and the notification panel with a working back-gesture.
## Settings

- Page background is **true black** (`lerp(Color.Black, surface, dimPreviewReveal)`) — the
  wallpaper returns only while the dim setting is being previewed.
- The whole `settings` nav route is wrapped in `MaterialTheme(colorScheme = amoledColorScheme())`
  (`ui/theme/Theme.kt`). **Scoped to the route, not inside `SettingsScreen`**, because the screen
  is a ~180-parameter entry point and re-indenting its body to wrap it in a theme is exactly the
  kind of edit that corrupts the file. `VictoriaNavHost.kt` `composable("settings")`.
- Cards are `SettingsTone.card` = `#0B0B0E` with a 1dp `SettingsTone.edge` hairline. A card on
  black cannot separate itself by being lighter; the border is what draws it.
- The three tone values live together in `SettingsTone` in `SettingsComponents.kt` on purpose —
  they are one idea and drift apart if they are written at each use.
- The sections list is **one card containing 8 rows**, each with a coloured mark at its left.
  `SettingsRow` grew a `leading` slot for it.
- Measured: page `0,0,0`, card `0B0B0E`, section label luminance 11→231 ≈ **16:1 contrast**.
- **Sailfish pass — one accent for the whole app.** `ui/theme/Glow.kt` holds `GlowAccent`
  (`#6FE3F5`) and every lit control: `GlowingSwitch`, `GlowingCheckbox`, `GlowingRadioButton`,
  `GlowingSlider`, `GlowingRangeSlider`, `GlowingFilterChip`, `GlowingSegment` /
  `GlowingSegmentTrack`. **All of them are in the app except the last four** — those exist so
  the next screen that needs one does not invent its own.
  - `GlowHalo()` is the shared halo: a `Brush.radialGradient` drawn at ~2× its slot, **never a
    blur** (no app-owned view for a RenderEffect; a per-frame one is wrong for a scrolling list).
  - `SettingsTone.accent` now just aliases `GlowAccent`, so settings code reads naturally and
    there is one value to change.
  - **Unticked controls are rings, not gaps** — they go near-black but keep a faint outline. A
    switch that vanishes when off is a switch you cannot find.
  - **Slider thumb is white, track is accent.** An accented thumb reads as part of the track.
- Measured on device: settings switch column 7.56% accent, favourites checkbox column 1.18%
  (a small square yields less than a switch pill), sections-list mark column 4.91%.
- The page has one soft radial light behind the marks, falling off to true black by the bottom
  of the list. It only shows because **`Scaffold(containerColor = Color.Transparent)`** and the
  page colour is painted by the outer `Box` — an opaque container is a wall.
- Cards get a 1dp horizontal-gradient rim on their top edge so a flat panel reads as raised.
- Measured: light from `(18,26,28)` under the app bar to `(0,0,0)` lower down; cards `0B0B0E`;
  labels still 11→231.

## Still outstanding

### Niagara parity — status, checked 2026-10-17

| Feature | Status |
|---|---|
| **Hidden apps in search** | **ALREADY DONE.** `appListSearchHidden` in `Prefs.kt` defaults to **`false`**, so hidden apps are excluded from search by default and there is a setting to opt in (`settings_search_hidden`, App list section). **Not a Niagara gap for us — do not "implement" it.** |
| Usage Breaker (nudge at 5/10/20…240 min) | not started |
| Contact search with direct call/SMS | not started |
| Notification Summary / batching | not started |

### The -1 page is still called "very very buggy" by the user

I have fixed six separate faults on it blind (stale `AndroidView`, one-way drag, writes to a
stale snapshot, gesture read from the wrong callback, one MOVE only, overlap on drop) and each
time they found something new. **Do not keep guessing.** Ask which gesture they just performed
and what they expected, one at a time, and reproduce that exact case before changing anything.

## Gesture exclusion — the back-swipe bug

`HomeRoute` spends Android's 200dp-per-side back-gesture exclusion budget on the **A-Z scrub
band**. That is right on the home screen and wrong on a full-screen place: while the
notification panel or the left page is up, **both edges belong to the system and nothing is
excluded**. See `edgesAreSystems` near the `setSystemGestureExclusionRects` call.

Symptom when it was wrong: BACK worked, the *back gesture* did not, and the only way out of
either place was HOME — which on a launcher is the screen you were already on, so it read as a
gesture that changed nothing. **A gesture that "doesn't work" on a launcher is very often a
gesture the launcher is eating; check exclusion rects before suspecting the handler.**

Also: BACK is a *key*, the back *gesture* is an edge swipe. `adb input keyevent KEYCODE_BACK`
proving BACK works proves nothing about the gesture — test with
`adb shell input swipe 2 1200 950 1200 250`.

## Still outstanding

- **Niagara parity work**, in the order suggested to the user:
  1. Notification Summary / batching (~6h digests) — the biggest gap, and every piece is
     already present because we own the notification surface.
  2. Sort by usage *within* each letter group (we have a global sort only).
  3. Locale-aware alphabet letters.
  4. Exclude hidden apps from search (Niagara has this bug too — free win).
  5. Contact search with direct call/SMS.
  6. KLWP/KWGT variable publishing.
  Already better than Niagara, do not regress: free-form widget placement (their single
  biggest complaint), app shortcuts launching, no server calls for icon categorisation,
  notification actions + inline reply, cold-start icon decode now off the main thread.

## Last few commits

- `c6e0649` Rebuild the left-hand page on Flow's placement model
- `aab96de` test: the whole of arranging, as a list, before any UI is written on it
- `ddf4aae` refactor: the placement model is Flow's - a list and a width, and nothing else
- `f65446c` revert: the free-placement page, out entirely

- `378d16f` feat: a switch is a lamp, not a mechanism

- `baa2c42` feat: a welcome on first use, a dotted wave, and the app panel follows your finger
- `2833cc7` fix: the sliders were dead, and the ball sat in the middle at 0%

- `ccb6ca7` feat: rounded switches, sliders that glow, and a wave for an icon
- `973149c` feat: free resizing is worth having again, because the space can be filled

- `933adcf` fix: a long press on a widget was also a tap

- `f1e059a` refactor: settings on true black, and the sections list given something to look at
- `6595b6d` feat: the page reads as its own place, and a long press arranges a widget on it
- `0626cc2` revert: put the page on the left back the way it was
- `c842812` fix: give the sideways swipe on a row back to the row
- `26132f8` refactor: settings, rebuilt as sections with a peek at what each one changes
- `50eb36a` fix: the home screen blurs itself, and the page can barely be reached
- `fe27bb8` feat: the left page is a real page, not a gesture in a gap  ← the pager, reverted

## Known device quirks

- **Reaching Ala's own settings:** long-press empty home space → a menu appears with
  "Ala Launcher settings". There is no intent filter for it and the app-list entry with that
  name is the **system** Settings app, which is a different app and an easy wrong turn.
- `adb shell input swipe x y x y 900` is how to long-press.
- The corner button in the lower right is bound to Google here, so a blind tap at ~(900,2200)
  opens Google and looks like a launcher bug.
- The app-list search field can grab focus and bring up the IME after some swipes, which
  makes `uiautomator dump` come back empty. Not a crash; re-dump.
- A swipe below the favourites genuinely launches an app (that is quick-launch working).
- `am start` often reports "Activity not started, intent has been delivered to currently
  running top-most instance". Verify with `dumpsys activity activities | topResumedActivity`
  if a cold start is being tested.
- **DataStore is `files/datastore/victoria_prefs.preferences_pb`** (not `victoria.preferences_pb`
  — that name is wrong and reading it returns garbage). Use `run-as … base64` then
  `[Convert]::FromBase64String`; the raw dump is protobuf, so strings and JSON need
  `[^ -]` stripped before reading.

## The left-hand widget page, rebuilt (`c6e0649`)

On Flow's model, which is an **ordered list plus one size per entry**. Read out of Flow 40.1.6:
`WidgetModel(widgetId: Int, dimensionsInLine: Int?, habitId: Long)`. Searching that package
for row/column/page/x/y/left/top/cellIndex returns nothing - no coordinates, so no collision and
nothing to resolve a drop against. Every one of the removed page's six faults lived in that
machinery. What is *added* to Flow is **width**, so two narrow widgets share a row; that is the
one dimension Flow has no concept of, and it is why the line is a wrapping `FlowRow`.

- `ui/home/MinusOnePage.kt` - the page. `ui/home/WidgetReorder.kt` - the arithmetic, moved
  out of the **test** source set into main so the page can use it (15 tests stay in `WidgetReorderTest`).
- Long-press an entry to arrange it: centre grip moves, bottom/right/corner resize, cross removes.
- `WidgetReorder.targetIndex` counts from the neighbours' **centres**, not a fixed dp step - a
  fixed step is twitchy on a short entry and sluggish on a tall one.
- `onResize`/`onMove`/`onRemove` all go through `updateEntries`, which **re-reads** the
  stored list inside the coroutine. A drag writes several times in a few hundred ms; applying
  each to the snapshot the page opened with meant they overwrote each other and the drag stored
  no change at all. This was one of the six original faults and it is easy to reintroduce.
- Settings row restored. It was deleted with the page in `f65446c`, which made the mode
  impossible to turn on - the page was unreachable and looked broken rather than absent.

### Verified on device

- Gap swipe across empty home space opens the page (y=800, 200 -> 900, 400ms).
- Empty state renders; add FAB is at `[899,2195]-[1032,2328]`, centre **(965, 2261)**.
- BACK closes the page and stays in the launcher. BACK while arranging ends the arrangement
  first (registered later, so it wins).

### Not yet verified - the phone disconnected

The picker from the add button, two entries sharing a row, drag to reorder, the resize handles.
Treat those as untested. The fixes are in; nothing proves them yet.

## Two traps hit while doing this

1. **An always-composed full-screen overlay eats every touch under it, even at alpha 0.** The
   overlay had `.pointerInput` for swipe-to-dismiss and was composed while hidden; its handler
   took the down and HomeScreen's gap handler **never started at all**. Logging which handler
   ran is what found it - reasoning about Compose hit-testing did not. Now `if (minusOneVisible)`.
   Same rule already applies to the notification shade.
2. **The FAB set a flag and launched nothing.** `onAddWidgetToMinusOne` only assigned
   `minusOneWidgetPicker = true`; it now also calls `widgetActions.onAddWidget(null)`, which
   is the same launch the home screen's add button uses. One flag is not a launcher.

## Hand-editing the DataStore (works, now known)

The device had **no** `minus_one_mode` stored at all, so the default `OFF` applied and the
page was unreachable. Wrote the key by hand. Shape, confirmed by the file's own existing keys:

    0a <n>                      preferences entry
    0a <klen> <key bytes>       KeyValuePair.key
    12 <vlen>                   KeyValuePair.value  (a nested Value message)
      <field tag> <len> <bytes> Value: 1=bool 08, 3=integer 18, 5=string 2a

`12 02 08 01` is how an existing `true` looks, which is what gives the numbering away.
Using **6** for string (the oneof-in-KeyValuePair numbering) corrupts the file and the app
crashes on every launch in `PreferencesProto.PreferenceMap.parseFrom`. Original bytes saved at
`C:\Users\udayk\AppData\Local\Temp\opencode\vprefs_orig.b64` if they want it reverted.
To write: `adb push` to `/data/local/tmp`, then `run-as ... cp`, then `rm`.

## Standing rules reaffirmed

- Diagnose with device evidence, not reasoning. Both bugs above were found by logging which
  handler ran, after reasoning had been confidently wrong twice.
- `WidgetReorderTest`: three moves 0->4, 4->2, 2->0 compose back to `[a,b,c,d,e]`. Two
  hand-computed expectations in it were wrong; the code was right.
- The gap-swipe test coordinate must come from content bounds, not a fixed y - and with no
  favourites and no widget there is no gap at all, so probe several y values.


### Widget page: fully verified on device (all of it)

| What | Evidence |
|---|---|
| Gap swipe opens the page | swipe 200,600 -> 950,600 400ms |
| Add button -> picker | user added 3 widgets themselves; ids 82, 83, 87 |
| Long press -> arrange | shows `429 x 253 dp` and the remove cross |
| Drag to reorder | `[82,83,87]` -> `[83,82,87]` on disk and on screen |
| Width handle | right edge; `w:256` -> `x 38..646` exactly 256dp |
| Height handle | bottom edge; `h:253 -> 289`, entry below pushed down |
| Corner handle | both at once; `w:232->424, h:289->127` |
| Packing | 169dp + 232dp sat **side by side** at x 38..439 and 458..1009, |
| | the 8dp gap between them, third wrapped to the next row |

Coordinates on this device: empty band is **y 447..766** (between widget and first
row). A-Z strip is x>=1037. FAB centre **965, 2261**. Right-edge handle centre is
`right - 40px`; bottom handle is `bottom - 40px`; corner is both.

## DO NOT hand-edit the DataStore again

I broke the user's phone doing it. Three separate bugs, each a crash or silent
data loss, all from writing protobuf by hand:

1. `# Memory — Victoria/Ala Launcher fork  **Write this file after every message.** Keep it short, factual, and current. Delete anything that is no longer true rather than annotating it. A wrong "still true" line is worse than a missing one.  Last updated: after commit `f1e059a` (page edit mode + AMOLED settings).  ---  ## Project  - Path: `C:\Users\udayk\Documents\Default Project\victoria-launcher` - Rebranded to **Ala Launcher**, version 1.0, on 2026-10-17. - `applicationId` is **still `dev.victorialauncher`** — deliberately not renamed. It is   Android's identity for the app; changing it makes a different app and orphans the granted   notification-listener permission, the allocated widget ids, the DataStore and any backup.   `versionCode` went 107 → 108 for the same reason (a downgrade fails to install). - Upstream is `adelmonte/victoria-launcher` by **adelmonte**, GPL-3.0-or-later. Credits now   live in the About section, not only the README. - Remote to push to: `git push fork main` (fork = Torchbearer0).  ## Backups  - **`C:\Users\udayk\Desktop\Ala-Launcher-backup-2026-10-17.zip`** — 10.3 MB, 280 entries, 98   Kotlin files, made with `git archive --format=zip -o <dest> HEAD` from the repo root.   Tracked files only, so no build output and no gradle caches, and it is exactly a commit.   Check what is in one with `[System.IO.Compression.ZipFile]::OpenRead(...)`. - `NEXT-SESSION.md` no longer exists — its content was folded into this file. Nothing is lost.  ## Environment  - `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` - Build: `.\gradlew.bat assembleDebug testDebugUnitTest --offline` from the project root. - `git` is at `C:\Program Files\Git\cmd\git.exe`, not on PATH. - adb is at `C:\Users\udayk\AppData\Local\Android\Sdk\platform-tools`, not on PATH. - Device serial `963028779900070`. Screen 1080x2376, density 380. - `git`/adb sometimes need `$env:PATH` prepended per shell invocation. - 187 unit tests passing as of `0626cc2`.  ## Hard-won rules — do not relearn these  1. **Never bulk-rewrite Kotlin with PowerShell `Set-Content`/`-replace`.** It has corrupted    files here twice (once stripped a whole object body and left stray braces; once ate a KDoc    block). Use the editor tool. Reading a file's lines via `[System.IO.File]::ReadAllLines`    is fine; *writing* that way is the problem. 2. **`adb shell ... > file` corrupts binary.** PowerShell redirection mangles the bytes, so a    DataStore read comes back looking like the key is missing when it is not. Use base64:    `adb shell "run-as <pkg> base64 files/datastore/x.preferences_pb"`, then    `[Convert]::FromBase64String` after stripping whitespace. This produced a whole round of    false "NOT SET" readings before it was caught. 3. **The user is the ground truth and often right.** Twice they were right and I was wrong:    a swipe that "didn't fire" was a broken *check*, and a gesture they wanted reverted was a    gesture I had broken. Check my instrumentation before believing my own negative result. 4. **Diagnose with device evidence, not reasoning.** `adb shell uiautomator dump` + reading the    XML gives text/content-desc and node bounds — enough to drive the UI blind and to see    whether something is really on screen. 5. **A Compose gesture handler that covers a whole screen must only consume drags it actually    acts on.** `detectHorizontalDragGestures` consumes at slop *regardless of direction*, so    using it for "only if eligible" steals gestures from everything above it. This bit three    times: the quick-launch detector, and then the -1 page's own swipe-to-dismiss, which both    consumed every move it saw and treated any 72dp sideways travel as "dismiss". 6. **Measure drags by absolute position, not `positionChange()`** when the down may already be    consumed — `positionChange()` returns 0 for a consumed change, so travel always reads 0. 7. **A hosted widget is an Android view handling its own touch stream.** Any `pointerInput`    wrapped around it waits for an unconsumed drag it is never given. Gestures over a widget    have to be read inside `LongPressFrameLayout`. 8. **An Android `ViewGroup.onInterceptTouchEvent` is not called again after it returns true.**    Everything after that arrives at `onTouchEvent`. A drag handler written only in the intercept    pass therefore fires exactly once, with a slop-sized delta, and then goes quiet. Read the    drag in **both** passes. 9. **`onSizeChanged` measures where it is attached, not what the content got.** Attached above    a `padding`, it reports the padded size and any arithmetic done from it produces something    that does not fit. Attach it below the padding. 10. **A clickable Compose parent merges its children's semantics**, which is why a `contentDescription`     on a child `Icon` never reaches the accessibility tree — and therefore never reaches     `uiautomator dump` either. Put the label on the clickable box itself via     `.semantics { contentDescription = ... }`. 11. **No `INTERNET` permission, ever.** Deliberate. This is why the Discover page hands off to    the Google app instead of embedding a feed — a WebView would need the permission. 12. **Never change a background colour without changing the content colour with it.** Making the    settings page true black while leaving the content on the system scheme gave a light-mode    device near-black text on a black page. Measure it, don't eyeball it — see below. 13. **`AndroidView` builds its view once, in the factory, and never again.** Wrap it in    `key(widgetId)` or a list that reorders will keep every slot drawing its old content. This    is why the cross used to remove a widget that wasn't the one under the finger. 14. **To check colours on device without being able to see the screen:** `adb shell screencap -p`,    `adb pull`, then `System.Drawing.Bitmap` + `GetPixel` in PowerShell. Sample the bounds of a    text node from a `uiautomator` dump and report min/max luminance — that gives a real    contrast ratio instead of an opinion. (Never `adb ... > file` for binary; `pull` is fine.)    Beware: a scan can land on the app bar and read as "the effect is missing" when it is simply    under an opaque header. Scan a column and look at the falloff, not a single point. 15. **Anything drawn *behind* a `Scaffold` needs the Scaffold's container to be transparent.**    An opaque `containerColor` is a wall, and whatever the box underneath drew is invisible. 16. **The stock widget picker needs a press-hold-then-drag**, which `adb input swipe` cannot    produce (it interpolates from the first move, so there is no hold). Don't burn turns trying    to add a second widget to test reordering — test the logic as a pure function instead. 17. **`adb input swipe x y x y 900` is a long press.** And remember a widget's real bounds from    the dump before pressing it — pressing at a stale coordinate lands outside the widget and    silently does nothing, which reads as a broken gesture. 18. **`onInterceptTouchEvent` is NOT called for MOVE once a child has the gesture.** A ViewGroup    only consults it for the press and while it has no touch target. To see a drag that a child    is competing for, override **`dispatchTouchEvent`** — it is called for every event that    reaches the view, whichever child handles it. This cost three rounds of dead-end debugging. 19. **Claim the gesture from ACTION_DOWN, not from your own slop.** If you ignore a drag until    it clears your threshold, a scrolling ancestor sees the same threshold cross first and takes    the gesture. You then get exactly one MOVE. Symptom: the gesture "half works". 20. **Never write state from a captured composition snapshot.** `updateEntries` applied each    change to the list as it was at composition time, so several writes in a few hundred ms    overwrote each other and the last won with stale data. Read the current value **inside the    coroutine** (`app.prefs.flow.first()`). Symptom: the UI changes and nothing persists. 21. **NEVER bulk-edit Kotlin from PowerShell, not even "just a few exact token renames".** I    did it once for 10 `Checkbox(`→`GlowingCheckbox(` lines and it happened to be fine — but    only because every `String.Contains` was checked first. Use the editor tool. 22. **`SwitchDefaults.colors` / `CheckboxDefaults.colors` do not all take the same parameters.**    `SwitchDefaults` has no `uncheckedBorderColor`; `CheckboxDefaults` has no    `uncheckedBorderColor` either (only `disabledBorderColor`). Drop the param rather than    guessing again. 23. **Material3 controls do not appear in a `uiautomator` dump as their platform class.**    A Compose `Switch` is not an `android.widget.Switch` node. To check one exists and is    coloured, screencap and count accent pixels — the pixel-sampling recipe above.  ## The page to the left of home - REMOVED  **Gone, commit 65446c.** Do not try to restore or rebuild it. It went through six attempts (stale AndroidView, one-way drag, writes to a stale snapshot, the gesture read from the wrong callback, only one MOVE arriving, overlap on drop) and never worked well enough to keep working on. The user asked for it to be taken out.  MinusOnePage.kt, its two test files, the overlay, the gap-band swipe, both BackHandlers, the minusOneVisible blur, the picker branch and the settings row are all deleted. data/MinusOneEntry.kt and the minus_one_* preferences were **left in place on purpose** - nothing reads or writes them, so they are inert, and deleting a user's stored settings for no gain is worse than leaving them.  Still there and unrelated: the home screen's own widget slot, the row swipe that opens an app's panel, and the notification panel with a working back-gesture. ## Settings  - Page background is **true black** (`lerp(Color.Black, surface, dimPreviewReveal)`) — the   wallpaper returns only while the dim setting is being previewed. - The whole `settings` nav route is wrapped in `MaterialTheme(colorScheme = amoledColorScheme())`   (`ui/theme/Theme.kt`). **Scoped to the route, not inside `SettingsScreen`**, because the screen   is a ~180-parameter entry point and re-indenting its body to wrap it in a theme is exactly the   kind of edit that corrupts the file. `VictoriaNavHost.kt` `composable("settings")`. - Cards are `SettingsTone.card` = `#0B0B0E` with a 1dp `SettingsTone.edge` hairline. A card on   black cannot separate itself by being lighter; the border is what draws it. - The three tone values live together in `SettingsTone` in `SettingsComponents.kt` on purpose —   they are one idea and drift apart if they are written at each use. - The sections list is **one card containing 8 rows**, each with a coloured mark at its left.   `SettingsRow` grew a `leading` slot for it. - Measured: page `0,0,0`, card `0B0B0E`, section label luminance 11→231 ≈ **16:1 contrast**. - **Sailfish pass — one accent for the whole app.** `ui/theme/Glow.kt` holds `GlowAccent`   (`#6FE3F5`) and every lit control: `GlowingSwitch`, `GlowingCheckbox`, `GlowingRadioButton`,   `GlowingSlider`, `GlowingRangeSlider`, `GlowingFilterChip`, `GlowingSegment` /   `GlowingSegmentTrack`. **All of them are in the app except the last four** — those exist so   the next screen that needs one does not invent its own.   - `GlowHalo()` is the shared halo: a `Brush.radialGradient` drawn at ~2× its slot, **never a     blur** (no app-owned view for a RenderEffect; a per-frame one is wrong for a scrolling list).   - `SettingsTone.accent` now just aliases `GlowAccent`, so settings code reads naturally and     there is one value to change.   - **Unticked controls are rings, not gaps** — they go near-black but keep a faint outline. A     switch that vanishes when off is a switch you cannot find.   - **Slider thumb is white, track is accent.** An accented thumb reads as part of the track. - Measured on device: settings switch column 7.56% accent, favourites checkbox column 1.18%   (a small square yields less than a switch pill), sections-list mark column 4.91%. - The page has one soft radial light behind the marks, falling off to true black by the bottom   of the list. It only shows because **`Scaffold(containerColor = Color.Transparent)`** and the   page colour is painted by the outer `Box` — an opaque container is a wall. - Cards get a 1dp horizontal-gradient rim on their top edge so a flat panel reads as raised. - Measured: light from `(18,26,28)` under the app bar to `(0,0,0)` lower down; cards `0B0B0E`;   labels still 11→231.  ## Still outstanding  ### Niagara parity — status, checked 2026-10-17  | Feature | Status | |---|---| | **Hidden apps in search** | **ALREADY DONE.** `appListSearchHidden` in `Prefs.kt` defaults to **`false`**, so hidden apps are excluded from search by default and there is a setting to opt in (`settings_search_hidden`, App list section). **Not a Niagara gap for us — do not "implement" it.** | | Usage Breaker (nudge at 5/10/20…240 min) | not started | | Contact search with direct call/SMS | not started | | Notification Summary / batching | not started |  ### The -1 page is still called "very very buggy" by the user  I have fixed six separate faults on it blind (stale `AndroidView`, one-way drag, writes to a stale snapshot, gesture read from the wrong callback, one MOVE only, overlap on drop) and each time they found something new. **Do not keep guessing.** Ask which gesture they just performed and what they expected, one at a time, and reproduce that exact case before changing anything.  ## Gesture exclusion — the back-swipe bug  `HomeRoute` spends Android's 200dp-per-side back-gesture exclusion budget on the **A-Z scrub band**. That is right on the home screen and wrong on a full-screen place: while the notification panel or the left page is up, **both edges belong to the system and nothing is excluded**. See `edgesAreSystems` near the `setSystemGestureExclusionRects` call.  Symptom when it was wrong: BACK worked, the *back gesture* did not, and the only way out of either place was HOME — which on a launcher is the screen you were already on, so it read as a gesture that changed nothing. **A gesture that "doesn't work" on a launcher is very often a gesture the launcher is eating; check exclusion rects before suspecting the handler.**  Also: BACK is a *key*, the back *gesture* is an edge swipe. `adb input keyevent KEYCODE_BACK` proving BACK works proves nothing about the gesture — test with `adb shell input swipe 2 1200 950 1200 250`.  ## Still outstanding  - **Niagara parity work**, in the order suggested to the user:   1. Notification Summary / batching (~6h digests) — the biggest gap, and every piece is      already present because we own the notification surface.   2. Sort by usage *within* each letter group (we have a global sort only).   3. Locale-aware alphabet letters.   4. Exclude hidden apps from search (Niagara has this bug too — free win).   5. Contact search with direct call/SMS.   6. KLWP/KWGT variable publishing.   Already better than Niagara, do not regress: free-form widget placement (their single   biggest complaint), app shortcuts launching, no server calls for icon categorisation,   notification actions + inline reply, cold-start icon decode now off the main thread.  ## Last few commits  - `c6e0649` Rebuild the left-hand page on Flow's placement model - `aab96de` test: the whole of arranging, as a list, before any UI is written on it - `ddf4aae` refactor: the placement model is Flow's - a list and a width, and nothing else - `f65446c` revert: the free-placement page, out entirely  - `933adcf` fix: a long press on a widget was also a tap - `c6e0649` Rebuild the left-hand page on Flow's placement model - `aab96de` test: the whole of arranging, as a list, before any UI is written on it - `ddf4aae` refactor: the placement model is Flow's - a list and a width, and nothing else - `f65446c` revert: the free-placement page, out entirely  - `f1e059a` refactor: settings on true black, and the sections list given something to look at - `6595b6d` feat: the page reads as its own place, and a long press arranges a widget on it - `0626cc2` revert: put the page on the left back the way it was - `c842812` fix: give the sideways swipe on a row back to the row - `26132f8` refactor: settings, rebuilt as sections with a peek at what each one changes - `50eb36a` fix: the home screen blurs itself, and the page can barely be reached - `fe27bb8` feat: the left page is a real page, not a gesture in a gap  ← the pager, reverted  ## Known device quirks  - **Reaching Ala's own settings:** long-press empty home space → a menu appears with   "Ala Launcher settings". There is no intent filter for it and the app-list entry with that   name is the **system** Settings app, which is a different app and an easy wrong turn. - `adb shell input swipe x y x y 900` is how to long-press. - The corner button in the lower right is bound to Google here, so a blind tap at ~(900,2200)   opens Google and looks like a launcher bug. - The app-list search field can grab focus and bring up the IME after some swipes, which   makes `uiautomator dump` come back empty. Not a crash; re-dump. - A swipe below the favourites genuinely launches an app (that is quick-launch working). - `am start` often reports "Activity not started, intent has been delivered to currently   running top-most instance". Verify with `dumpsys activity activities | topResumedActivity`   if a cold start is being tested. - **DataStore is `files/datastore/victoria_prefs.preferences_pb`** (not `victoria.preferences_pb`   — that name is wrong and reading it returns garbage). Use `run-as … base64` then   `[Convert]::FromBase64String`; the raw dump is protobuf, so strings and JSON need   `[^ -]` stripped before reading.  ## The left-hand widget page, rebuilt (`c6e0649`)  On Flow's model, which is an **ordered list plus one size per entry**. Read out of Flow 40.1.6: `WidgetModel(widgetId: Int, dimensionsInLine: Int?, habitId: Long)`. Searching that package for row/column/page/x/y/left/top/cellIndex returns nothing - no coordinates, so no collision and nothing to resolve a drop against. Every one of the removed page's six faults lived in that machinery. What is *added* to Flow is **width**, so two narrow widgets share a row; that is the one dimension Flow has no concept of, and it is why the line is a wrapping `FlowRow`.  - `ui/home/MinusOnePage.kt` - the page. `ui/home/WidgetReorder.kt` - the arithmetic, moved   out of the **test** source set into main so the page can use it (15 tests stay in `WidgetReorderTest`). - Long-press an entry to arrange it: centre grip moves, bottom/right/corner resize, cross removes. - `WidgetReorder.targetIndex` counts from the neighbours' **centres**, not a fixed dp step - a   fixed step is twitchy on a short entry and sluggish on a tall one. - `onResize`/`onMove`/`onRemove` all go through `updateEntries`, which **re-reads** the   stored list inside the coroutine. A drag writes several times in a few hundred ms; applying   each to the snapshot the page opened with meant they overwrote each other and the drag stored   no change at all. This was one of the six original faults and it is easy to reintroduce. - Settings row restored. It was deleted with the page in `f65446c`, which made the mode   impossible to turn on - the page was unreachable and looked broken rather than absent.  ### Verified on device  - Gap swipe across empty home space opens the page (y=800, 200 -> 900, 400ms). - Empty state renders; add FAB is at `[899,2195]-[1032,2328]`, centre **(965, 2261)**. - BACK closes the page and stays in the launcher. BACK while arranging ends the arrangement   first (registered later, so it wins).  ### Not yet verified - the phone disconnected  The picker from the add button, two entries sharing a row, drag to reorder, the resize handles. Treat those as untested. The fixes are in; nothing proves them yet.  ## Two traps hit while doing this  1. **An always-composed full-screen overlay eats every touch under it, even at alpha 0.** The    overlay had `.pointerInput` for swipe-to-dismiss and was composed while hidden; its handler    took the down and HomeScreen's gap handler **never started at all**. Logging which handler    ran is what found it - reasoning about Compose hit-testing did not. Now `if (minusOneVisible)`.    Same rule already applies to the notification shade. 2. **The FAB set a flag and launched nothing.** `onAddWidgetToMinusOne` only assigned    `minusOneWidgetPicker = true`; it now also calls `widgetActions.onAddWidget(null)`, which    is the same launch the home screen's add button uses. One flag is not a launcher.  ## Hand-editing the DataStore (works, now known)  The device had **no** `minus_one_mode` stored at all, so the default `OFF` applied and the page was unreachable. Wrote the key by hand. Shape, confirmed by the file's own existing keys:      0a <n>                      preferences entry     0a <klen> <key bytes>       KeyValuePair.key     12 <vlen>                   KeyValuePair.value  (a nested Value message)       <field tag> <len> <bytes> Value: 1=bool 08, 3=integer 18, 5=string 2a  `12 02 08 01` is how an existing `true` looks, which is what gives the numbering away. Using **6** for string (the oneof-in-KeyValuePair numbering) corrupts the file and the app crashes on every launch in `PreferencesProto.PreferenceMap.parseFrom`. Original bytes saved at `C:\Users\udayk\AppData\Local\Temp\opencode\vprefs_orig.b64` if they want it reverted. To write: `adb push` to `/data/local/tmp`, then `run-as ... cp`, then `rm`.  ## Standing rules reaffirmed  - Diagnose with device evidence, not reasoning. Both bugs above were found by logging which   handler ran, after reasoning had been confidently wrong twice. - `WidgetReorderTest`: three moves 0->4, 4->2, 2->0 compose back to `[a,b,c,d,e]`. Two   hand-computed expectations in it were wrong; the code was right. - The gap-swipe test coordinate must come from content bounds, not a fixed y - and with no   favourites and no widget there is no gap at all, so probe several y values.  += @(...) | Out-Null` - piping an **assignment** to Out-Null discards it,
   so every entry was written as a bare `0a` with no length. Crash loop:
   `CorruptionException -> InvalidWireTypeException`.
2. Inverted tag and length. Protobuf is **tag, then length, then payload**.
3. Off-by-one when slicing the key: wrote `\\x11minus_one_entrie` (17 bytes) instead
   of `minus_one_entries`. Parses fine, so no crash - the app just reads an empty
   list and the page looks empty with no explanation.

Rules if it ever has to be done again:

- **Parse-validate before pushing.** Walk every top-level entry with a real LEB
  reader and require the walk to consume the file exactly, with no leftovers.
  This caught bugs 2 and 3 immediately and would have caught 1.
- Assert the literal key name is present in the result, not just that a frame parses.
- **Never** replace a live app's DataStore while the process is running: DataStore
  caches the file once read and the running process will serve stale data and then
  overwrite your edit. Kill it properly first - see below.
- Save a byte-exact backup before touching it, and keep it until the app has run clean.

### am force-stop does not stop the launcher

It is the default home app, so the system restarts it within a second: `pidof`
returns a **new** pid right after, and `am start` says "delivered to currently
running top-most instance". It looked like a cold start and was not, which is
why an edit appeared to have no effect. Use `am kill-all` then `am start -S`,
and check the pid actually changed.

Setting the default home again after it gets lost: `cmd package set-home-activity
--user 0 dev.victorialauncher/.MainActivity` reports Success but the system can
still show a chooser - check `cmd role get-role-holders android.app.role.HOME`
and `cmd package resolve-activity --user 0 --brief -a android.intent.action.MAIN
-c android.intent.category.HOME`, which should end with `isDefault=true`.


## The page can be filled now (`973149c`)

WidgetPageFill.firstGap mirrors FlowRow's line breaking to find the first hole wide enough
for a widget, and the add button puts one there at exactly that size. Verified on device: a
251dp widget in a 422dp row reports `163 dp free`, and the new one lands beside it at
x 38..634 and x 653..1040 - same row, 8dp between.

**Two bugs, and the second is the one to remember.**

1. The gap loop only ran while a *following* entry was considered, so the **last line** never
   reported one - and the last line is exactly what a resized widget leaves behind.
2. `leftover = container - lineWidth` was 8dp too wide. A widget filling a gap still has to be
   *placed* there, and placing costs the inter-item gap: 251 + 8 + 171 = 430 in a 422 row. The
   page said 171 free, offered a 171dp widget, and could not lay it out. **The caption and the
   arithmetic agreed with each other and were both wrong, so nothing that compared them caught
   it** - and my hand-computed test expectations were off in the same direction, so 220 tests
   passed it too. Fixed to `container - lineWidth - gapDp`.

That is the third time now that the tests and the code have been wrong in the *same* direction
and agreed with each other. They cannot both be the check. Only the device caught this one.

- The caption sits **under** the list, not in it. A placeholder item would occupy the very room
  it is describing and would stop later entries fitting where they used to.
- Settings: the page is a switch now. Discover is gone from the enum - it never drew anything of
  ours, it opened Google, and a launcher setting that launches another app is a redirect.

## Visual changes (`ccb6ca7`)

- Switch/checkbox are **drawn**, not Material's. Material3 1.3 took the shape parameters off
  `Switch` and left the pill as the only option, so there is nothing to round without replacing
  it. Off is grey with no glow and **no accent anywhere** - the old one kept a faintly cyan
  border, so unticked switches were still slightly lit.
- Slider: the lit half carries its own halo clipped to the filled width; the handle is a ball.
  Material's slider is still underneath, invisible, for drag/accessibility/steps.
- App icon: blue gradient background, two wave strokes, plus a **monochrome** layer for themed
  icons on Android 13. All four resources confirmed present in the APK.

## Swiping on this device - where things actually are

The user has customised the **bottom** strip for left/right app swipes. Do not swipe there.
The widget page opens only in the band **between the widget section and the favourites**:

    gap y = (lowest widget bottom + first favourite top) / 2

- Find the widget bottom from `AppWidgetHostView` bounds.
- Find the first favourite top from a node with **x < 400** below that. The A-Z strip sits at
  x >= 1037 and its letters will otherwise be mistaken for a favourite row and give a 30px
  band that cannot be hit.
- Swipe **<= 350ms**. At 500ms the background long-press fires instead and opens the context
  menu (Edit layout / Add widget / Choose favorites / Ala Launcher settings).
- The home screen takes ~10s to settle after widgets change, and uiautomator catches it
  half-loaded. Dump three times with no input and only trust the last two if they agree.


## NEVER draw a control over another one and expect them to agree (`2833cc7`)

The slider regression is the most instructive bug in this project and the whole lesson is one
sentence: **a Box lays its children out TopStart unless told otherwise, and a child that is not
told to fill picks its own width.** Three things went wrong at once and none of them was visible
in the source:

- The custom track drew along the top of the box while Material's thumb drew on the centre line
  below it. Looked like a plain line with a dot on a line of its own.
- The Slider was not `fillMaxWidth`, so it laid out at its own width while the track beside it
  spanned the row. Its gesture mapping measured a different rectangle from the one being looked
  at, so touching the handle threw the value to wherever the finger landed.
- The thumb slot's size is what Material maps the value onto, so a slot sized for the glow
  instead of the ball quietly moved **both ends of every setting's range**.

Fixed by not using Material's slider at all (~40 lines). The rule now: **when you draw your own
track, you own the slider** - do not overlay somebody else's and hope the two agree.

### How it was actually diagnosed

By pixel sampling, not by reading the code: screenshot via `screencap -p` then base64 (never
`> file`, it corrupts the PNG), and finding the ball as the **longest contiguous run of bright
pixels per row**. That is what proved the ball was pinned dead centre at 0% - the giveaway the
user spotted and I had not.

- Beware: `{0},{1}` -f [int](/16)*16, ... is a PowerShell precedence trap. The cast binds
  tighter than `,` so the format gets four args for three slots and prints hundreds of errors.
  Parenthesise the whole expression, or assign to a variable first.

## Verified on device since (`baa2c42`)

| | evidence |
|---|---|
| Slider at 0% | ball at **x=98**, left end |
| Slider drag right | 82%, ball x=952 |
| Slider drag left | 5%, ball x=150 |
| App panel follows finger | swipe x=140 -> panel x=264; swipe x=520 -> panel x=644 |
| Welcome page 1 | Next at x 814..1004 y 2174..2290; wave = 5455 cyan px above headline |
| Welcome page 2 | all 7 feature lines present |
| Done | lands on home, splash gone |

`welcome_seen` is currently **false** on the device, so the splash will appear once on next
launch. Tapping Done sets it true. It was flipped by hand in the DataStore (one byte, 08 01 ->
08 00, same length so no framing changes - still validate the file before pushing).


## The light must not escape its own box (`378d16f`)

`GlowOrb` draws inside `Modifier.clipToBounds()`. That single line is the whole spill fix.

Every previous version scaled a radial gradient past its own bounds via `graphicsLayer`
`scaleX/scaleY` - which changes paint but not layout, so the glow painted freely over whatever
was next to it. On a list of ticked rows each row's light landed on the rows above and below,
and the fix everyone reaches for (make the halo smaller) only makes it less wrong.

**Clip the box and the spill is impossible**, so row spacing stops having to be negotiated
against the glow. Measured on the favourites list after the change:

| row | above | inside | below |
|---|---|---|---|
| BitChord | 0 | 1210 | 0 |
| Gmail | 0 | 1210 | 0 |
| Flow | 0 | 1210 | 0 |

Measure this the same way when a glow looks wrong again - count bright pixels in a band above,
inside and below each row. It is the only way to tell a spill from a soft edge.

### Palette

`GlowAccent` is **white** (was cyan). On AMOLED the pixels are off when black, so white is
the only colour that reads as the screen *on* rather than as paint on it. Dull when off, bright
and blooming when on, no third state.

### Two PowerShell traps that cost real time

- **Never name a function `Measure`** - it collides with the `Measure-Object` alias and every
  call silently becomes a cmdlet error. Cost me two full cycles.
- ` = @()` then `[]` is an **array**, not a hashtable, so `ContainsKey` does
  not exist. Use `@{}` for a keyed accumulator. Thousands of error lines, zero information.

## Icon and splash (both need eyes on them)

- Icon wave is now a real shape: crest line + variable-thickness body, 73 dots sized by distance
  to the nearer edge. I **cannot see it** - please say if the crest reads.
- Splash now lights bottom to top so the wave forms. `welcome_seen` is false on the device, so
  it will appear once on next launch; one tap of Done clears it.

