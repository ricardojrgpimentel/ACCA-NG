# Release validation — 2.0.0-ng-beta.3

9 October 2026. Official signed APK, package `com.accang.app`, version code 43.

- 77 release unit tests passed with no failures or errors.
- Release lint completed without errors. Existing warnings and a language-split
  detection warning remain; the effective Gradle configuration was separately
  checked and `android.bundle.language.enableSplit` is false.
- All 21 existing locales contain all 230 new strings checked by the translation
  validator, with no placeholder, newline or duplicate-key errors. Older gaps
  remain in some languages, and AI-assisted drafts need native-speaker review.
- APK signature verified against the checked-in official certificate, SHA-256
  `b658f70c0aa5046aaf2a508be344ad882c0293947628a6cf276a3ec8c533ea8d`.
- ZIP alignment passed. Bundled ACC-NG and DJS checksums match the existing
  provenance record; these archives were not modified.
- Current source files and Git history were scanned for common private-key and
  access-token patterns, with no matches. Private signing material remains
  outside the repository and is not included in release artifacts.

## Device test

Samsung SM-G975F running Android API 36, previously using the local beta.2 APK:

- Updated with `adb install -r`, without clearing app data. Installation succeeded
  and the installed package reports version 2.0.0-ng-beta.3 / code 43.
- Opened the dashboard; the existing active profile "Cool down after 60%" and its
  configured charge and temperature limits remained visible.
- The existing app language was Polish. Selected Portuguese using the app's
  language picker; the settings screen and selected-language summary updated
  immediately. Android reported locale `pt`; the process ID was unchanged.
- The crash buffer contained no crash records after the test.
- Restored the original Polish app locale through Android's locale manager.

This does not validate older Android versions, every language, cold-start
persistence, a complete charging cycle or physical thermal protection. Previous
profile-application checks are recorded in validation-2.0.0-ng-beta.2.md.
