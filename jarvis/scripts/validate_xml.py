#!/usr/bin/env python3
"""Validate all Android XML resources are well-formed (real parse check)."""
import sys, glob, xml.dom.minidom as M

ROOT = "/home/saif/Downloads/raphael-ai-assistant-main/jarvis/android/app/src/main"
ok, bad = [], []
for path in glob.glob(f"{ROOT}/**/*.xml", recursive=True):
    try:
        M.parse(path)
        ok.append(path)
    except Exception as e:
        bad.append((path, str(e)))
print(f"XML files parsed OK: {len(ok)}")
for p in ok:
    print(f"  OK  {p.replace(ROOT+'/','')}")
if bad:
    print(f"\nINVALID XML: {len(bad)}")
    for p, e in bad:
        print(f"  BAD {p}: {e}")
    sys.exit(1)
print("\nAll XML resources are well-formed.")
