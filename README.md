# ACCA-NG (fork of AccA)



> **Fork** of [MatteCarra/AccA](https://github.com/MatteCarra/AccA), modernised for current Android.
> Installed as `com.accang.app` (side-by-side with the original app).
>
> Key changes vs upstream `develop`:
> - Build: Gradle 8.12, AGP 8.7.3, Kotlin 2.0, Java 17, `compileSdk`/`targetSdk` 36, `minSdk` 26, KSP (Room/Moshi), Maven Central + JitPack (jcenter removed).
> - Bundled daemons: ACC v2023.10.16, DJS v2021.12.14.
> - Android 12–16 runtime fixes: `PendingIntent` mutability, `registerReceiver` exported flags, `AccBootReceiver` registered in the manifest, widget updater as a foreground service, scoped-storage logging, `fitsSystemWindows` edge-to-edge layouts, new-API nullability signatures.
> - Bug fixes from upstream issues: async ACC config load in the editor (no more `runBlocking` on the UI thread), crash-proof config parser, locale-safe voltage/current/temperature formatting, V/A/W unit normalisation for new ACC `acca -i` output, daemon stop via `acca -D stop` (the old `accd.` shortcut spawned stray processes).
> - Vendored `CircleProgressBar` view (artifact never reached Maven Central).
>
> Visual / reliability pass (ACCA-NG):
> - Electric-yellow energy monogram designed with Google Stitch, with adaptive
>   launcher icons and an Android 13+ monochrome themed-icon layer. Logo sources
>   and previews are in [design/branding](design/branding).
> - All root shell calls go through `RootShell` with a `timeout` bound, so a
>   wedged daemon command can never freeze the app again (root cause of the
>   "infinite loading" reports: `set_ch_curr` waits for *charging* state when
>   control files were never detected).
> - Config editor loads behind a centered skeleton with pulse; read failures
>   offer Retry / Use-defaults instead of an endless spinner.
> - Dashboard config card shows an error-with-retry state instead of spinning
>   forever; tapping the card retries.
> - Idle-mode probe is skipped when not charging (it hangs otherwise).

### Charging diagnostics

Open **Charging diagnostics** from the ACC status card when charging limits do
not take effect. The dashboard also reports pending current calibration, a stopped
service, the known modified service with fixed 90%/80% limits, and sustained battery
charging above a configured percentage limit (30 seconds of fresh observations).
A service fingerprint difference is reported as a difference, not proof of a fault.

- **Calibrate current** requires all chargers to be disconnected. Five stable
  discharge readings over ten seconds determine the sensor polarity and scale;
  connected, missing, near-zero or mixed-sign readings do not change calibration.
  The configuration is backed up before a confirmed calibration is written.
- **Restore bundled service** repairs `accd.sh` from the app's existing ACC bundle
  when the installed version matches the bundled version. It does not downgrade
  another version. After confirmation, it backs up the module and configuration
  under `/data/adb/vr25/acc-data/backup/troubleshoot-<id>/`, validates and atomically
  replaces the service, restarts ACC and checks the fingerprint and preserved
  settings. A failed verification attempts to restore the previous executable.
- **Copy diagnostics** copies current readings and any displayed backup path for
  manual sharing. Nothing is sent automatically.

Profile application now waits for the settings to be read back before reporting
success. Automatic charging-switch discovery does not deactivate a saved profile;
an explicitly enforced switch must still match. These checks do not certify a full
charge/pause/resume cycle or guarantee every kernel's charging controls work.



- [DESCRIPTION](#description)
- [DOWNLOAD](#download)
- [LICENSE](#license)
- [LOCALIZATION](#localization)


---
## DESCRIPTION

[AccA](https://github.com/MatteCarra/AccA) is an [acc](https://github.com/VR-25/acc) and [djs](https://github.com/VR-25/djs) front-end.
Both modules come bundled and are automatically installed as needed.

The app is developed with ordinary users in mind.
It targets mainly people and aliens alike who feel uncomfortable with terminal.
ACC and djs commands are still made available, though.

**PLEASE** read acc's documentation (README) **BEFORE** installing AccA!
It's available in Markdown and HTML formats from the link above.
All **disclaimers** and **warnings** listed there apply to this project as well!

If you point your finger at us, because you forgot to do your homework and your home got burned to the ground as a result, we'll simply ignore you.

Typically, we don't answer questions that already have well documented answers.

Join our [Telegram group](https://t.me/acc_group)!


---
## DOWNLOAD

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/mattecarra.accapp/)


---
## LICENSE

Copyright 2019-2021, [MatteCarra](https://github.com/MatteCarra/), [Squabbi](https://github.com/Squabbi/), [VR25](https://github.com/VR-25/)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.


---
## LOCALIZATION

Help us with translations at [CrowdIn](https://crowdin.com/project/advanced-charging-controller/)!
