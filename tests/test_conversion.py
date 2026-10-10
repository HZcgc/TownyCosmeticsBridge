import importlib.util
from pathlib import Path
import tempfile
import unittest
import yaml

spec = importlib.util.spec_from_file_location("convert_ec7", Path(__file__).resolve().parents[1] / "tools/convert_ec7.py")
converter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(converter)


class ConversionTest(unittest.TestCase):
    def test_flat_pool_keeps_legacy_rarity_probabilities_and_item_payload(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); source = root / "source"; output = root / "output"
            (source / "crates").mkdir(parents=True); (source / "keys").mkdir()
            def write(path, data): path.write_text(yaml.safe_dump(data))
            item = {"Provider": "vanilla", "Data": {"Value": '{count:1,id:"minecraft:diamond"}', "DataVersion": 4890}}
            write(source / "config.yml", {"Rewards": {"Rarities": {"common": {"Name": "Common", "Weight": 70}, "rare": {"Name": "Rare", "Weight": 30}}}})
            write(source / "keys/demo.yml", {"Virtual": True, "Name": "Demo Key", "ItemData": item})
            rewards = {}
            for rid, rarity, weight in [("a", "common", 1), ("b", "common", 1), ("c", "rare", 9), ("d", "rare", 1)]:
                rewards[rid] = {"Type": "ITEM", "Rarity": rarity, "Weight": weight, "PreviewData": item, "ItemsData": {"0": item}}
            write(source / "crates/demo.yml", {"Name": "Demo", "ItemProvider": item, "Rewards": {"List": rewards},
                  "CostOptions": {"one": {"Entries": {"0": {"Type": "key", "Key": "demo", "Amount": 1}}}},
                  "Block": {"Positions": ["161,81,44,spawn"]}})
            converter.convert(source, output)
            weights = []
            for rid in rewards:
                converted = yaml.safe_load((output / f"objects/reward/demo/{rid}.yml").read_text())
                weights.append(converted["base"]["weight"])
                self.assertEqual(item, converted["items"]["item_list"]["0"])
            self.assertEqual([35, 35, 27, 3], weights)
            crate = yaml.safe_load((output / "objects/crate/demo.yml").read_text())
            self.assertEqual(["161,81,44,0,0"], crate["blocks"]["positions"]["minecraft:spawn"])
            self.assertEqual({"demo": {"amount": 1}}, crate["key_requirements"]["keys"])
            self.assertFalse(crate["hologram"]["enabled"])
            key = yaml.safe_load((output / "objects/key/demo.yml").read_text())
            self.assertTrue(key["base"]["virtual"])
            self.assertFalse(list(output.rglob("*.db")))

    def test_key_rewards_use_ec7_command_and_preserve_hologram_flag(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); source = root / "source"; output = root / "output"
            (source / "crates").mkdir(parents=True); (source / "keys").mkdir()
            item = {"Provider": "vanilla", "Data": {"Value": '{count:1,id:"minecraft:chest"}', "DataVersion": 4890}}
            (source / "config.yml").write_text(yaml.safe_dump({"Rewards": {"Rarities": {
                "common": {"Name": "Common", "Weight": 1}}}}))
            commands = ["crates key give %player_name% vote 3 -sf", "crates key give %player_name% daily 1",
                        "eco give %player_name% 500", "say crates key give is an old command"]
            crate = {"Name": "Demo", "ItemProvider": item,
                     "Rewards": {"List": {"keys": {"Type": "COMMAND", "PreviewData": item, "Commands": commands}}},
                     "CostOptions": {"one": {"Entries": {"0": {"Type": "key", "Key": "vote", "Amount": 1}}}},
                     "Post-Open": {"Commands": ["crates key give %player_name% cosmetic 2 -sf"]}}
            for name, enabled in [("hidden", False), ("visible", True)]:
                crate["Block"] = {"Hologram": {"Enabled": enabled}}
                (source / f"crates/{name}.yml").write_text(yaml.safe_dump(crate))
            converter.convert(source, output)
            for name, enabled in [("hidden", False), ("visible", True)]:
                result = yaml.safe_load((output / f"objects/crate/{name}.yml").read_text())
                self.assertEqual(enabled, result["hologram"]["enabled"])
                self.assertEqual(["cratekey give %player_name% cosmetic 2"], result["opening"]["opening_commands"])
                reward = yaml.safe_load((output / f"objects/reward/{name}/keys.yml").read_text())
                self.assertEqual(["cratekey give %player_name% vote 3", "cratekey give %player_name% daily 1",
                                  *commands[2:]], reward["commands"]["bundles"]["legacy"]["commands"])


if __name__ == "__main__": unittest.main()
