# Detent nb (fork)

This fork of [Detent](https://github.com/bjarneo/detent) adds a theme that follows another computer, and a clock that opens your clock app. It installs next to the original, because it has its own application id: `app.detent.launcher.nb`.

## What is different

- **Live theme.** One extra theme, `laptop`, takes its colours from a JSON file that your computer publishes. Detent reads it while it is in front: when it starts and every 30 seconds after. The theme changes in place, and the lock screen follows when the lock screen switch is on. The last theme is kept for offline use. The theme sits on the dial after the built-in ones, and the theme step has a `laptop` chip to jump to it.
- **Clock.** Tap the clock to open the clock app that answers the alarm intent. Hold it for the setup, as before.

## The theme file

The file is Flux's `flux.theme` shape. The only parts Detent needs are `colors.background` and `colors.foreground`, as `#rrggbb`. It also reads `colors.accent` (then `blue`, `orange`, `green`, whichever has enough contrast with the background), `mode` (`dark` or `light`) and `name`.

```json
{"version": 1, "name": "smoke", "mode": "dark",
 "colors": {"background": "#111419", "foreground": "#f0f3f7", "accent": "#ffffff"}}
```

Detent sends If-None-Match with the last ETag, so an unchanged theme costs a few bytes. It ignores redirects, plain http and bodies over 64 KB. A file without a readable background and foreground never replaces the colours that are showing.

## Switching it on

The address comes from the build, not from the source, so no address is in this repository. Without one, the live theme is off and the app behaves like Detent.

```
./gradlew assembleRelease -PdetentLiveUrl=https://your-computer.example/theme.json
```

or put `detentLiveUrl=...` in `~/.gradle/gradle.properties`.

## Permission

This fork asks for the INTERNET permission. Upstream Detent has none, and says so. Here it is only used for the one https read above, and only when an address is built in. Nothing else leaves the phone.

## Tests

`./gradlew testDebugUnitTest` runs the theme parser tests.

---

<p align="center">
  <a href="docs/media/detent-promo.mp4"><img src="docs/media/home.jpg" alt="Detent on a phone with the retro-82 theme. The apps sit on a dial at the right edge of the screen." width="100%"></a>
</p>

# Detent

Detent is a home screen for Android that you turn. Your apps sit on a dial at the edge of the screen. Turn the dial to move an app into the notch. Tap the app to open it.

[Watch the 48-second video](docs/media/detent-promo.mp4) · [Download the latest APK](../../releases/latest/download/detent.apk)

## Install

You need Android 10 or later.

1. Download `detent.apk` from the [latest release](../../releases/latest).
2. Open the file on your phone.
3. If Android asks, allow your browser or file manager to install apps.
4. Open Detent and follow the setup.
5. On the `ready` step, tap `set as home app`.

To install from a computer, connect the phone with USB debugging on and run this command:

```sh
adb install detent.apk
```

## Use the dial

| Action | Result |
| --- | --- |
| Drag or flick the dial | The dial turns. Each app clicks into the notch. |
| Tap the app in the notch | The app opens. Its name lifts off the dial. |
| Tap a different app | The dial turns to that app. |
| Hold the dial | Letters replace the apps. Slide to a letter and release to jump to it. |
| Hold the app in the notch | A menu shows the app shortcuts, `app info`, and `remove from dial`. |
| Hold the clock or an empty area | The setup opens: shape, theme, apps, and feel. |
| Tap `search` | Search all your apps. Tap `+ dial` to add an app to the dial. |

With a hardware keyboard, `j` and `k` turn the dial. `l` or `Enter` opens the app. `/` opens search. `Esc` goes back.

<p>
  <img src="docs/media/letter.jpg" alt="Hold the dial to jump by letter." width="49%">
  <img src="docs/media/menu.jpg" alt="Hold an app to see its shortcuts." width="49%">
</p>
<p>
  <img src="docs/media/themes.jpg" alt="Turn the dial to try a theme." width="49%">
  <img src="docs/media/search.jpg" alt="Search all apps and add one to the dial." width="49%">
</p>

## Shapes and themes

Detent has 5 radial layouts. Each name follows the curve of the dial.

| Layout | Look |
| --- | --- |
| `soft · radial · double` | The default. Dots swell toward the notch, with a fainter inner arc. |
| `round · radial · double` | The same dots on a tighter curve. |
| `soft · radial · ticks` | Hairline ticks on a thin rim. |
| `flat · radial · dots` | A dot matrix on a wide curve. |
| `flat · radial · nodes` | Small nodes on a wide curve. |

The dial can sit on the right or the left edge.

Detent has 27 themes. The 4 phone themes are dusk, ember, signal, and paper. They use a tinted dot-hand art. The 23 Omarchy themes each bring their own wallpaper. On the theme step, the colours change on each click of the dial. The wallpaper loads when the dial stops.

### Use your own wallpaper

Your photo replaces the theme wallpaper. The theme still sets the colours.

1. Hold the clock to open the setup.
2. Tap `next` to go to the theme step.
3. Tap `your wallpaper` and pick a photo.
4. Turn the dial to pick the colours.
5. Tap `next` until you see `done`.
6. Tap `done`.

Detent crops the centre of the photo to the shape of your screen and keeps a copy. It does not need access to your other photos. To go back to the theme wallpaper, tap `theme wallpaper` on the same step. If the `lock screen` switch is on, the lock screen uses your photo too.

## Settings

The `feel` step has 4 switches:

| Switch | What it does |
| --- | --- |
| `sound` | Plays a short click for each app that passes the notch. The phone mutes it in silent or vibrate mode. |
| `haptics` | Gives a light tick for each app and a firmer tick for each letter. It follows the system touch feedback setting. |
| `dots` | Shows an accent dot on apps with new notifications. The dot clears when you open the app. This switch needs notification access. |
| `lock screen` | Sets the lock screen and the system wallpaper to the theme. Android then takes the lock screen colours from it. |

If Android shows "Restricted setting" when you allow notification access, open the app info for Detent. Tap ⋮, then `Allow restricted settings`. Then try again.

## Permissions

Detent does not have the internet permission. It does not send data anywhere.

| Permission | Why Detent needs it |
| --- | --- |
| `QUERY_ALL_PACKAGES` | To list all the apps you can open and to find your default apps for the first dial. |
| `VIBRATE` | For haptics. |
| `SET_WALLPAPER` | For the `lock screen` switch. |
| Notification access | Optional. For unread dots. Detent reads only which apps have notifications and when they arrived. It does not keep the notification text. |

## Build from source

You need JDK 17 or later and the Android SDK with platform 36.

To build the APK and install it on a connected phone, run these commands in the repository folder:

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without the signing variables, the build uses your local debug key.

## Release

The `build` workflow builds the APK on each push and pull request. You can download that APK from the workflow run.

To publish a release, push a version tag:

```sh
git tag v0.2.0
git push origin v0.2.0
```

The workflow then builds a signed APK and attaches `detent.apk` to a GitHub release.

The workflow signs releases with these repository secrets:

| Secret | Value |
| --- | --- |
| `DETENT_KEYSTORE_BASE64` | The release keystore, encoded as base64. |
| `DETENT_KEYSTORE_PASSWORD` | The keystore password. |
| `DETENT_KEY_ALIAS` | The key alias. |
| `DETENT_KEY_PASSWORD` | The key password. |

To sign a local build with the same key, set the variables `DETENT_KEYSTORE`, `DETENT_KEYSTORE_PASSWORD`, `DETENT_KEY_ALIAS`, and `DETENT_KEY_PASSWORD`. `DETENT_KEYSTORE` is the path to the keystore file.

Caution: if your phone has a build signed with a different key, Android refuses the update. Uninstall Detent first. This removes your Detent setup.

## Credits

- The wallpapers and the dot-hand art come from the [Omarchy](https://github.com/basecamp/omarchy) themes.
- The font is [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), under the SIL Open Font License 1.1. See [`licenses/JetBrainsMono-OFL.txt`](licenses/JetBrainsMono-OFL.txt).

## License

MIT. See [`LICENSE`](LICENSE).
