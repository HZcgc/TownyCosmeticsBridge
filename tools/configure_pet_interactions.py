#!/usr/bin/env python3
"""Prepare a small MCPets/MythicMobs overlay from a stopped server's config copies."""
import argparse
from pathlib import Path
import re

import yaml

INTERACTION_SKILLS = (
    "nm_bryborn_the_bone_dragon_flap",
    "nm_cylly_the_clown_honkshot",
    "nm_cylly_the_clown_honk",
    "nm_myrtle_the_mummy_crush",
    "nm_widge_the_witch_cat_cauldron",
)


def configure_mcpets(text):
    data = yaml.safe_load(text)
    if not isinstance(data, dict):
        raise ValueError("MCPets config must be a mapping")
    for key, value in (("RightClickToOpenMenu", "true"),
                       ("LeftClickToOpenMenu", "false"), ("SneakMode", "true")):
        if not isinstance(data.get(key), bool):
            raise ValueError(f"Missing or invalid MCPets setting: {key}")
        pattern = rf"(?m)^({key}:[ \t]*)(?:true|false)([ \t]*(?:#[^\r\n]*)?)(\r?)$"
        text, count = re.subn(pattern, lambda m: m[1] + value + m[2] + m[3], text)
        if count != 1:
            raise ValueError(f"Expected exactly one setting: {key}")
    return text


def configure_skills(text):
    data = yaml.safe_load(text)
    if not isinstance(data, dict):
        raise ValueError("MythicMobs skills must be a mapping")
    newline = "\r\n" if "\r\n" in text else "\n"
    for name in INTERACTION_SKILLS:
        skill = data.get(name)
        if not isinstance(skill, dict) or not isinstance(skill.get("Skills"), list):
            raise ValueError(f"Missing or invalid animation skill: {name}")
        # Keep sounds, timings, state mechanics, comments and other skills intact.
        pattern = re.compile(rf"(?m)^{re.escape(name)}:[ \t]*\r?\n(?:[ \t].*\r?\n|[ \t]*\r?\n)*")
        blocks = list(pattern.finditer(text))
        if len(blocks) != 1:
            raise ValueError(f"Expected exactly one skill section: {name}")
        match = blocks[0]
        block = match[0]
        conditions = skill.get("TriggerConditions", [])
        if not isinstance(conditions, list):
            raise ValueError(f"Invalid TriggerConditions: {name}")
        if "crouching false" not in conditions:
            header = re.search(r"(?m)^  TriggerConditions:[ \t]*\r?\n", block)
            if "TriggerConditions" in skill and header is None:
                raise ValueError(f"Use a block-style TriggerConditions list: {name}")
            if header:
                block = block[:header.end()] + "  - crouching false" + newline + block[header.end():]
            else:
                end = block.index(newline) + len(newline)
                block = block[:end] + "  TriggerConditions:" + newline + "  - crouching false" + newline + block[end:]
        if name == INTERACTION_SKILLS[0]:
            block = block.replace("  - onground{} true cancel", "  - (onground && offgcd) true")
        text = text[:match.start()] + block + text[match.end():]
    yaml.safe_load(text)  # Validate the entire result before writing anything.
    return text


def configure(config_path, skills_path, output):
    config = configure_mcpets(config_path.read_bytes().decode("utf-8"))
    skills = configure_skills(skills_path.read_bytes().decode("utf-8"))
    # Both sources must pass validation before the output directory is created.
    changes = {output / "plugins/MCPets/config.yml": config,
               output / "plugins/MythicMobs/skills/townyreborn_halloween_pets.yml": skills}
    for path, text in changes.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode("utf-8"))
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mcpets-config", type=Path, required=True)
    parser.add_argument("--mythic-skills", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    changes = configure(args.mcpets_config, args.mythic_skills, args.output)
    print(f"Prepared {len(changes)} files: right-click animation, sneak-right-click MCPets menu")


if __name__ == "__main__":
    main()
