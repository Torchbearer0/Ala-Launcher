# Victoria Launcher

An open source alternative to [Niagara Launcher](https://niagaralauncher.app) — a
minimal, list-based Android home screen.

![Victoria Launcher](docs/banner2.png)

---

## This repository is a fork

Victoria Launcher was written by [adelmonte](https://github.com/adelmonte/victoria-launcher)
and is used here under [GPL-3.0-or-later](LICENSE). The original commits, the original
SPDX headers in every source file and the "forked from" attribution are all kept intact,
so the history here reads as what it is: someone else's launcher, extended.

Everything below the divider is upstream's own README. This section is the fork's.

### What this fork adds

**The app panel.** Swipe a home row in from the edge and it opens under that row: the
app's notifications, its shortcuts, and its widgets, in one glass panel that follows the
row you touched rather than dropping into a screen corner. Tapping a notification opens
what the app asked it to open — the message, not just the app's front page.

**The notification list.** A deep pull opens a full notification shade of our own:
grouped by app, swipe to dismiss, clear one app or all, notification actions and inline
reply where the notification offers them. Needs the notification listener permission,
which the app asks for and will explain if it is missing.

**Notification previews on app rows.** A row for an app with a notification shows the
newest one under its label — sender, how long ago, the text, and a chevron. On both the
home screen and in the A-Z drawer.

**A real A-Z strip.** Tap a letter to jump, drag to scrub through the alphabet, on the
home screen and in the drawer. Tap the edge to open the list and let go. The letters
themselves are the touch target, so the strip does not sit there swallowing taps down the
whole side of the screen.

**One screen edge, several gestures.** An optional corner button with a different target
for tap, hold and swipe-up. The edge zone that opens the drawer can sit on the left, the
right or both, and can be confined to the vertical band the letters occupy so it stops
taking touches meant for whatever shares that side.

**Folders in the list.** Off by default: with them on, folders appear in the A-Z drawer
next to the apps they contain.

**System gestures that still work.** A launcher is a full-screen app that lives under the
system's own gestures, and it is easy to swallow them by accident. Every gesture layer
here yields inside `WindowInsets.systemGestures` on whichever edge it belongs to, and the
drawer's nested scrolling leaves horizontal drags to the system. BACK works from the
edges of the drawer, which is where you reach for it.

**Menus that match the panel.** The long-press app menu and the notification panel are
drawn in the same translucent glass as the app panel, and the home screen blurs behind
the long-press menu so it stays readable on any wallpaper.

**Adding a widget from the panel.** An app's widgets are listed in its own panel. Tapping
one asks to confirm, names the variant you picked, and then adds that exact widget —
without sending you through the full device catalogue to find the row you were already
looking at.

### Building this fork

Same as upstream: JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The notification features need the notification listener granted, and the permission
cannot be set from `adb shell settings put` — it does not persist. Grant it from a shell
on a connected device:

```sh
adb shell cmd notification allow_listener dev.victorialauncher/dev.victorialauncher.media.NowPlayingListenerService 0
```

---

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/dev.victorialauncher/)

Or grab the APK from [Releases](../../releases). Both carry the same signature,
so you can move between them without uninstalling.

Once it is installed, pick Victoria Launcher under
**Settings → Apps → Default apps → Home app**.

Requires Android 8.0 (API 26) or newer.

## Getting started

The home screen starts almost empty on purpose — everything on it is put there by
you.

- **Long-press the wallpaper** for the menu: favorites, widgets, edit layout,
  settings.
- **Swipe in from either edge** for the A-Z list, then slide along the letters to
  jump to one. Tap the edge and let go to just open it.
- **Long-press any app**, on either screen, to rename it, change its icon, hide
  it, or file it in a folder.
- **Edit layout** gives you drag handles for the order and steppers for every
  gap, height and margin.

Everything else lives in Settings, which has a search box over the whole of it.

Two things are worth knowing before you go looking. Double-tapping the A-Z strip
to lock the screen needs an accessibility service, and if that toggle is greyed
out you have to allow restricted settings from App info first — Android blocks it
for anything installed outside a store. And **Export settings** is how you keep
your setup: Android's own backup deliberately skips this app, because the file
names every app you have arranged and a backup that runs without the app cannot
leave a private space out of it.

Work profiles are picked up automatically. A private space needs Android 15 or
later and Victoria set as your default home app.

## Changelog

Per-release notes live in
[fastlane/metadata/android/en-US/changelogs](fastlane/metadata/android/en-US/changelogs),
and are shown on each [GitHub release](../../releases) and on the app's F-Droid
page.

## Reporting something

One thing per issue. A ticket with nine requests in it gets one reply covering nine
things, and the eight you did not care about bury the one you did.

For a bug, say what you did, what you expected, and what happened — and which version, on
which phone. Most of what looks broken turns out to depend on a setting, so say if you
have changed any. A screenshot or a recording is worth more than any description of how
something looks or moves.

For a request, say what you are trying to do and not only the feature you have in mind.
There is often already a way, and where there is not, knowing the goal tends to change the
shape of the answer.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

Translations are very welcome — see [TRANSLATING.md](docs/TRANSLATING.md).

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, and [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together.

## License

[GPL-3.0-or-later](LICENSE)
