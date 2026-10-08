# Yellow and charcoal app theme

The screenshots in `before/` were captured from the installed Android app on
the connected SM-G975F. Dashboard, profiles, battery/power details, and the
charge editor were uploaded to Google Stitch, along with the new brand preview.

Stitch project: `8703406586496560780`.

`stitch-uploads.json` records the uploaded screen IDs. `stitch-request.json`
contains the actual redesign request. `stitch-screens.json` records the returned
screens and downloadable assets. The edit request exceeded the HTTP timeout;
the finished designs were recovered by reading the project and its screens.
The generation was not repeated.

## Stitch designs

- `stitch/dashboard-dark.png` / `.html`
- `stitch/profiles-dark.png` / `.html`
- `stitch/battery-dark.png` / `.html`
- `stitch/dashboard-light.png` / `.html`
- `preview-stitch.png`: overview of all four proposed screens

These are Stitch proposals, not screenshots of the modified native app.

## Native adaptation

The existing Android layouts and functionality are retained. The shared
palette, component styles, active profile badge, primary profile action,
navigation selection, battery ring, editor controls, and dialog actions now
follow the yellow/charcoal visual language.

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#F7F6F0` | `#171A1F` |
| Surface | `#FFFFFF` | `#20242B` |
| Surface variant | `#F0EEE6` | `#292E37` |
| Primary fill | `#FFD600` | `#FFD600` |
| Text on primary | `#171A1F` | `#171A1F` |
| Action text | `#855800` | `#FFD600` |
| Primary text | `#171A1F` | `#F7F6F0` |
| Secondary text | `#626975` | `#9EA3AE` |
| Subtle outline | `#E2DFD2` | `#333842` |

The surface, fill, and action colors follow the Stitch HTML. Light secondary
text is slightly darker than the proposal so it is readable on the warm-white
background. Interactive outlined controls have stronger outlines than passive
card edges. Success remains green and errors remain red; neither is used as
the app's brand color. Disabled controls have separate foreground/background
colors.

## Validation

- `:app:assembleDebug` passed.
- Normal-text contrast checks passed for primary and secondary text on both
  backgrounds and surfaces, action text on surfaces, and text on filled
  buttons. The minimum among these checked pairs is 5.11:1 in light mode and
  6.16:1 in dark mode.
- The initial device update was rejected with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. After the user requested installation,
  the previous APK, private files, preferences, and databases were backed up.
  The app was reinstalled and its data restored, remapping the former app UID
  to the new UID and restoring SELinux contexts. Database and preference
  hashes were verified against the backup before launching the app.
- The new version is installed and running on the SM-G975F. The dark dashboard and profiles
  were visually checked with yellow navigation, profile badges/actions,
  battery ring, graphite surfaces, and a green running-service icon. The saved
  profiles screenshot is `after/profiles-dark.png`.
- Built APK: `app/build/outputs/apk/debug/app-debug.apk`.
