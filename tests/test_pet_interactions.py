import importlib.util
from pathlib import Path
import tempfile
import unittest

import yaml

spec = importlib.util.spec_from_file_location(
    "configure_pet_interactions", Path(__file__).resolve().parents[1] / "tools/configure_pet_interactions.py")
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


class PetInteractionsTest(unittest.TestCase):
    CONFIG = """# Keep this comment and spelling.
RightClickToOpenMenu: false # custom comment
LeftClickToOpenMenu: true
SneakMode: false
Prefix: '§dTowny Reborn'
MySQL:
  Host: localhost
  Password: 'unchanged'
BlackListedWorlds: [eventworld]
"""

    @staticmethod
    def skills():
        return "".join(
            f"{name}:\n  Conditions:\n  - "
            + ("onground{} true cancel" if index == 0 else "(onground && offgcd) true")
            + f"\n  Skills:\n  - state{{state=animation{index}}} @self\n  - delay {index + 5}\n"
            for index, name in enumerate(patcher.INTERACTION_SKILLS)
        ) + "unrelated:\n  Skills:\n  - sound{s=entity.cat.ambient} @self\n"

    def test_overlay_preserves_all_other_settings_and_animation_mechanics(self):
        before = yaml.safe_load(self.CONFIG)
        after_text = patcher.configure_mcpets(self.CONFIG)
        after = yaml.safe_load(after_text)
        for key in set(before) - {"RightClickToOpenMenu", "LeftClickToOpenMenu", "SneakMode"}:
            self.assertEqual(before[key], after[key])
        self.assertIn("# Keep this comment and spelling.", after_text)
        self.assertIn("# custom comment", after_text)
        skills_before = yaml.safe_load(self.skills())
        skills_after = yaml.safe_load(patcher.configure_skills(self.skills()))
        self.assertEqual(skills_before["unrelated"], skills_after["unrelated"])
        for name in patcher.INTERACTION_SKILLS:
            self.assertEqual(skills_before[name]["Skills"], skills_after[name]["Skills"])
            self.assertEqual(["crouching false"], skills_after[name]["TriggerConditions"])

    def test_repeated_patches_keep_existing_trigger_conditions_and_line_endings(self):
        skills = self.skills().replace("  Skills:\n", "  TriggerConditions:\n  - isplayer true\n  Skills:\n")
        skills = skills.replace("\n", "\r\n")
        once = patcher.configure_skills(skills)
        self.assertEqual(once, patcher.configure_skills(once))
        self.assertNotIn("\n", once.replace("\r\n", ""))
        for name in patcher.INTERACTION_SKILLS:
            self.assertIn("isplayer true", yaml.safe_load(once)[name]["TriggerConditions"])
        config = patcher.configure_mcpets(self.CONFIG)
        self.assertEqual(config, patcher.configure_mcpets(config))

    def test_missing_pet_skill_does_not_write_a_partial_configuration(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            config = root / "config.yml"
            skills = root / "skills.yml"
            config.write_text(self.CONFIG, encoding="utf-8")
            skills.write_text("unrelated:\n  Skills: []\n", encoding="utf-8")
            output = root / "overlay"
            with self.assertRaises(ValueError):
                patcher.configure(config, skills, output)
            self.assertFalse(output.exists())
            self.assertEqual(self.CONFIG, config.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
