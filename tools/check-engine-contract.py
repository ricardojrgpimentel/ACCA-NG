#!/usr/bin/env python3
"""Ensure frontend fixtures describe the exact bundled engine, without installing it."""
import hashlib
import json
from pathlib import Path
import re
import tarfile

ROOT = Path(__file__).resolve().parents[1]
fixtures = json.loads((ROOT / 'app/src/test/resources/acc-ng/frontend-contract.json').read_text())
with tarfile.open(ROOT / 'app/src/main/res/raw/acc_bundle', 'r:gz') as archive:
    members = archive.getmembers()
    module = next(m for m in members if m.name.endswith('/module.prop'))
    prefix = module.name.removesuffix('module.prop')
    metadata = archive.extractfile(module).read().decode().strip()
    if metadata != fixtures['module']:
        raise SystemExit('Bundled module metadata differs from frontend fixtures. Re-export from the engine.')
    for name, expected in fixtures['sources'].items():
        actual = hashlib.sha256(archive.extractfile(prefix + name).read()).hexdigest()
        if actual != expected:
            raise SystemExit(f'Bundled {name} differs from tested frontend fixture source.')
    source = archive.extractfile(prefix + 'install/external-power.sh').read().decode()
    generated = (ROOT / 'app/src/main/java/mattecarra/accapp/acc/GeneratedExternalPower.kt').read_text()
    literal = re.search(r'const val source = (".*")', generated).group(1)
    if json.loads(literal.replace(r'\$', '$')) != source:
        raise SystemExit('Frontend external-power helper differs from bundled engine source.')
props = dict(line.split('=', 1) for line in metadata.splitlines() if '=' in line)
version = re.search(r'const val bundledVersion = (\d+)',
    (ROOT / 'app/src/main/java/mattecarra/accapp/acc/Acc.kt').read_text()).group(1)
if version != props['versionCode']:
    raise SystemExit('Acc.bundledVersion differs from bundled module.')
print(f"Frontend fixtures match bundled {props['version']} ({version}).")
