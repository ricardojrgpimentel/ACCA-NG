#!/usr/bin/env python3
"""Check new AccA-NG translations; report inherited translation gaps separately."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")
LITERALS = {
    "ng_engine_main": ("(main)",),
    "ng_setup_message": ("Advanced Charging Controller NG", "Magisk", "ZIP"),
}


def read(directory):
    resources = {}
    for path in sorted(directory.glob("strings*.xml")):
        for element in ET.parse(path).getroot():
            name = element.get("name")
            if not name:
                continue
            if name in resources:
                raise ValueError(f"{directory.name}: duplicate {name}")
            resources[name] = (path, element)
    return resources


def main():
    source = read(RES / "values")
    required = {
        name for name, (path, element) in source.items()
        if element.get("translatable") != "false"
        and (path.name != "strings.xml" or name.startswith("ng_engine_")
             or name == "use_default_config")
    }
    errors = []
    locales = 0
    for directory in sorted(RES.glob("values-*")):
        translated = read(directory)
        if not translated:
            continue  # Empty legacy locale directories use the English fallback.
        locales += 1
        missing = required - translated.keys()
        for name in sorted(missing):
            errors.append(f"{directory.name}: missing {name}")
        for name in sorted(required & translated.keys()):
            original = source[name][1]
            localized = translated[name][1]
            text = "".join(localized.itertext())
            original_text = "".join(original.itertext())
            if not text.strip():
                errors.append(f"{directory.name}: empty {name}")
            if localized.get("formatted") != original.get("formatted"):
                errors.append(f"{directory.name}: formatting attribute differs for {name}")
            if original.get("formatted") != "false":
                if Counter(FORMAT.findall(text)) != Counter(FORMAT.findall(original_text)):
                    errors.append(f"{directory.name}: format arguments differ for {name}")
            if text.count(r"\n") != original_text.count(r"\n"):
                errors.append(f"{directory.name}: line breaks differ for {name}")
            for literal in LITERALS.get(name, ()):
                if literal not in text:
                    errors.append(f"{directory.name}: missing literal {literal!r} in {name}")
        inherited = sum(
            name not in translated and name not in required
            and element.get("translatable") != "false"
            for name, (_, element) in source.items()
        )
        print(f"{directory.name}: {len(required) - len(missing)}/{len(required)} new; "
              f"{inherited} inherited resources still use English")
    for error in errors:
        print(error, file=sys.stderr)
    print(f"Checked {locales} locales; {len(errors)} errors.")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
