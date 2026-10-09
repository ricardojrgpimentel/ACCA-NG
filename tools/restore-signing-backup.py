#!/usr/bin/env python3
"""Restore a Bitwarden secure-note backup into a NEW private directory."""
import base64
import hashlib
import os
from pathlib import Path
import sys

if len(sys.argv) != 3:
    sys.exit("Usage: python3 tools/restore-signing-backup.py note.txt NEW_PRIVATE_DIRECTORY")
text = Path(sys.argv[1]).read_text()
marker = 'Complete keystore backup (Base64; decode to accang-release.p12):'
if text.count(marker) != 1:
    sys.exit("Expected an AccA-NG complete signing backup note.")
fields = dict(line.split(': ', 1) for line in text.split(marker)[0].splitlines() if ': ' in line)
blob = base64.b64decode(''.join(text.split(marker)[1].split()), validate=True)
if hashlib.sha256(blob).hexdigest() != fields['Keystore SHA-256']:
    sys.exit("Backup checksum mismatch; nothing written.")
expected = (Path(__file__).resolve().parents[1] / 'docs/signing-certificate-sha256.txt').read_text().strip()
if fields['Certificate SHA-256'] != expected or fields['Alias'] != 'accang-release':
    sys.exit("Backup identity differs from the official release key; nothing written.")
# This format uses URL-safe randomly generated passwords: reject property injection.
for value in [fields['Store password'], fields['Key password']]:
    if not value or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in value):
        sys.exit('Unexpected password encoding; restore manually.')
os.umask(0o077)
out = Path(sys.argv[2]).expanduser().resolve()
if '\n' in str(out) or '\\' in str(out):
    sys.exit('Choose a simple absolute destination path.')
out.mkdir(mode=0o700, parents=True, exist_ok=False)
(out / 'accang-release.p12').write_bytes(blob)
(out / 'password.txt').write_text(fields['Store password'] + '\n')
(out / 'keystore.properties').write_text(
    f"ACCA_KEYSTORE_LOCATION={out}/accang-release.p12\n"
    f"ACCA_KEYSTORE_PASSWORD={fields['Store password']}\n"
    f"ACCA_KEY_ALIAS={fields['Alias']}\n"
    f"ACCA_KEY_PASSWORD={fields['Key password']}\n")
print(f"Restored to {out}. Verify the certificate with keytool before signing.")
