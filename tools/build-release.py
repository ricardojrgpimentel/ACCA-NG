#!/usr/bin/env python3
"""Build and verify a locally signed release. Never publishes or exports private keys."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
if not os.environ.get("ACCA_KEYSTORE_PROPERTIES_LOCATION") and not (ROOT / "keystore.properties").is_file():
    required = ("ACCA_KEYSTORE_LOCATION", "ACCA_KEYSTORE_PASSWORD", "ACCA_KEY_ALIAS", "ACCA_KEY_PASSWORD")
    if not all(os.environ.get(name) for name in required):
        sys.exit("Configure release signing first; see docs/RELEASING.md.")
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk:
    sys.exit("Set ANDROID_HOME to your Android SDK directory.")
versions = [p for p in (Path(sdk) / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", p.name)]
if not versions:
    sys.exit("Install Android SDK build-tools first.")
build_tools = max(versions, key=lambda p: tuple(map(int, p.name.split('.'))))
subprocess.run(["./gradlew", "--no-daemon", ":app:testReleaseUnitTest", ":app:lintRelease", ":app:assembleRelease"], check=True)
apk_dir = ROOT / "app/build/outputs/apk/release"
metadata = json.loads((apk_dir / "output-metadata.json").read_text())
if metadata["applicationId"] != "com.accang.app":
    sys.exit("Unexpected application ID")
if len(metadata["elements"]) != 1:
    sys.exit("Expected one universal APK")
element = metadata["elements"][0]
apk = apk_dir / element["outputFile"]
verified = subprocess.run([str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], check=True, capture_output=True, text=True).stdout
expected = (ROOT / "docs/signing-certificate-sha256.txt").read_text().strip().lower()
actual = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\w+)$", verified, re.M)
if actual != [expected]:
    sys.exit("APK does not match the official release certificate")
subprocess.run([str(build_tools / "zipalign"), "-c", "-P", "16", "4", str(apk)], check=True)
version = element["versionName"]
if not re.fullmatch(r"[A-Za-z0-9.-]+", version):
    sys.exit("Unsafe version name")
out = ROOT / "build/releases" / version
out.mkdir(parents=True, exist_ok=True)
name = f"AccA-NG-{version}.apk"
shutil.copy2(apk, out / name)
shutil.copy2(ROOT / "docs/accang-release-certificate.pem", out)
(out / "SHA256SUMS").write_text("".join(f"{hashlib.sha256((out / n).read_bytes()).hexdigest()}  {n}\n" for n in [name, "accang-release-certificate.pem"]))
(out / "signature-verification.txt").write_text(verified)
print(f"Verified release: {out}")
