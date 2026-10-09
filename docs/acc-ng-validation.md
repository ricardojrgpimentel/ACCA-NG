# ACC-NG integration validation — 2026-10-09

Engine repository: https://github.com/ricardojrgpimentel/ACC-NG
Test release: v1.0.2-ng (202610092), based on ACC v2023.10.16.

The app bundle is the release tarball built with `python3 tools/build_ng.py`.
Its SHA-256 is `4f70e4ec67116221169f8d0d22dd009b0d8da1c48237283ce694fddc24379ffd`.
The Magisk ID stays `acc`, so migration replaces the existing engine and
preserves scheduler/runtime paths. The displayed name is ACC-NG, with a generic
description; charging thresholds belong to app profiles.

## Verification

- 12 engine tests passed: dynamic pause/resume limits, thermal pause/resume,
  notification deduplication, real connection/state observations, unknown sensor
  handling, disabled notices, global preferences preserved on profile writes,
  and selection of bundled/GitHub branch/tag archive folders.
- `assembleDebug`, all 56 app unit tests and `lintDebug` passed (no lint errors).
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
- Final device check: ACC-NG v1.0.2-ng, one active daemon, all 27 installed top-level
  shell files identical to source, and the installed APK identical to the local APK.

The engine automatically selected `battery/charging_enabled 1 0` on this kernel.
Original configuration remains pause 60%, resume 50%, shutdown 2%; the active
profile remains “Muitas horas no carregador”. Existing calibrated polarity and
other settings were preserved.

## Scope

This is a prerelease verified on this Samsung/kernel combination. Boot persistence,
a physical unplug/replug notification cycle and other devices have not been tested.
Thermal thresholds were tested with isolated fixtures; the physical battery was
not heated or its sensor spoofed. Critical original ACC control paths remain intact.
