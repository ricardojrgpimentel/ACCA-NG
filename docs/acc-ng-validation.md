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

## Script command lookup

On Samsung SM-G975F, `acca -v` failed with “inaccessible or not found” because
the root shell PATH did not include the ACC runtime directories. The executable
at `/dev/.vr25/acc/acca` returned `v1.0.3-ng (202610093)`; ACC-NG retains the
`acca` and `acc` command names. User scripts now prepend `/dev/.vr25/acc` and
`/dev` to PATH inside their child shell. Read-only device checks passed for
`acca -v`, `acc -v`, multiline bodies with quotes/pipelines and preservation of
an explicit exit code 23. These checks did not change charging settings.

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

### Input units after reinstall

Final dashboard inspection found the installer resetting modern input units to
legacy A/V even after the one-time dashboard migration. The modern handler
normalises readings to micro units, so this displayed approximately 4200 V.
Install/reinstall now sets microamp/microvolt inputs for modern handlers and marks
the migration complete; display/output unit choices remain unchanged.

Reattachment to an already installed Magisk module was also tested by removing
only the app's module symlink and accepting “Configurar”. It reused ACC-NG without
wiping settings, retained the active profile and displayed the measured voltage
correctly as 4.200 V. The installed final APK was verified against the local build.


## ACC dev integration candidate I1 — v1.0.4-ng

The first selective integration is now in the source and candidate app bundle;
see [the implementation record](acc-upstream-implementation.md) for applied
upstream origins, pending work and the candidate checksum. This section does
not replace the historical v1.0.3-ng physical validation above.

The candidate retains NG API 1, the old config schema and CLI output. Host
engine tests passed (33 executed, 2 Android-only skipped). All 20 limit fixtures
passed with root, mksh and BusyBox on Samsung, using temporary files only.
All 28 runtime shell scripts passed Android syntax checks. The app's 77 unit
tests, debug build and lint passed. Candidate packaging is deterministic.
No installed APK/module/configuration/profile or live sysfs control was changed.
Upgrade, rollback, actual current/voltage/thermal controls and reboot validation
remain pending before publication.


## ACC dev integration candidate I2 — v1.0.5-ng

R0 contract fixtures and parsing/capability work are recorded in
[the implementation record](acc-upstream-implementation.md#i2--r0-contrato-e-fixtures-entre-appmotor).
The current bundle is v1.0.5-ng (202610095), from engine commit `2d969cb3ec3854155479115b5b230d344e4ff44f`;
SHA-256 `b6b7dca9b4fd7816031b7852ed66ef339626d5c76d4f22c71dc5d130ebecca4a`. NG API 1 and schema 202310160 stay unchanged.

52 host engine tests: 50 passed, 2 Android-only lock tests skipped. The 17
contract tests and existing 20 limit tests passed on Samsung SM-G975F using
root, mksh and BusyBox against generated temporary files. All 28 runtime shell
scripts passed Android syntax checks. The app's 106 tests passed in both debug
and release; both variants passed build and lint. The local release check is
unsigned, matching CI, and is not published. Fixture source hashes match the bundle and packaging is deterministic.

During the isolated fixture run, no installed APK/module, configuration, profile or live sysfs control was
changed. Physical upgrade/rollback, hardware limit application, OEM thermal
behaviour and reboot remain pending. v1.0.3-ng remains the published engine;
v1.0.4-ng and v1.0.5-ng are source/bundle candidates only.


## I2 physical debug check — Samsung SM-G975F, 2026-10-09

After root was granted, debug beta.4-debug (44) upgraded the installed engine
from v1.0.3-ng to candidate v1.0.5-ng (202610095). The official app remains
installed; both apps use the same global engine.

- The debug signing certificates differed. Persistent debug data was backed up
  privately on the phone and restored after reinstall; all eight backed-up
  regular files and symlinks were verified before launch. No full private-data
  export was made.
- Immediately after upgrade, configuration matched the pre-upgrade backup
  byte-for-byte. All 28 installed runtime shell scripts match source.
- Three saved debug profiles remain visible: Default Custom, Charge to 90%,
  and Cool down after 60%. This debug instance shows custom configuration and
  no active profile, distinct from the earlier official-app check.
- Restart through the dashboard succeeded and left one verified daemon.
- With the known battery/charging_enabled 1 0 -- switch temporarily selected,
  cooldown capacity temporarily 99%, and the battery at 77%, resume 82% / pause
  85% produced +366 mA and charging_enabled=1. Resume 70% / pause 75% produced
  +4 mA and charging_enabled=0. Retrying 82% / 85% produced +153 mA and
  charging_enabled=1. Temperature stayed around 27.8–27.9 °C. Thermal limits
  were unchanged and no temperature sensor was spoofed.
- Cleanup stopped the daemon before restoring the original configuration
  byte-for-byte, then started it again. Final app readback shows shutdown 5%,
  resume 70%, pause 80%, cooldown 60% with 50s/10s, and temperatures 40/45/40 °C.
  Automatic discovery subsequently selected battery/batt_slate_mode 0 1;
  the only configuration difference at final comparison was this switch.

The first short cycle with automatic discovery was inconclusive: the worker
outlasted the sample windows, state output lagged physical readings, and
wrote an older configuration after restoration while the daemon was active.
Stopping the daemon before restoration and repeating with the known switch
resolved test cleanup. Discovery/cache and state readback remain R1.2/R1.4
work; this does not validate every automatic switch. Raw traces are retained
in ignored build output; configuration backups remain private on the phone.

Cable disconnect/reconnect for this candidate, locked boot, current/voltage
reset/reapplication, rollback and OEM thermal policy remain pending. The CI
run completed successfully; the candidate has not been published.
