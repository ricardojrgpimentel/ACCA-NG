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


### Candidate physical USB disconnect/reconnect

The user disconnected and reconnected USB with the candidate installed. A
bounded five-minute logger captured external online nodes, battery current,
status, controls and NG state without requiring a live ADB connection.

- With batt_slate_mode=0, USB online changed from 1 to 0 and current became
  negative. NG state changed to connected=false / Discharging within the
  sampled seven-second window. Engine event history records disconnected at
  21:27:09 UTC.
- USB online returned to 1; NG connection state updated within the sampled
  two-second window. Event history records connected at 21:27:46 UTC, followed
  by idle and resumed. User-visible notification delivery was not confirmed.
- Earlier periodic USB online=0 samples coincided with the configured slate
  cooldown control; they did not produce disconnected events. These samples
  are distinct from the physical unplug with slate control off.
- Final readback retained resume 70% / pause 80%, negative discharge polarity,
  notification preferences and one daemon. Physical battery temperature was
  below 29 °C. No limits were changed for this cable test.

Cable connection detection passes on this device/candidate. State refresh
latency and automatic discovery remain separate open work; locked boot and
other pending physical checks above are still unverified. Pre-reboot engine
configuration and boot identity are saved privately on the phone for the next
check.


### Candidate locked boot

The user rebooted with USB connected and left the device at its first PIN
prompt. Root ADB remained available before credential unlock.

- The kernel boot identity changed; uptime was 107.51 seconds at the first
  check, independently confirming a new boot.
- Both dumpsys user and activity processes reported user 0 RUNNING_LOCKED.
  Neither the official nor debug app had a running process at that check.
- ACC-NG v1.0.5-ng started automatically through the enabled Magisk module.
  One daemon was running, PID 8586, matching the runtime lock. Process start
  ticks and CLK_TCK placed its launch at 30.63 seconds after boot.
- The configuration SHA-256 matched the pre-reboot backup exactly:
  3edaa8195194973fb644d9aef2b8b7e08e10754df4c5172291434620b4338ec2.
  Resume 70%, pause 80%, cooldown, thermal limits, polarity and notification
  preferences persisted. NG state reported connected=true with USB online=1.
- Battery capacity was 79% and temperature 28.7 °C. The single current sample
  was negative despite the reported Charging state; this startup observation
  is not evidence of positive net battery charging or a new physical cutoff
  cycle. The earlier known-switch cycle remains the physical cutoff evidence.

Locked boot persistence passes on this Samsung/Magisk combination without
opening either app. The user was told they could unlock after the check.
Automatic discovery/state freshness, current/voltage reset/reapplication,
rollback, OEM thermal policy and other root managers remain separate work.

## I3 local candidate — 2026-10-10

ACC-NG **v1.0.6-ng (202610106)** and AccA-NG **2.0.0-ng-beta.5 (45)** are
unpublished candidates. Engine source: [`1e8291b`](https://github.com/ricardojrgpimentel/ACC-NG/tree/1e8291bcc69d59de677674728075b0c9063b561a),
over `2d969cb`; app base: `6b52e0a`. NG API 1 and schema 202310160 remain
unchanged. Implementation and upstream origins are recorded in
[the I3 integration record](acc-upstream-implementation.md#i3--descoberta-alimentação-e-aplicação-de-limites).

The deterministic engine archive and app resource have SHA-256:

```text
5bfa3609db90cd43c0c529ea8f91a920e02c20654c06f8fe6611c29e43914b92
```

The physical trial used archive
`17332c2b3e43583e4df9233dd74802e947e627f992f80764db067c00984a0d79`.
Before committing, only its bundled `docs/upstream-integration.md` was updated
to record the completed trial and source provenance. All runtime files are
byte-for-byte identical to that trial; the new archive was reproduced and the
app rebuilt. This archive change did not repeat physical validation.

### Automated checks

| Check | Result |
| --- | --- |
| Engine host suite | 73 cases: 71 passed, 2 Android-only skipped |
| Engine suite with Android fixture runners | 73 passed; root, mksh and BusyBox on Samsung SM-G975F for shell fixtures; Python/build checks on host |
| Runtime shell syntax on Android | 32 scripts passed; final limit-control change also executed by all 21 limit cases on Android |
| App unit tests | 114 passed in each of debug/release; no failures, errors or skips |
| App builds and lint | Debug and unsigned release APKs built; lintDebug/lintRelease passed |
| Shared contract | Export/check passed, including generated Kotlin power helper and all 18 bundled runtime source hashes |
| Archive reproduction | Building from a clean git archive of engine commit 1e8291b produced the same SHA-256; packaged checksum matches |
| Translation checks | 21 locales checked, no errors; new status strings include pt-PT/pt-BR and English fallback |

Fixtures cover deferred requests, no support, refused writes, OEM value drift,
original-value restoration, cache loss/restarts, strict units, alias deduplication,
controls that share a driver value, blacklist/live candidate validation,
thermal references and preservation of the latest/manual settings. They use
temporary files and stubs, not live charging controls.

### Limited physical upgrade and readback — Samsung SM-G975F

The existing I2 engine/configuration and debug app data were backed up before
installation. Private evidence is kept under ignored `build/validation/i3/`,
with a corresponding dated backup on the device. The official release app was
unchanged; the debug app was updated to code 45.

The first upgrade attempt exposed an inherited installer bug: uninstall cleanup
matches `/data/local/tmp/acc[-_]*` and deleted the installation source in that
directory. Automatic recovery restored I2, restarted the daemon and preserved
the configuration hash. Repeating from `/data/local/tmp/ng-i3-candidate-*`
installed I3 successfully. This is a staging workaround; **the cleanup bug is
still open in R5.3**. It does not certify the explicit rollback CLI.

The installed runtime matched all 18 source hashes from the committed bundle.
The installed debug APK matched the local debug artifact at the trial check,
before the documentation-only archive update described above. The installed
engine reported v1.0.6-ng/202610106, with one daemon matching its runtime lock.

A bounded live trial requested **250 mA** and **4100 mV**, then withdrew both
requests and restored the exact original configuration:

- The eligible `ac`, `usb` and `wireless/current_max` controls stayed at 475000.
  Writes were refused. Readback reported `current.state=failed`, with
  `current.supported=true` meaning discovered candidate controls, not successful
  write support. No current ownership snapshot was acquired.
- Voltage discovery remained `supported=unknown`, `state=pending` while the
  battery was Discharging and USB online=0 during configured slate cooldown.
  This does not prove voltage support or lack of support, and was not a physical
  cable unplug/replug test.
- The 12 samples covered approximately 36 seconds. Battery temperature remained
  26.6–26.7 °C. No battery sensor was spoofed or battery deliberately heated.
- After withdrawal, both requests read `default`/`off`; ownership markers were
  absent. Current nodes retained mode 444 and owner 0:0. `battery/siop_level`
  remained 100, mode 664, owner 1000:1001; its write/reset was not exercised.
- The original configuration SHA-256 was restored exactly:
  `3edaa8195194973fb644d9aef2b8b7e08e10754df4c5172291434620b4338ec2`.
  Later observation showed slate=0, USB online=1 and positive battery current
  (134 mA), consistent with charging resuming under the original protection.
  The final status showed online=true and both power limits off.

The physical result confirms upgrade/configuration preservation and honest
failure reporting on this kernel. **Successful current/voltage application and
restoration remain unverified** because these current controls refused writing
and voltage discovery did not complete in the trial window. Full OEM thermal
policy, explicit rollback, locked boot/USB repetition for I3, other devices and
root managers remain open. I2 results remain evidence for I2 only.

No GitHub release, updater announcement or publication metadata was changed.
The bundled engine source is pinned; finish the required R7 checks before distribution.


## I4 staging cleanup candidate — 2026-10-11

ACC-NG **v1.0.7-ng (202610117)** / AccA-NG **2.0.0-ng-beta.6 (46)**,
unpublished. Engine source pinned to local commit
`8305720c356fec9c8b4be8ee7a5de1bffc4983aa`, over I3 `1e8291b`;
app base `e857f57`. This source commit has not yet been pushed. API 1/schema
202310160 and charging behaviour remain unchanged.

Candidate tarball and app resource SHA-256:

```text
603b4aabd7315066e09a279637c29f8411612371f0afc05f3d5c32b9e86ebaf1
```

### Automated checks

| Check | Result |
| --- | --- |
| Engine host suite | 79 cases: 77 passed, two Android-only skipped |
| Engine with Android fixture runners | 79 passed; Samsung root/mksh/BusyBox shell fixtures, Python/build checks on host |
| Installation regression | Six tests/nine scenarios pass on host and Android; all six tests fail against the old uninstaller |
| Shell syntax | All 35 runtime/installer scripts passed Android /system/bin/sh -n |
| App unit tests | 114 passed in each debug/release, no failures/errors/skips |
| App build/lint | Debug and unsigned release builds, lintDebug/lintRelease passed |
| Shared contract | Export/check passed; 22 source hashes including four installation scripts |
| Reproduction | Clean git archive of source commit reproduces exact tarball; flashable uninstaller equals fixed source |

Installer fixtures execute full production scripts with Android paths redirected
below a temporary root. Root/process/platform tools and service initialization
are stubbed. Tests verify clean installation, direct/tarball upgrades, full
uninstall, unrelated staging preservation, and recovery from injected copy
failure (exit 42). They verify backup/config restoration, one fixture service
initialization, original exit status and source cleanup after recovery. They do
not validate live rollback CLI or charging hardware.

### Physical upgrade — Samsung SM-G975F / Magisk

Private evidence and scripts are under ignored `build/validation/i4/`; a dated
module/configuration backup was retained on the device before installation.
The committed candidate was installed using the **exact app raw installer**
from /data/local/tmp/acc-i4-install-*. A separate acc-i4-sentinel-* directory
contained an unrelated marker. Both match the formerly destructive glob.

- Upgrade succeeded, reporting v1.0.7-ng/202610117. The staging installer and
  unrelated marker survived; the tarball caller removed its extracted source.
- Original configuration SHA-256 remained exactly
  `3edaa8195194973fb644d9aef2b8b7e08e10754df4c5172291434620b4338ec2`,
  both immediately after install and after daemon initialization.
- All 19 installed runtime hashes (including uninstall.sh) matched the fixed
  candidate's contract. Root-level installers are tracked in the 22 bundle
  hashes but are not copied into the installed runtime.
- The initial verifier sampled --init and its temporary subshell too early.
  Follow-up after initialization confirmed one normal accd process, PID matching
  the runtime lock. This was a verification timing issue; installation passed.
- Readback showed current/voltage requests at default and both states off.
  online=false was observed; no physical USB or charging-limit test was
  performed.
- Debug APK upgrade succeeded and package metadata reports beta.6-debug/code 46.
  Official release app and DJS were unchanged. Test staging/sentinel paths were
  removed after collecting evidence; the module/config backup was retained.

The staging cleanup defect observed in I3 is fixed and has physical upgrade
coverage. Failed-copy recovery is covered in fixtures; no live failure was
injected. Explicit rollback CLI, broader R5.3 cleanup/root managers, successful
limit application/restoration, OEM policy, USB/locked boot repetition and full
R7 remain pending. No source push, GitHub release or updater announcement.


## I6 joint core candidate — 2026-10-11

**ACC-NG v1.0.8-ng (202610118) / AccA-NG 2.0.0-ng-beta.7 (47)**.
Engine source: `ea5972f5d95e08ba9b98ac67c83ef375121aad72`.
API 1/schema 202310160 retained; no stable release/updater change.
Historical I1–I5 evidence above is not relabelled as this candidate's evidence.

Tarball/app-resource SHA-256:

```text
96a4d95125be088cd5d2109020da8bcfaa8f1ddfb88f2359fed9922554ada1a1
```

| Check | Result |
| --- | --- |
| Engine host suite | 133 cases: 131 passed, two Android-only skipped |
| Engine with Android fixture runners | 133 passed; root/mksh/BusyBox fixtures on Samsung, Python/build checks on host |
| Shell syntax | 53 scripts passed /system/bin/sh -n; final CLI changes checked again |
| Configuration transactions | Concurrent writes, invalid/failing commits, imports/scripts, atomic reset/removal and restart without inherited writer lock |
| Lifecycle/install fixtures | Boot/disable/readiness, safe extraction, HTTP failure, cleanup/recovery/rollback and bounded exports |
| App unit tests | 119 passed in each debug/release, no failures/errors/skips |
| App build/lint | Debug build, signed release, lintDebug/lintRelease passed |
| Shared contract | 35 source hashes, module/schema/capabilities and exact generated external-power helper match the shipped bundle |
| Reproduction | Clean git archive reproduces identical engine tarball; candidate resources verified in both APKs |
| Signing | Existing release certificate SHA-256 b658f70c0aa5046aaf2a508be344ad882c0293947628a6cf276a3ec8c533ea8d; APK signature and 16 KiB alignment verified |
| Local APK updates | Signed/debug beta.7/code 47 installed; database rows/preferences and DJS verified before locked boot. Final resource-only repack installed as an update before PIN; encrypted data is checked again only after user unlock |

Fixtures write only generated temporary files; they do not certify electrical
power limiting or all root managers. Boot/process/platform tools are simulated
in lifecycle fixtures. Actual device trials are recorded separately below.
Private device logs, database copies and backups remain outside Git.

### Physical Samsung SM-G975F / Magisk

Evidence/scripts: ignored `build/validation/i6-final/`. Each upgrade retained a
dated module/config backup, tested the exact app installer from acc-* staging,
preserved a separate unrelated sentinel, removed only its extraction and
verified 31 installed runtime hashes plus one normal daemon matching its lock.
The final candidate is installed on the device.

- User feedback on 2026-10-11: “esta a funcionar bem no samsung”. This records
  a positive report of general use. The report does not specify individual
  test scenarios or independently verify the installed version; physical USB
  unplug/replug, clean installation and the other certification gaps below
  remain open until their results are recorded.
- Original config SHA-256 remained
  `3edaa8195194973fb644d9aef2b8b7e08e10754df4c5172291434620b4338ec2`.
  Native battery current is mA (factor 1000); voltage is µV. Manual polarity,
  selected switch, thermal limits and notification preferences were retained.
- A physical probe of `battery/batt_slate_mode 0 1 --` returned **0 / Ok**.
  Off/on were confirmed with three stable samples each and original OFF restored.
  This is a working pause switch, not a claim of idle-mode support.
- The first trial exposed that slate OFF clears USB online while the cable is
  attached. The final helper records our OFF readback and preserves unknown;
  probes temporarily enable the configured input, prove connected power and
  restore its journal. Android/host fixtures exercise baseline recovery,
  cancellation and resolving relative paths from the app.
- Capacity trial confirmed initial charging, real pause and real resume over
  three consecutive samples. At 73%, pause yielded about **−107 mA** and resume
  **+337 mA**, temperature **29.9 °C**. Cooldown capacity was temporarily 99% to
  isolate capacity switching from the original 50s/10s cycle; thermal limits
  remained active. The first capacity trial retained cooldown and did not prove
  net charging; it restored the profile and prompted this corrected trial.
  All temporary capacity/cooldown changes were removed and original hash restored.
- Explicit CLI rollback selected the retained v1.0.7 snapshot, matched every
  runtime script/module hash, kept the current config and started one daemon.
  Reinstalling v1.0.8 through the exact app installer passed again.
- Reboot changed boot ID; before PIN/unlock, engine-state was ready, a single
  daemon owned its lock and 31 runtime hashes matched. A temporary interval
  away from the fuel-gauge boundary (resume 61%, pause 76%, cooldown 99%)
  confirmed real offMid pause with slate OFF;
  all thermal/OEM settings remained active. Its config hash survived reboot.
  Original config and last-good were restored afterward under the writer mutex.
  An earlier trial at 71%/resume 70% could not prove a stable OFF state during
  boot. The code audit also removed dependence on observing net charging before
  a known offMid pause, forces its OFF write even if normal disable would
  classify net discharge as already stopped, and retains unresolved automatic
  requests; two new
  behavioral fixtures cover these paths. No UI unlock was performed.
  The verifier accepts numeric deviceLocked=1 and confirms RUNNING_LOCKED
  credential-encrypted user state; its initial textual-boolean assumption was
  corrected.
- Samsung SIOP: with charging held OFF and the daemon stopped, production
  set_temp_level/apply_limit helpers changed siop_level **100 → 80 → 100**,
  captured/restored the owned value and removed their journal. Original mode
  **664** and config hash were retained, then the daemon restarted. This proves
  control write/readback/restoration; the stopped-charge trial does not certify
  electrical current reduction or OEM thermal policy under load. Private
  evidence: build/validation/i6-final/thermal-level.json.

Final code CI passed [Android checks](https://github.com/ricardojrgpimentel/ACCA-NG/actions/runs/38168343919): translations, exact bundled contract, tests, lint and unsigned release build.

### Certification still requiring hardware

The candidate implements R0–R5 and validates the available Samsung/Magisk path.
Successful current/voltage limiting and broader OEM thermal behavior require
a kernel with supported writable nodes; the SIOP write/reset above covers that
specific Samsung control, not its electrical efficacy. the earlier I3 current-write refusal is
not promoted to proof of successful I6 limiting. KSU/APatch and other OEM
rules have fixtures and official manager flag/path references, not physical
certification. Clean installation was initially covered by complete-script fixtures; the
12 October live clean-installation follow-up below now covers Samsung/Magisk. Physical USB
unplug/replug was not repeated for I6; historical I2 evidence remains I2.
Thermal boundaries/cooldown/shutdown are exercised synthetically, without
heating the device or falsifying live sensors.

New idle_apps, Encore, percentage-current/editor features and advanced R6
extensions remain optional outside this candidate. Existing temporary
force-charge and mask behavior keep physical capacity and thermal/OEM limits.
Source PRs and locally signed artifacts are review candidates; no GitHub
release, main merge or stable updater announcement was performed.


### Post-unlock and clean-installation follow-up — 2026-10-12

The current roadmap branches were recovered from the two draft PRs; `main`
held I3 at the start of this follow-up. The Samsung was already running the pinned I6 runtime. No older
candidate was installed over it. Private evidence is under ignored
`build/validation/`, with backups retained on the device.

- User 0 was RUNNING_UNLOCKED. Official-app SQLite integrity checks passed;
  all rows (three profiles, ten scripts, no schedules) and preferences matched
  the pre-PIN backup. Debug already had one profile instead of the earlier
  three, with different preferences before this session. That state was kept;
  it is not evidence that the earlier debug update preserved every old row.
- Rebuilt and verified the signed beta.7 APK with the established certificate
  and 16 KiB alignment, installed it as an update, and opened the dashboard.
  Both apps retained their session-baseline database rows. Debug preferences
  were unchanged; official preferences changed only `profile=3` to `profile=-1`
  when the dashboard recognized the current custom configuration. Saved profiles
  remained present. No historical debug data or engine profile was restored over
  the current settings. The latest config SHA-256 remained
  `f51e728c6a5d1f3641284c1c6d28fb61fbabcaaf1de8a3a3d348ac452f084366`.
- All 31 installed runtime source hashes match the existing I6 contract. The
  engine reports ready/Magisk. Both rebuilt APKs contain the exact deterministic
  bundle with SHA-256 `96a4d95125be088cd5d2109020da8bcfaa8f1ddfb88f2359fed9922554ada1a1`.
- Live clean installation used the exact app raw installer and bundle. After
  stopping the original daemon, its module, data and runtime were moved aside;
  the installer had no existing module/configuration to migrate. Defaults were
  resume 70% / pause 75%, temperature 35/50/45/55, no power limits and automatic
  polarity; valid physical sensors and ready/Magisk were observed. Original
  module/data were then restored, and the current configuration passed a
  byte-for-byte comparison. App data and DJS were not removed. The first setup
  attempt could not move the tmpfs runtime onto `/data`; its recovery passed.
  The successful attempt used a rename within `/dev` for that runtime.
- A bounded 500 mA / 4100 mV trial recorded `500::failed` and
  `4100::unsupported` from the production limit results. The current request was not confirmed as applied; voltage discovery reported no eligible control. Concurrent
  `--power-status` calls were busy while the daemon owned the configuration
  lock, so they were not described as successful readback. After stopping the
  daemon, voltage read unsupported and current read pending after stop reset
  its result marker. Both requests were removed; config/last-good were restored
  exactly and normal protection restarted. This remains failure/no-support
  evidence, not successful electrical current/voltage limiting.
- Engine suite: 133 cases on macOS (131 passed, two Android-only skipped) and
  133 with Android fixture runners. The historical preference-write test runs
  on the host even in the Android invocation. Its missing host `timeout` was
  fixed; host fixtures now use real POSIX file locks, bounded subprocesses and
  translations of the used GNU tool forms. macOS Bash 3.2's zero status on an
  unclosed array is treated as a syntax failure. The diagnostic fixture uses an
  explicit synthetic boot identity instead of assuming host `/proc`. These are
  test-tool changes; the runtime/archive bytes remain identical to I6.
- App: 119 tests passed per debug/release, both lints and builds passed;
  translations checked 21 locales with no errors; shared contract export/check
  passed. All 51 runtime/installer/translation shell sources checked in this
  follow-up passed Android syntax. Previous 53-script checks included additional
  staged wrappers. Engine CI now covers Linux and macOS plus fixture export and
  deterministic candidate packaging, without promoting `module.json`. Both jobs
  passed [Engine checks](https://github.com/ricardojrgpimentel/ACC-NG/actions/runs/38225148486).

Physical cable removal/reconnection requires a person at the device. Other OEM,
KSU/APatch, writable mcc/mcv and OEM policy under load remain separate hardware
certification. The core integration does not imply that unsupported hardware
acquired support. No public release or stable updater announcement was made.


### Direct legacy upgrade follow-up — 2026-10-12

A further R7 audit checked the exact v1.0.3-ng upgrade path, rather than relying
on the earlier chain of incremental upgrades. The original dated on-device
snapshot `install-20261009-221423-28464` contained v1.0.3-ng/202610093 with API 1
and its original configuration. While the current module/data/runtime were
safely held aside, those genuine module files and configuration were restored
as the installer input. The old daemon was never started. This validates the
legacy on-disk upgrade path, not a running-old-daemon transition.

The exact app installer/bundle upgraded that installation to v1.0.8-ng/202610118
and reached ready/Magisk with valid physical sensors. The upgrade backup matched
all old module files and the original profile exactly. Every upgraded config
byte except the initially empty automatic `chargingSwitch` assignment remained
unchanged: resume 70% / pause 80%, cooldown capacity 60%, temperatures 40/45/40/55,
manual negative polarity, notification settings and hooks were preserved.
Automatic discovery selected `battery/batt_slate_mode 0 1`. The first verifier
incorrectly treated that expected selection as a profile-loss failure; the
second explicitly verified that it was the only change and checked the complete
rollback snapshot. Both attempts restored the current installation and its
session-baseline configuration exactly.

Private evidence: ignored `build/validation/legacy-upgrade-trial.txt` and dated
on-device backups. The final current config SHA-256 remains
`f51e728c6a5d1f3641284c1c6d28fb61fbabcaaf1de8a3a3d348ac452f084366`.

The core PRs were merged into engine main `938e75f` and app main `206411c`.
Post-merge CI passed on both exact commits:
[engine Linux/macOS checks](https://github.com/ricardojrgpimentel/ACC-NG/actions/runs/38225806794)
and [app Android checks](https://github.com/ricardojrgpimentel/ACCA-NG/actions/runs/38226054157).
These merges did not create a release or promote the stable updater.

R7 remains open for current-candidate physical cable disconnect/reconnect and
fresh calibration with genuinely disconnected external power, successful
mcc/mcv on writable hardware, OEM policy under load and physical KSU/APatch.
The available Samsung result does not certify those unobserved scenarios.


### Samsung SIOP under charging load — 2026-10-12

The preceding SIOP test had the charging control OFF, so it proved ownership
and restoration without electrical-effect evidence. This follow-up used the
real `acc --set temp_level` CLI while the battery was naturally charging.
Current firmware/runtime remained I6; no battery service/sensor was falsified.

For the bounded test only, resume/pause were 65%/70% and cooldown capacity 101%
so that the original 50%/60% interval did not stop charge during sampling. The
actual capacity was 58–59%. Shutdown, temperatures, switch, polarity and other
OEM/current/voltage settings remained active. Four physical battery-current
samples were taken every two seconds in each phase:

| Phase | SIOP readback | Mean battery current | Battery temperature |
| --- | ---: | ---: | ---: |
| Before restriction | 100 | +1908.5 mA | 29.4–29.7 °C |
| `temp_level=70` | 30 | +1158.5 mA | 29.7 °C |
| After `temp_level=0` reset | 100 | +1898 mA | 29.7–29.8 °C |

This observed reduction followed by return toward the baseline supports an
electrical effect of this specific Samsung SIOP control under the sampled load.
It does not establish an exact current target or general thermal-policy behavior.
SIOP mode/owner stayed 664 and 1000:1001. Its ownership journal was removed after
reset. The original current config and last-good were restored byte-for-byte;
original SIOP 100 was confirmed and the daemon restarted. Private evidence:
ignored `build/validation/siop-load-trial.txt` and `siop-load-summary.json`, plus a
dated device backup. The actual USB online node was 0 in these samples; positive
battery current confirms charge, but this is not evidence of physical unplug.

CI for app main `534b5a9` also passed
[Android checks](https://github.com/ricardojrgpimentel/ACCA-NG/actions/runs/38227058994).
The remaining full R7 gate still needs physical cable/calibration work and
suitable writable mcc/mcv and KSU/APatch/OEM hardware. No release was published.


## I7 unplug/calibration candidate — 2026-10-12

ACC-NG v1.0.9-ng/202610129 / AccA-NG beta.8/code 48, unpublished.
Runtime source `b3e59e6d3bed20d52aadcb66d351f4a7eac2af88`; bundle SHA-256
`6254bb8ad9bf5bf2d9e35c1344124815325258c1b62b63da7a5f9ddfa8682b51`.

### Physical I6 cable trial and identified calibration delay

The user physically removed and reattached the USB cable with I6 installed.
A bounded detached logger captured 118 samples independently of the ADB
connection. The ADB transport changed on reconnect. AC was the actual incoming
source on this Samsung; the USB online node alone was not a correct cable proxy.

- Incoming-power readback changed true → false → true, and real battery current
  changed from about +1854 mA to −539 mA and then +1566 mA.
- Engine events recorded disconnected at 09:26:12 UTC and connected at
  09:27:03 UTC. Connection state updated coherently; the event check does not
  establish user-visible notification delivery.
- `dischargePolarity` had been cleared for fresh automatic calibration, but
  remained blank. This trial passes physical cable detection for I6, not
  automatic calibration.
- The logger removed its temporary resume 65% / pause 70% and restored the exact
  original config/last-good. Engine ready and config SHA-256
  `f51e728c6a5d1f3641284c1c6d28fb61fbabcaaf1de8a3a3d348ac452f084366`
  were verified. Private evidence is ignored `build/validation/manual-usb/`.

The controller resumed at low capacity even with explicitly disconnected power;
its ON verification could wait 14 × 3 seconds on an unpowered control before
another calibration sample. A full loop regression, including real is_charging
and set_dp functions, reproduces 153 simulated seconds and three futile ON
requests for three samples. The corrected loop skips those requests for known
unplug, yielding three samples in 27 seconds. Connected and unknown power still
allow resume, so an OEM input pause is not mistaken for a disconnected cable.

The corrected I7 trial is recorded separately below. The remaining external-hardware
certification and release gate continue to apply.


I7 final automated checks: 138 Android-runner cases passed; 138 host cases
(136 passed, two Android-only skipped). The pending-ON regressions also verify
unplug during an existing check: 42 simulated seconds before the fix, three
after, including lagging kernel Charging status. Connected/unknown resume
remains eligible. The signed/debug beta.8 build checks and final physical trial
are recorded below.


I7 packaging/upgrade checks: 119 app tests passed per debug/release, both lints
and builds passed. The signed APK retains the established certificate and
16 KiB alignment. The final bundle was installed through the exact app raw
installer; the APK update succeeded. All 31 runtime hashes match the final
contract. Official/debug database rows and preferences matched the immediately
pre-upgrade backup, and the original config hash remained unchanged. The
initial v1.0.9 package was an intermediate local build; both module and APK were
replaced with the final b3e59e6 bundle before the next physical trial. No public
version code or release asset was reused.


The final classification regression also reproduces native Discharging with two
negative battery-current samples while polarity is blank. Relative-current
fallback previously changed that to Charging. Confirmed absent incoming power
now remains Discharging without waiting for calibration; unknown/connected
power retains the existing behavior. Final source is b3e59e6, covered by all
138 Android cases and 136 host cases plus two Android-only skips.


### Physical I7 calibration and restoration — 2026-10-12

The user unplugged and reattached the cable with final b3e59e6 runtime and
signed beta.8 installed. A detached logger captured 89 samples through the
ADB disconnect. Recorded accd.sh/batt-interface.sh hashes match that source.
Fresh automatic calibration began with dischargePolarity blank; temporary
resume 65% / pause 70% / cooldown 101% ensured charging before unplug.

- Incoming-power readback changed true → false at 10:23:43 UTC → true at
  10:25:24 UTC. AC was the incoming source; USB online stayed zero.
- The disconnected event and Discharging state were observed at 10:23:53 UTC,
  with real battery current −760 mA. Polarity became `-` at 10:24:13 UTC,
  30 seconds after the first offline sample, without an unpowered ON wait.
- Reconnect event was recorded at 10:25:31 UTC. The next sample at 10:25:32 UTC
  showed connected/Charging and +1647 mA; subsequent charging current reached
  +1880 mA. Learned negative polarity remained after reconnect.
- Published daemon state lagged the first offline/online readbacks by about
  ten/eight seconds, consistent with controller cadence and logger sampling.
  This is not an assertion of instantaneous state or delivered UI notifications.
- Logger result was `restored`. Original config and last-good were restored
  byte for byte; config SHA-256 remains
  `f51e728c6a5d1f3641284c1c6d28fb61fbabcaaf1de8a3a3d348ac452f084366`.
  Final health showed ready/Magisk, one daemon matching its lock, original
  protective thresholds, SIOP 100/mode 664/owner 1000:1001 and current-node
  permissions 444/root. Charging is again governed by the original profile.

Private raw evidence and summary are ignored `build/validation/manual-i7-usb/`;
final health is `build/validation/i7-final-device-health.txt`. Android CI passed
for engine b3e59e6 on Linux/macOS and app de2cefb. This closes physical cable
and fresh automatic-calibration checks on this Samsung. Successful writable
mcc/mcv, other OEM policy and KSU/APatch still require separate hardware;
R6 remains optional. No public release or updater promotion occurred.


## Completion audit — 2026-10-12

The full R0–R5/R7 objective remains **incomplete**. Runtime and app integration
are in main (engine `96dfe45`, app `6d64cb2`) and both exact merge CIs passed:
[engine Linux/macOS](https://github.com/ricardojrgpimentel/ACC-NG/actions/runs/38236917858),
[app Android](https://github.com/ricardojrgpimentel/ACCA-NG/actions/runs/38237425088).
Review below distinguishes executed synthetic behavior, actual Samsung trials
and external-hardware evidence that is still absent. Historical I6 trials retain
their source identity; they are not relabeled as I7 repetitions.

The audit found a structural assertion for three protective code paths. It was
replaced by seven controller scenarios in `tests/test_controller_protections.py`
in the engine: cooldown-charge capacity/max-temperature/shutdown/unplug,
invalid config/sensors, physical percent/mV shutdown boundaries, reconnect
cancellation, three-attempt error 7 and two-cycle idle reselection. They execute
production control/threshold/wait helpers with synthetic files and stub only
platform operations. No live thermal sensor was changed and no shutdown was
triggered. Removing protective sampling or shutdown eligibility, extending the
control budget to four, or extending idle reselection to three each caused
failures in those tests; mutations were applied only in memory.

Full audited engine suite: **144 Android passed; 142 host passed/two Android-only
skipped**. App results inspected from XML: **119 per debug/release**, no errors,
failures or skips. Existing signed build/lint/signature/alignment evidence
continues to apply because this increment changes tests/documentation only.
The engine package reproduced SHA-256
`6254bb8ad9bf5bf2d9e35c1344124815325258c1b62b63da7a5f9ddfa8682b51`;
source reference b3e59e6 and the installed signed beta.8 remain accurate.
Private audit logs are `/private/tmp/acc-roadmap-audit-{host,android,mutations}.log`.

| Requirement | Inspected authoritative evidence | Completion scope / missing proof |
| --- | --- | --- |
| R0.1 machine outputs and codes | Engine `test_contract.py` executes CLI/info/config/daemon/switch fragments; app `FrontendContractTest`, `PrintedConfigContractTest` parse exact exported bundle fixtures | Contract implemented and exercised; success/failure/unknown remain distinct |
| R0.2 capabilities/schema compatibility | Engine module/contract fixtures; app `NgEngineContractTest` covers missing metadata, legacy API 1, malformed/unknown API/schema and rejects guessed writers | Implemented; API 1/schema 202310160 retained |
| R0.3 old/current/raw profiles | Engine profile rewrite/readback and config transaction fixtures; app legacy JSON/temperature/profile tests; signed update database/prefs report | Implemented; current official three/debug one profiles and all rows retained against session baseline |
| R0.4 synthetic sensors and refusal | Engine contract/sensors/discovery/limits suites cover sign/zero/missing/malformed/scale/refused reads and writes; actual Android fixture invocation passed | Implemented; synthetic behavior does not certify electrical limiting |
| R1.1 conditional resets | Limits tests cover untouched defaults, original snapshots, restart, partial/failed resets; real SIOP 100→30→100 and journal removal | Implemented and SIOP physically proved; successful physical mcc/mcv still missing |
| R1.2 isolated current/voltage application/cache | Discovery/limits/frontend-control tests exercise cache loss, restart defaults, deferred latest requests, aliases and no cross-limit reset | Implemented in fixtures; writable mcc/mcv hardware still required |
| R1.3 owner/mode and OEM control | Limits tests execute permission fallback, restoration failure, owner/mode, target symlinks; real SIOP kept 664/1000:1001 under charging load | Specific restoration proved; independent OEM thermal-policy interaction remains unverified |
| R1.4 saved versus applied readback | Frontend-controls/discovery tests and app PowerLimits/ConfigVerifier/FrontendContract tests; real `500::failed` / `4100::unsupported` evidence | Failure/no-support reporting proved; successful physical current/voltage application missing |
| R2.1 valid candidates/blacklist | Discovery/frontend-control tests reject absent/empty/invalid/blacklisted references and preserve manual choices | Implemented and exercised; discovery never establishes support merely from a filename |
| R2.2 exact OEM switches | Whole OEM-rule execution and deduplication tests in `test_sensors.py`; manual selection preserved | Implemented; other OEM boards not physically certified |
| R2.3 physical sensor selection/units | Sensors suite executes split Nexus interface, absent uevent, explicit units, battery/input distinction, missing protection; Samsung actual sensor-status/current measurements | Implemented; real Samsung mA/µV path verified, other OEM physical paths missing |
| R2.4 incoming-power semantics | Shared external-power engine/app fixtures, own-input-cut unknown and relative-path tests; I7 AC online and new events across cable removal/reconnect | Samsung physical detection proved; own pause does not authorize false calibration |
| R3.1 switch result/codes | Switch probe/quiet/incomplete/reference/restore-failure fixtures; app parser preserves complete success/idle versus errors; physical slate probe | Implemented; tested Samsung pause switch passes without claiming idle support |
| R3.2 bounded/cancellable/concurrent probes | Deadline, TERM, busy mutex, original-OFF restoration and grouped-snapshot tests; app test worker/process cases | Executed synthetic behavior and physical switch restoration proved |
| R3.3 automatic recovery budget | New full-controller case observes two reselections then three disable attempts/error 7, plus recovery log; lock/worker fixtures and final one-daemon check | Bounded behavior proved; no generic claim for every OEM switch |
| R3.4 idle and polarity | Stable three-sample/threshold/sign/manual/zero fixtures; I7 blank→negative calibration at 30 seconds with negative physical battery current | Fresh Samsung calibration proved; manual compatibility retained |
| R3.5 thermal/capacity pause and `r` | Engine actual threshold/transition tests, `r` case, controller cooldown interruption; app TemperatureConfig/ConfigVerifier tests | Implemented and exercised synthetically; no live battery heating required |
| R3.6 cooldown/shutdown | New controller cases observe first-ON-sample interruption at exact capacity/temperature bounds, physical percent/mV shutdown and reconnect cancellation; existing OFF-wait case | Effects/deadlines proved with synthetic data; actual shutdown stubbed |
| R4.1 import/CRLF/named scripts | Config transaction suite executes replacement/addition, case-insensitive names, quoting/extensions and invalid import | Implemented and exercised |
| R4.2 atomic config/recovery | Concurrent writers, failing commits/reset/removal, last-good and invalid-file recovery tests; physical trial config/last-good restoration | Implemented and original files restored byte for byte |
| R4.3 parsers/defaults/units | Config/contract suite and app exact printed output, temperature/config-verification tests; physical original thresholds retained | Implemented; six-field capacity and original defaults/schema retained |
| R4.4 scheduler/DJS | Daily/midnight/quoting/wrapper fixture; DJS archive unchanged, app database rows unchanged with zero existing schedules | Engine scheduler exercised; no pre-existing DJS schedules existed to trigger in this session |
| R5.1 readiness/disable/boot | Actual readiness/disable/150-second grace fixtures and app diagnostics; I6 locked boot with changed boot ID/RUNNING_LOCKED, one daemon and physical OFF | Implemented and Samsung locked boot observed on I6; unchanged boot branch covered by I7 control fixtures |
| R5.2 managers/BusyBox | Explicit Magisk/KSU/APatch manager installation flags and independent BusyBox fixture; actual Samsung Magisk install without reboot | Magisk physically verified; KSU/APatch physical trials absent |
| R5.3 install/staging/rollback | Complete installer fixtures and I6 live clean/upgrade/rollback/reinstall; I7 exact app installer update, 31 runtime hashes and single-daemon health | Samsung paths observed by increment; rollback/failure synthetic cases pass |
| R5.4 secure archive/downloader | Actual TLS/HTTP failure/timeout argument fixture, archive traversal/symlink/root rejection and bundle/GitHub source selection tests | Implemented; NG identities/URLs retained, no insecure transport fallback |
| R5.5 bounded diagnostics | Actual diagnostic exporter fixture verifies 128 KiB log caps, timeout of platform dump, test/sensor/limit context and temp cleanup | Implemented and exercised; live loop does not wait for exporter |
| R7 joint artifact and preservation | Deterministic source archive, bundle hash, 35 contract/31 runtime hashes, beta.8 signed APK equality, XML tests, lint/build/signature/16 KiB checks, data/config proofs and merged PRs | Joint candidate integrated and reviewable; no release or updater promotion |
| R7 physical matrix | I6 clean/legacy upgrade/reboot/capacity/rollback/SIOP load; I7 signed upgrade/cable/fresh polarity/restoration; separate raw results retained | Samsung evidence available. Successful writable mcc/mcv, independent OEM policy and physical KSU/APatch remain absent; full R7 is not complete |
| R6 optional features | Catalog and module capabilities exclude new idle_apps/Encore/percentage-current editor; existing physical-capacity/mask/temporary-charge protections retained | Explicitly outside candidate; not substituted for missing R7 evidence |

The remaining gate needs an attached supported current/voltage-control device,
physical KSU and APatch environments, and independent OEM-policy read/write
observations under normal load. Repeating a rejected Samsung current write or
renaming fixture results would not supply that evidence. The automatic goal
must therefore remain incomplete until suitable hardware or a user-approved
change to the required scope is available. Public release assets/tags and
stable `module.json` stay untouched.
