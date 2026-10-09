# Release validation — 2.0.0-ng-beta.1

Date: 9 October 2026. Package `com.accang.app`, version code 41.

- Gitleaks 8.30.1 scanned all 998 pre-release commits reachable from local refs
  and the candidate public source snapshot, including supported nested archives;
  no secrets were reported. Automated scanning is not an exhaustive security audit.
- 67 release unit tests passed, including 7 shell-fixture regression tests for
  ACC-NG/DJS archive selection and module-specific installer cleanup.
- `lintRelease` passed with 0 errors and 469 warnings. Existing lint warnings and
  Gradle deprecations have not all been resolved.
- Signed release compilation succeeded; `apksigner verify` passed with the
  published certificate fingerprint and `zipalign -c -P 16 4` passed.
- An unsigned release also compiled with no signing configuration, supporting
  independent builds and CI without the private key.
- The signed beta installed on Samsung SM-G975F. Replacing it with the same signed
  APK using `adb install -r` succeeded. Package metadata confirms version code 41,
  min SDK 26, target SDK 36 and a non-debuggable release. This checks package-manager
  acceptance; it is not an upgrade test from a prior public version or a new test
  of engine runtime behaviour. The existing debug app was retained.
- A copy of the complete Bitwarden note was decoded in a private temporary
  directory. The keystore bytes and certificate fingerprint matched, and the
  recovered password successfully decrypted/imported the private-key entry. The
  temporary recovered keys were removed. Uploading the backup to the user's
  Bitwarden vault still needs to be done by the user.

See ../acc-ng-validation.md for the earlier physical engine validation. This
release does not extend its hardware coverage or claim F-Droid reproducibility.
