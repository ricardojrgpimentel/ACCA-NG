# Release validation — 2.0.0-ng-beta.4

9 October 2026. Official signed APK, package `com.accang.app`, version code 44.

- 77 release unit tests passed with no failures or errors.
- Release lint completed without errors; existing toolchain warnings remain.
- Translation checks passed across all 21 locales. The eight new script-dialog
  strings were added in English and Portuguese (Portugal); other locales may
  use fallback text for these additions.
- APK signature verified against the official certificate, SHA-256
  `b658f70c0aa5046aaf2a508be344ad882c0293947628a6cf276a3ec8c533ea8d`.
- ZIP alignment passed. Bundled ACC-NG and DJS archives still match the existing
  provenance checksums; neither archive changed.
- Current source and all 1,082 existing Git revisions were scanned for common
  private-key and access-token patterns, with no matches. Private signing
  material stays outside the repository and release artifacts.
- Release APK metadata reports `AccA-NG`, package `com.accang.app`, version
  `2.0.0-ng-beta.4` and code 44. Debug branding is scoped to `app/src/debug/res`.

## Device checks

Samsung SM-G975F running Android API 36, previously using beta.3 / code 43:

- Updated with `adb install -r` without clearing data; installation succeeded.
- The dashboard still displayed the active profile "Cool down after 60%", with
  its existing charge, temperature and cooldown settings.
- Opened Scripts and tapped "ACC Version". The dialog displayed the description,
  `acca -v`, the execution explanation and an enabled Run button. No output or
  running indicator appeared before the explicit action.
- Pressed Run. The dialog displayed `v1.0.3-ng (202610093)`, a successful exit
  code 0 and Close. Run was hidden after completion; Close returned to the list.
- Local database snapshots before and after this read-only script test confirmed
  all three profiles, the empty schedules table and all script definitions were
  unchanged. The script's stored output and exit code matched the displayed result.
- Read-only shell checks separately passed for ACC command lookup by name,
  quoted multiline bodies, pipelines and preservation of an explicit exit code 23.

The app-data snapshots and UI XML captures remain in ignored
`app/build/device-backups/beta4-validation/`; they are not release assets.

Rotation during execution, failure and timeout dialogs, older Android versions,
complete charging cycles and physical thermal protection were not physically
tested for this release. Prior engine/profile validation remains documented in
the earlier release records. This remains a prerelease.
