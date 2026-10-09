# ACCA-NG About screen

Native implementation of the selected Editorial proposal. The screen uses the
existing yellow and charcoal palette in light and dark mode, open sections,
quiet dividers and accessible links instead of the former stacked team cards.

The order is app identity and build, current maintainer Ricardo Pimentel,
fork repository and issue reporting, original AccA developers, ACC/DJS author,
original project and ACC community, then collapsible technical and license
sections. The current fork and original projects have separate destinations.
Original copyright and the Telegram icon attribution remain visible under
license and attributions. Maintainer initials are used instead of a remote
profile image, so the screen does not need a network connection to render.

English, Portuguese (Portugal) and Portuguese (Brazil) copy is provided. App
version/build information comes from BuildConfig. ACC metadata is read only
when the technical section opens, off the UI thread, with a bounded shell
command and an unavailable state. Disclosure state survives configuration
changes and includes accessibility state descriptions and section headings.

## Validation

- `:app:assembleDebug` and `:app:lintDebug` passed. Lint reports no issues in the
  changed About activity, layout, styles or strings; existing warnings remain
  elsewhere in the project.
- Visually checked on an Android 16 emulator in Portuguese in both themes.
- Checked at 320 dp width with font scale 1.3: content wraps, links stay
  reachable, and technical and license sections expand.
- Confirmed that expanded technical state survives configuration changes and
  that missing ACC/root returns `Indisponível` without blocking the screen.
- A temporary debug-only entry point was used for emulator preview and removed.
  The final APK retains the original non-exported About activity.
- The connected Samsung app was not updated or reinstalled.

Screenshots: `about-light.png`, `about-dark.png`, `about-credits-dark.png`.
These are captures of the native screen, not design mockups.
