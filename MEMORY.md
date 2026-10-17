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

## Current state of the -1 page (the page to the left of home)

- Access: **swipe right through the empty band between the widget and the first row.** The band
  is measured from where the widget ends and the first row starts, so it follows the padding.
- It is an **overlay**, not a pager page. A pager was tried (commits `fe27bb8`..`c842812`) and
  **reverted** because it broke two gestures the user needs and glitched the home screen.
  Do not re-introduce a pager here without asking.
- **Never** take the sideways swipe from a favourite row (that is the app panel) or from below
  the favourites (that is the quick-launch slot). These three share one axis; the gap band is
  the only place the page may have it.
- Discover mode opens the **Google app automatically** on the swipe; no prompt/button.
- The page has **its own gradient surface** and the home screen **blurs while it is showing**.
  A launcher cannot blur the wallpaper (it is the window background, not an app-owned view), so
  the scrim is what carries the separation.
- **Long press an entry to arrange it.** Then:
  - handles appear only on that entry (bottom = height, right = width, corner = both),
  - a drag on its middle moves it past a neighbour, one step per 40dp of travel,
  - a **cross** in the corner removes it, a **dot** beside it opens widget settings / app info,
  - the long press no longer opens a menu; the dp **slider dialog is deleted**, not just hidden.
- Entries pack side by side in a `FlowRow`. While something is edited, a leftover gap of 96dp or
  more shows a **plus** that opens the widget picker *there*. The plus is a **sibling** of the
  entry in the FlowRow — as a child it is clamped to the entry's width and lands on the widget.
- Width `0` means "fill the row" and is not written to JSON.
- File layout: `data/MinusOneEntry.kt` (model + JSON), `ui/home/MinusOnePage.kt` (page).

## Settings

- Page background is **true black** (`lerp(Color.Black, surface, dimPreviewReveal)`) — the
  wallpaper returns only while the dim setting is being previewed.
- Cards are `SettingsTone.card` = `#0B0B0E` with a 1dp `SettingsTone.edge` hairline. A card on
  black cannot separate itself by being lighter; the border is what draws it.
- The three tone values live together in `SettingsTone` in `SettingsComponents.kt` on purpose —
  they are one idea and drift apart if they are written at each use.
- The sections list is **one card containing 8 rows**, each with a coloured mark at its left.
  `SettingsRow` grew a `leading` slot for it.

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