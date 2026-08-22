#!/usr/bin/env python3
"""Validate all Android XML resources are well-formed (real parse check)."""
import sys
import xml.dom.minidom as M
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main"
ok, bad = [], []
for path in ROOT.rglob("*.xml"):
    try:
        M.parse(str(path))
        ok.append(path)
    except Exception as e:
        bad.append((path, str(e)))
print(f"XML files parsed OK: {len(ok)}")
for p in ok:
    print(f"  OK  {p.relative_to(ROOT)}")
if bad:
    print(f"\nINVALID XML: {len(bad)}")
    for p, e in bad:
        print(f"  BAD {p}: {e}")
    sys.exit(1)
print("\nAll XML resources are well-formed.")
