"""Verify complete offline catalogs and positional argument parity. No external dependencies."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
folders = ["values", "values-en", "values-b+zh+Hant", "values-b+zh+Hans"]
catalogs = {}
for folder in folders:
    entries = ET.parse(root / folder / "strings.xml").getroot().findall("string")
    values = {x.attrib["name"]: "".join(x.itertext()) for x in entries}
    assert len(values) == len(entries), f"Duplicate string key in {folder}"
    assert all(value.strip('"').strip() for value in values.values()), f"Empty value in {folder}"
    catalogs[folder] = values
baseline = catalogs["values"]
for folder, values in catalogs.items():
    assert values.keys() == baseline.keys(), f"Missing or extra keys in {folder}"
    for key, value in values.items():
        assert sorted(re.findall(r"%[0-9]+\$[sd]", value)) == sorted(re.findall(r"%[0-9]+\$[sd]", baseline[key])), f"Argument mismatch: {folder}/{key}"
assert not any(re.search("[\u3400-\u9fff]", v) for v in baseline.values()), "Chinese text leaked into English fallback"
print(f"Translation check passed: {len(baseline)} keys across {len(folders)} complete catalogs")
