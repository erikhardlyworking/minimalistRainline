#!/usr/bin/env python3
"""Check complete Android translations and compatible formatting arguments; no dependencies."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

resources = Path(__file__).resolve().parents[1] / "app/src/main/res"


def entries(path):
    result = {}
    for node in ET.parse(path).getroot():
        if node.tag != "string":
            continue
        name = node.attrib["name"]
        assert name not in result, f"Duplicate {name} in {path}"
        result[name] = node
    return result


def arguments(value):
    return sorted(re.findall(r"%\d+\$[.\d]*[sdf]", value or ""))


base = entries(resources / "values/strings.xml")
required = {name for name, node in base.items() if node.get("translatable") != "false"}
for language in ("nb", "sv", "fi", "da"):
    translated = entries(resources / f"values-{language}/strings.xml")
    assert set(translated) == required, f"{language}: missing/extra keys {set(translated) ^ required}"
    for name in required:
        assert translated[name].text, f"Empty {language}/{name}"
        assert arguments(translated[name].text) == arguments(base[name].text), f"Formatting mismatch: {language}/{name}"
    print(f"{language}: {len(required)} complete translations; formatting arguments match")
