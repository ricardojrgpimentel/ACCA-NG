# Building and releasing AccA-NG

Official package: `com.accang.app`. Debug package: `com.accang.app.debug`.
First public beta: `2.0.0-ng-beta.1`, version code `41`.

## Signing identity

The official GitHub APK signer is an RSA-3072 key named `accang-release`, stored in
a password-protected PKCS12 keystore **outside the repository**. Its public
certificate is [accang-release-certificate.pem](accang-release-certificate.pem).
Certificate SHA-256:

```text
b658f70c0aa5046aaf2a508be344ad882c0293947628a6cf276a3ec8c533ea8d
```

This certificate is public. It cannot sign an APK. The private keystore, its
password and any Base64 backup of it must never be committed or attached to a
GitHub issue/release. Do not create a new release key for each version.

## Configure a signing machine

Install JDK 17 (or a compatible newer JDK), Android SDK platform 36, Build Tools
34.0.0 and a Build Tools version providing `apksigner` and `zipalign`.
Set `JAVA_HOME`, put its `bin` on `PATH` and set `ANDROID_HOME`.

Store these values in a private properties file outside the checkout:

```properties
ACCA_KEYSTORE_LOCATION=/absolute/private/path/accang-release.p12
ACCA_KEYSTORE_PASSWORD=<stored privately>
ACCA_KEY_ALIAS=accang-release
ACCA_KEY_PASSWORD=<stored privately>
```

Point Gradle at that file (the command contains a path, not passwords):

```sh
export ACCA_KEYSTORE_PROPERTIES_LOCATION=/absolute/private/path/keystore.properties
python3 tools/build-release.py
```

Alternatively set all four `ACCA_*` values in the environment. Environment values
take precedence. An incomplete configuration or a missing explicitly selected
properties file fails the build. With no signing configuration, ordinary Gradle
release builds are unsigned, allowing CI and F-Droid to build independently.

The release script runs release unit tests, lint and compilation, verifies the APK
signature against the checked-in certificate fingerprint, checks ZIP alignment,
then writes only public release artifacts under `build/releases/<version>/`:

- `AccA-NG-<version>.apk`
- `SHA256SUMS`
- `accang-release-certificate.pem`
- `signature-verification.txt`

The script never publishes. CI does not have the release signing key.

## Publish a version

1. Increase `versionCode` and set `versionName` in app/build.gradle. Never reuse a
   published version code for a changed APK.
2. Update the bundled engine provenance, Fastlane changelog and release notes.
3. Run the signed build script and inspect test/lint output.
4. On a suitable test device, verify install, root setup and an update over the
   previous release without clearing app data. Preserve user module/profile data.
5. Review the exact Git diff, scan current files and Git history for secrets,
   commit and push, then tag that exact commit as `v<versionName>`.
6. Create a GitHub prerelease with the four public artifacts above and the release
   notes. Independently download and verify the uploaded APK/checksums.

Do not publish debug APKs, device backups or the private signing directory.
Tag/commit source plus the separately linked engine source must remain available
for the corresponding binary release.

## Verify a download

Using Android SDK Build Tools:

```sh
apksigner verify --verbose --print-certs AccA-NG-2.0.0-ng-beta.1.apk
```

Compare `Signer #1 certificate SHA-256 digest` with the fingerprint above.
Verify the release asset checksums with `sha256sum -c SHA256SUMS` on Linux or
`shasum -a 256 -c SHA256SUMS` on macOS.

## Backup and recovery

Keep the complete PKCS12 file, its store/key password, alias and certificate
fingerprint in a password manager. Test a restored copy with `keytool -list` and
compare the certificate fingerprint before relying on the backup. Keep an
additional encrypted offline copy in a separate location.

A complete note in the format generated for this release can be restored with:

```sh
python3 tools/restore-signing-backup.py /private/path/note.txt /private/path/new-signing-directory
```

The destination must not exist. The script verifies the keystore checksum, creates
private files and does not print passwords. Check the restored certificate using
`keytool` before use. Remove the temporary plaintext note after restoring.

If using Bitwarden, a Secure Note can store the password and a Base64 encoding of
the entire small PKCS12 file. Accounts with attachment support can instead attach
the PKCS12 file and keep the password in the same item. A certificate-only backup
cannot restore signing capability. Normal JSON/CSV vault exports do not include
file attachments; back them up separately or use Bitwarden's attachment-inclusive
export option and protect the exported file.

References: [Android signing](https://developer.android.com/studio/publish/app-signing),
[Bitwarden attachments](https://bitwarden.com/help/attachments/),
[Bitwarden exports](https://bitwarden.com/help/export-your-data/).

## Other stores

F-Droid preparation is tracked in [FDROID.md](FDROID.md); inclusion and reproducible
build verification are pending. If using Play App Signing later, plan the signing
identity before enrolment so distribution channels remain compatible. A separate
upload key is different from the app signing key.
