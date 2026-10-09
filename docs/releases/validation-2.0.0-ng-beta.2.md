# Device validation — 2.0.0-ng-beta.2

9 October 2026. Local signed candidate, version code 42; not yet published.

The beta.1 command trace for the seeded "Cool down after 60%" profile showed
successful setters followed by failed configuration readback. The app sent
`max_temp_pause=90`, but ACC-NG v1.0.3-ng uses `resume_temp`; its compatibility
printout mapped the current 45°C resume temperature to the legacy pause field.
The legacy write was ignored, so 90 seconds could never match that readback.

An isolated configuration copy on Samsung SM-G975F reproduced the ignored legacy
field. The live configuration checksum was unchanged by that experiment.

The app now stores resume temperature separately from legacy pause seconds,
selects modern semantics for ACC versions >= 202308120 and uses a five-degree
hysteresis for old profiles without an explicit resume temperature. For this
profile that means cooldown 40°C, pause 45°C, resume 40°C. Legacy engines retain
pause-second semantics. The optional resume_temp `r` suffix is preserved.
Modern setters are run in the foreground so failures are not hidden in workers.

Verification:

- 74 release unit tests passed, including old JSON compatibility, modern/legacy
  commands, readback matching, cooldown mismatch detection and resume overrides.
- Release lint passed without errors; existing warnings remain.
- Signed APK verification passed using the existing official certificate.
- Installed over beta.1 without clearing app data or reinstalling the module.
- Applying "Cool down after 60%" through the actual app reported success and
  marked the profile active. A repeated apply also succeeded.
- Final engine values: capacity=(5 60 70 80 false false), cooldownRatio=(50 10),
  temperature=(40 45 40 55).
- Expanded the successful action details, verified the layout on the device and
  tapped the new "Copiar detalhes" button. It copies the full displayed action
  trace plus app/API version and result to the Android clipboard.

This verifies profile application and readback, not a full physical cooldown or
thermal-protection cycle. GitHub beta.1 assets were not replaced.
