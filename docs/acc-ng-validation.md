# ACC-NG integration validation — 2026-10-09

Engine repository: https://github.com/ricardojrgpimentel/ACC-NG
Test release: v1.0.3-ng (202610093), based on ACC v2023.10.16.

The app bundle is the release tarball built with `python3 tools/build_ng.py`.
Its SHA-256 is `b89aca510f8728ca109ab3366eed6bde308f652d3667a51ce906f082617df6c5`.
The Magisk ID stays `acc`, so migration replaces the existing engine and
preserves scheduler/runtime paths. The displayed name is ACC-NG, with a generic
description; charging thresholds belong to app profiles.

## Verification

- 14 engine tests passed: dynamic pause/resume limits, thermal pause/resume,
  notification deduplication, real connection/state observations, unknown sensor
  handling, disabled notices, global preferences preserved on profile writes,
  and selection of bundled/GitHub branch/tag archive folders.
- `assembleDebug`, all 60 app unit tests and `lintDebug` passed (no lint errors).
- All module shell scripts passed `/system/bin/sh -n` on Samsung SM-G975F.
- Backed up the old root module and ACC runtime configuration to ignored local
  build output before migration. The full app-private directory was not exported.
- A real resume test temporarily used 86%/85% while the battery was 81%: charging
  current rose to about 1.7–1.9 A. Restoring 60%/50% stopped charging and the
  observed battery state returned to idle, with current close to zero.
- The app settings screen opened, notices were disabled and re-enabled through
  the app, and raw engine readback confirmed both values. Notices end enabled,
  in Portuguese. Turning them off does not change charging protection.
- Updated the engine through the actual app installer. It preserved configured
  limits and the active profile. Reinstalling the included version reused NG.
- Initial migration device check: ACC-NG v1.0.2-ng, one active daemon, all 27 installed top-level
  shell files identical to source, and the installed APK identical to the local APK.

The engine automatically selected `battery/charging_enabled 1 0` on this kernel.
Original configuration remains pause 60%, resume 50%, shutdown 2%; the active
profile remains “Muitas horas no carregador”. Existing calibrated polarity and
other settings were preserved.

## Scope

This is a prerelease verified on this Samsung/kernel combination. Boot persistence and other devices have not been tested.
Physical USB disconnection/reconnection was tested during calibration; a complete
notification cycle during normal steady-state operation has not been tested.
Thermal thresholds were tested with isolated fixtures; the physical battery was
not heated or its sensor spoofed. Critical original ACC control paths remain intact.

## Clean module setup flow

On Samsung SM-G975F, the ACC module, its runtime configuration and volatile
runtime directory were removed after a validated module/configuration backup.
DJS, LSPosed and the app's saved profiles were retained. This checks a clean
engine installation; it is not a wipe of the app's private data.

- With no module present, the app displays “Configurar o ACC-NG” and explains
  root access, installation in Magisk and replacement of another ACC.
- The module remains absent until “Configurar” is accepted.
- Accepting installs the bundled v1.0.3-ng without downloading a ZIP or
  rebooting. The app opens the dashboard with a new configuration (75% pause,
  70% resume) and correctly shows no active profile.
- Magisk was reopened and its Modules page displayed ACC-NG v1.0.3-ng with
  the enable switch on. DJS and LSPosed remained enabled.
- On this kernel, a fresh configuration needs discharge-current calibration.
  The dashboard and diagnostics explicitly explain disconnecting chargers.
  Installing the module alone does not complete that calibration.
- Root permission was already granted on this device. The no-root message now
  explains where to allow AccA-NG and how to reopen it; a newly denied root
  grant has not been physically tested.

Screenshots and the module/configuration backup are retained in ignored
`app/build/device-backups/acc-ng-clean-20261009-112854/`.

### Fixes found during clean setup

The first physical calibration attempt failed. The app and the inherited
controller incorrectly treated `battery/online` as an incoming charger flag.
The app now excludes battery/BMS/OTG nodes and supplies typed Battery/BMS.
The controller uses external supply readings and holds calibration when those
readings are unknown. Regression tests execute these readers against sensor
fixtures, including Samsung's battery enum, real external power and unknown data.

An update attempted while the old calibration workers were waiting also exposed
an inherited lock bug: subshells retained the daemon lock after their parent
terminated. The installer and service now signal only verified ACC daemon worker
commands, then bound the lock wait to five seconds. Fixture tests check that
unrelated commands/processes are not signalled. On Samsung, the corrected installer
successfully recovered the two existing orphan workers and updated to v1.0.3-ng.
The new stop path was then used before repeating a clean v1.0.3-ng setup.

Calibration/repair result dialogs now show their specific result message, so the
user receives the instruction about unplugging/stable readings directly in the
result dialog.

### Physical calibration and charging result

With the corrected app and a clean v1.0.3-ng module, the user disconnected USB,
ran “Iniciar calibração” and received success. Engine readback confirmed
`dischargePolarity=-`, Portuguese notices and an automatically selected
`chargingSwitch=(battery/charging_enabled 1 0)`.

The temporary sensor log independently confirmed `battery/online=1` while
`ac/online=0`, `usb/online=0`, status Discharging and negative current. This
reproduces the false positive in the previous reader. On reconnect, the engine
completed switch discovery and paused above 75% with `connected=true`,
`state=Idle`, `reason=limit`; current readback was -4 mA with AC online=1.
Only one daemon process remained after calibration.

The original configuration was restored byte-for-byte from the validated backup,
including pause 60%, resume 50%, shutdown 2%, selected switch and thermal limits.
The app's saved profiles were not deleted.

The original “Muitas horas no carregador” profile was then reapplied through the
app and displayed as active. Limits remained 60%/50%, polarity remained negative,
notices remained enabled in Portuguese, and AC was online with charging
current close to zero (0–4 mA). All 27 module scripts matched source and the installed APK matched the
local build. The one remaining daemon explicitly used the profile configuration.
Magisk's final Modules page displayed v1.0.3-ng alongside the unchanged DJS and
LSPosed modules.
