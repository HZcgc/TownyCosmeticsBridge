"""Check the delivered JARs, including the descriptor actually used by Paper."""
import hashlib
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import zipfile

import yaml

ROOT = Path(__file__).resolve().parents[1]


def project_version(project):
    return ET.parse(ROOT / project / "pom.xml").getroot().findtext(
        "{http://maven.apache.org/POM/4.0.0}version")


class ReleasePackagingTest(unittest.TestCase):
    def setUp(self):
        self.version = project_version("excellentcrates-patch")
        self.patch = ROOT / f"dist/ExcellentCrates-{self.version}.jar"

    def test_paper_and_bukkit_report_the_same_patch_version(self):
        with zipfile.ZipFile(self.patch) as jar:
            for name in ["plugin.yml", "paper-plugin.yml"]:
                description = yaml.safe_load(jar.read(name))
                self.assertEqual("ExcellentCrates", description["name"])
                self.assertEqual(self.version, description["version"])

    def test_native_overlay_only_changes_storage_classes_and_both_descriptors(self):
        with zipfile.ZipFile(ROOT / "build/deps/ExcellentCrates.jar") as original, zipfile.ZipFile(self.patch) as patched:
            changed = {name for name in original.namelist() if original.read(name) != patched.read(name)}
            self.assertEqual({"plugin.yml", "paper-plugin.yml",
                              "su/nightexpress/excellentcrates/keys/storage/db/KeyStorageDBSchema.class",
                              "su/nightexpress/excellentcrates/keys/storage/db/KeyStorageCachedDataService.class"}, changed)
            self.assertIn("META-INF/TOWNY-KEY-PATCH-LICENSE", patched.namelist())

    def test_key_bundle_contains_one_jar_per_plugin_and_matching_checksums(self):
        hunt_version = project_version("integrations/TownyRebornHunt")
        with zipfile.ZipFile(ROOT / "dist/TownyReborn-Crate-Key-Fix.zip") as bundle:
            jars = {name for name in bundle.namelist() if name.endswith(".jar")}
            self.assertEqual({f"plugins/ExcellentCrates-{self.version}.jar",
                              f"plugins/TownyRebornHunt-{hunt_version}.jar"}, jars)
            sums = bundle.read("SHA256SUMS").decode().splitlines()
            self.assertEqual(len(jars), len(sums))
            for line in sums:
                digest, path = line.split("  ", 1)
                self.assertIn(path, jars)
                self.assertEqual(digest, hashlib.sha256(bundle.read(path)).hexdigest())
            self.assertFalse(any(name.endswith((".db", ".properties")) for name in bundle.namelist()))

    def test_pet_update_preserves_live_configs_and_ships_the_new_defaults_in_the_jar(self):
        version = project_version("integrations/TownyRebornPetShops")
        jar_path = f"plugins/TownyRebornPetShops-{version}.jar"
        with zipfile.ZipFile(ROOT / f"dist/TownyReborn-Petshops-{version}.zip") as bundle:
            self.assertEqual({jar_path}, {name for name in bundle.namelist() if name.startswith("plugins/")})
            self.assertIn("examples/TownyRebornPetShops/config.yml", bundle.namelist())
            self.assertFalse(any(name.endswith(("npcs.yml", "transactions.yml")) or "/shops/" in name
                                 for name in bundle.namelist()))
            self.assertEqual((ROOT / f"dist/TownyRebornPetShops-{version}.jar").read_bytes(), bundle.read(jar_path))
        with zipfile.ZipFile(ROOT / f"dist/TownyRebornPetShops-{version}.jar") as jar:
            self.assertEqual(version, yaml.safe_load(jar.read("plugin.yml"))["version"])
            defaults = yaml.safe_load(jar.read("config.yml"))
            self.assertEqual(75000, defaults["defaults"]["ingame-price"])
            self.assertEqual(10.99, defaults["defaults"]["store-price"])
            self.assertEqual(64, defaults["npc"]["render-distance"])
            self.assertTrue(any("Left-click" in line for line in defaults["hologram"]["lines"]))
            self.assertTrue(any("Right-click" in line for line in defaults["hologram"]["lines"]))
            self.assertFalse(any(name.startswith("com/ticxo/") for name in jar.namelist()))


if __name__ == "__main__":
    unittest.main()
