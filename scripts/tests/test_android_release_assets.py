import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "release_assets", Path(__file__).parents[1] / "verify_android_release_assets.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseAssetsTest(unittest.TestCase):
    tag = "v1.0-beta12"
    migration = f"rally-{tag}-android-tv.apk"
    legacy = f"rally-{tag}-android-tv-legacy.apk"
    alias = "rally-android-tv.apk"

    def setUp(self):
        self.expected = {self.migration: "a" * 64, self.alias: "a" * 64, self.legacy: "b" * 64}
        self.assets = [
            {"name": name, "digest": f"sha256:{self.expected[name]}", "state": "uploaded", "size": 32}
            for name in (self.alias, self.legacy, self.migration)
        ]

    def test_accepts_compatible_download_and_both_current_platform_variants(self):
        module.verify_assets(self.assets, self.tag, self.expected)

    def test_rejects_original_legacy_first_release(self):
        with self.assertRaisesRegex(ValueError, "Missing verified APK"):
            module.verify_assets(self.assets[1:], self.tag, self.expected)

    def test_rejects_wrong_api_order_even_with_an_alias(self):
        with self.assertRaisesRegex(ValueError, "incompatible APK"):
            module.verify_assets(self.assets[1:] + self.assets[:1], self.tag, self.expected)

    def test_rejects_a_different_apk_under_the_compatibility_name(self):
        self.expected[self.alias] = "c" * 64
        self.assets[0]["digest"] = "sha256:" + "c" * 64
        with self.assertRaisesRegex(ValueError, "differs from"):
            module.verify_assets(self.assets, self.tag, self.expected)

    def test_rejects_corrupted_or_stale_published_bytes(self):
        self.assets[1]["digest"] = "sha256:" + "c" * 64
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            module.verify_assets(self.assets, self.tag, self.expected)

    def test_rejects_an_extra_apk_that_old_updaters_would_select(self):
        self.assets.insert(0, {"name": "app-debug.apk"})
        with self.assertRaisesRegex(ValueError, "incompatible APK"):
            module.verify_assets(self.assets, self.tag, self.expected)


if __name__ == "__main__":
    unittest.main()
