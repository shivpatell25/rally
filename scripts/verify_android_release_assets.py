#!/usr/bin/env python3
"""Verify release downloads for both old and current Rally updaters."""
import argparse
import json
import subprocess
from pathlib import Path


def verify_assets(assets, tag, expected):
    migration = f"rally-{tag}-android-tv.apk"
    legacy = f"rally-{tag}-android-tv-legacy.apk"
    alias = "rally-android-tv.apk"
    by_name = {asset["name"]: asset for asset in assets}
    for name in (migration, legacy, alias):
        if name not in by_name or name not in expected:
            raise ValueError(f"Missing verified APK: {name}")
        asset = by_name[name]
        if asset.get("state") != "uploaded" or asset.get("size", 0) <= 0:
            raise ValueError(f"Incomplete APK: {name}")
        if asset.get("digest") != f"sha256:{expected[name]}":
            raise ValueError(f"Published checksum mismatch: {name}")
    if expected[alias] != expected[migration]:
        raise ValueError("Compatibility download differs from the migration APK")
    # Beta 10/11 accepts every .apk and keeps the first asset on a version tie.
    # GitHub currently orders assets by name; check its actual API response too.
    for ordering in (assets, sorted(assets, key=lambda asset: asset["name"].lower())):
        first = next((a["name"] for a in ordering if a["name"].lower().endswith(".apk")), None)
        if first not in (alias, migration):
            raise ValueError(f"Older Rally updaters would choose an incompatible APK: {first}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repo", default="shivpatell25/rally")
    parser.add_argument("--checksums", type=Path, default=Path("SHA256SUMS"))
    args = parser.parse_args()
    expected = {}
    for line in args.checksums.read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        expected[name.lstrip("*")] = digest
    # This also supports draft releases while all assets are being verified.
    view = json.loads(subprocess.check_output([
        "gh", "release", "view", args.tag, "--repo", args.repo, "--json", "apiUrl"
    ]))
    release = json.loads(subprocess.check_output(["gh", "api", view["apiUrl"]]))
    verify_assets(release["assets"], args.tag, expected)
    print("Verified migration, legacy and compatibility APK digests; old updater selects migration.")


if __name__ == "__main__":
    main()
