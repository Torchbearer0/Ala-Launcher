# Memory — Victoria/Ala Launcher fork

**Write this file after every message.** Keep it short, factual, and current. Delete
anything that is no longer true rather than annotating it. A wrong "still true" line is
worse than a missing one.

Last updated: after commit `0626cc2` (revert of the pager for the -1 page).

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
   using it for "only if eligible" steals gestures from everything above it. This bit twice.
6. **Measure drags by absolute position, not `positionChange()`** when the down may already be
   consumed — `positionChange()` returns 0 for a consumed change, so travel always reads 0.
7. **A hosted widget is an Android view handling its own touch stream.** Any `pointerInput`
   wrapped around it waits for an unconsumed drag it is never given. Gestures over a widget
   have to be read inside `LongPressFrameLayout`.
8. **No `INTERNET` permission, ever.** Deliberate. This is why the Discover page hands off to
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
- Widget entries: per-entry width **and** height, laid out in a wrapping `FlowRow` so narrow
  ones sit side by side. Resize handles live **inside** each entry (bottom edge = height, right
  edge = width, corner = both) and consume at the **Initial** pass.
- `add widget` is a circular FAB in the lower right.
- Width `0` means "fill the row" and is not written to JSON; a corner drag measures the real
  width because adding a negative delta to `0` clamps back to `0`.
- File layout: `data/MinusOneEntry.kt` (model + JSON), `ui/home/MinusOnePage.kt` (page).

## Still outstanding (user has asked for these, not started)

- **Long-press edit mode for page widgets**: corner dots/handles to resize, an X in the
  top-right to remove, and drag-from-centre to move. No system property dialogs, no manual dp
  entry.
- **Settings redesign, again** — the card version was still judged too old. Wants AMOLED-style
  blacks instead of grey. Sailfish UI is acceptable as a reference.
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

- `0626cc2` revert: put the page on the left back the way it was
- `c842812` fix: give the sideways swipe on a row back to the row
- `26132f8` refactor: settings, rebuilt as sections with a peek at what each one changes
- `50eb36a` fix: the home screen blurs itself, and the page can barely be reached
- `fe27bb8` feat: the left page is a real page, not a gesture in a gap  ← the pager, reverted
- `58af398` feat: page entries are resizable on both axes and pack side by side
- `884b8f2` refactor: rename to Ala Launcher, and credit where it came from

## Known device quirks

- The app-list search field can grab focus and bring up the IME after some swipes, which
  makes `uiautomator dump` come back empty. Not a crash; re-dump.
- A swipe below the favourites genuinely launches an app (that is quick-launch working).
  Do not mistake it for a bug.
- `am force-stop` + `am start` works, but `am start` often reports "Activity not started,
  intent has been delivered to currently running top-most instance". Verify the PID changed
  if a cold start is being tested.