#!/usr/bin/env python3
"""Build the complete HQ 5.2.1 webspace update, excluding user configuration/data."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = "TownyReborn-StaffHQ-5.2.1-API9.zip"
EXCLUDED = {"config/config.php", "config/notifications.php"}


def build(destination: Path) -> tuple[int, str]:
    entries: dict[str, bytes] = {}
    web = ROOT / "source/web"
    for path in sorted(web.rglob("*")):
        relative = path.relative_to(web).as_posix()
        if path.is_symlink():
            raise ValueError(f"Unexpected symlink: {relative}")
        if not path.is_file() or relative in EXCLUDED or relative.split("/")[0] == "storage":
            continue
        entries["WEBSPACE-UPDATE/" + relative] = path.read_bytes()
    for archive_name, source in {
        "INSTALL-DE.md": "docs/INSTALL-5.2.1-DE.md",
        "UI-CHANGES.md": "docs/UI-CHANGES-5.2.1.md",
        "VALIDATION.md": "docs/VALIDATION-5.2.1.md",
        "STAFF-GUIDE.md": "docs/STAFF-GUIDE.md",
        "ROLE-PERMISSIONS.md": "docs/ROLE-PERMISSIONS.md",
    }.items():
        entries[archive_name] = (ROOT / source).read_bytes()
    entries["SHA256SUMS.txt"] = "".join(
        f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in sorted(entries.items())
    ).encode("utf-8")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 15, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    with zipfile.ZipFile(destination) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f"Corrupt ZIP entry: {bad}")
        if any(name.endswith(".jar") or "/storage/" in name for name in archive.namelist()):
            raise ValueError("Private data or plugin binary entered the web update")
    return len(entries), hashlib.sha256(destination.read_bytes()).hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "output" / NAME)
    args = parser.parse_args()
    count, digest = build(args.output.resolve())
    print(f"{args.output.resolve()} — {count} entries — SHA256 {digest}")
