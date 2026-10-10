#!/usr/bin/env python3
"""Convert legacy ExcellentCrates objects; never copies or modifies player databases."""
import argparse
from pathlib import Path
import yaml


def cooldown(enabled=False, mode="CUSTOM", value=0):
    return dict(enabled=bool(enabled), mode=mode, value=int(value))


def convert(source: Path, destination: Path):
    generated = {}
    config = yaml.safe_load((source / "config.yml").read_text())
    rarities = config["Rewards"]["Rarities"]
    for path in sorted((source / "keys").glob("*.yml")):
        old = yaml.safe_load(path.read_text())
        generated[f"key/{path.name}"] = {
            "base": {"virtual": old.get("Virtual", False)},
            "display": {"name": old.get("Name", path.stem), "lore": []},
            "item": {"item": old["ItemData"], "stackable": old.get("ItemStackable", True),
                     "inherit_display_settings": True},
        }
    for path in sorted((source / "crates").glob("*.yml")):
        old = yaml.safe_load(path.read_text())
        rewards = old["Rewards"]["List"]
        # EC6 first rolls the rarity, then a reward within it. EC7 rolls a flat pool.
        # Normalize each rarity pool so the resulting probabilities remain identical.
        totals = {}
        for reward in rewards.values():
            rarity = reward.get("Rarity", "common")
            totals[rarity] = totals.get(rarity, 0) + max(0, reward.get("Weight", 1))
        limited_rarities = {r.get("Rarity", "common") for r in rewards.values()
                            if r.get("Limits", {}).get("Enabled", False)}
        if limited_rarities and len(totals) > 1:
            raise ValueError(f"{path}: mixed rarities with changing player limits need a custom roll policy")
        keys = {}
        options = [o for o in old.get("CostOptions", {}).values() if o.get("Enabled", True)]
        if len(options) != 1:
            raise ValueError(f"{path}: expected exactly one enabled cost option")
        for entry in options[0]["Entries"].values():
            if entry["Type"] != "key":
                raise ValueError(f"{path}: unsupported cost type {entry['Type']}")
            keys[entry["Key"]] = {"amount": entry["Amount"]}
        positions = {}
        for position in old.get("Block", {}).get("Positions", []):
            x, y, z, world = position.split(",")
            positions.setdefault("minecraft:" + world.lower(), []).append(f"{x},{y},{z},0,0")
        generated[f"crate/{path.name}"] = {
            "base": {"permission_required": old.get("Permission_Required", False)},
            "display": {"name": old["Name"], "lore": old.get("Description", [])},
            "item": {"item": old["ItemProvider"], "item_stackable": old.get("ItemStackable", True),
                     "use_display": True},
            "rewards": {"required_rewards": 1, "reward_list": {
                rid: {"reward_id": rid} for rid in rewards}},
            "key_requirements": {"enabled": True, "keys": keys},
            "animation": {"enabled": old.get("Animation", {}).get("Enabled", True),
                          "profile": "csgo_animation:default"},
            "preview": {"enabled": old.get("Preview", {}).get("Enabled", True),
                        "config": "inventory:default"},
            "opening": {"enabled": True, "opening_commands": old.get("Post-Open", {}).get("Commands", [])},
            "cooldowns": {"global": cooldown(), "individual": cooldown(
                old.get("OpeningCooldown", {}).get("Enabled", False),
                value=old.get("OpeningCooldown", {}).get("Value", 0))},
            "blocks": {"positions": positions},
        }
        for rid, reward in rewards.items():
            limits = reward.get("Limits", {})
            preview = reward.get("PreviewData")
            if preview is None:
                raise ValueError(f"Missing preview item: {path.stem}/{rid}")
            value = {
                "base": {"weight": (max(0, reward.get("Weight", 1)) * rarities[reward.get("Rarity", "common")]["Weight"]
                                     / totals[reward.get("Rarity", "common")]) if totals[reward.get("Rarity", "common")] else 0},
                "towny_migration": {"original_weight": reward.get("Weight", 1), "source_version": 6},
                "preview": {"name": reward.get("Name", ""), "lore": reward.get("Description", []),
                            "icon": preview, "use_item_data": "Name" not in reward},
                "Rarity": {"enabled": True, "rarity_id": reward.get("Rarity", "common")},
                "broadcast": {"enabled": reward.get("Broadcast", False)},
                "limit": {"enabled": limits.get("Enabled", False),
                          "global": {"Enabled": limits.get("GlobalAmount", -1) >= 0,
                                     "Amount": limits.get("GlobalAmount", -1)},
                          "individual": {"Enabled": limits.get("PlayerAmount", -1) >= 0,
                                         "Amount": limits.get("PlayerAmount", -1)},
                          "alternative": {"enabled": False}},
                "cooldown": {scope: cooldown(limits.get(field, 0) > 0 and limits.get("Enabled", False),
                                             limits.get("CooldownType", "CUSTOM"), limits.get(field, 0))
                             for scope, field in [("global", "GlobalCooldown"), ("individual", "PlayerCooldown")]},
            }
            if reward["Type"] == "COMMAND":
                value["commands"] = {"enabled": True, "iterations": 1, "give_mode": "NORMAL",
                                     "bundles": {"legacy": {"commands": reward["Commands"], "weight": 1.0}}}
            elif reward["Type"] == "ITEM":
                value["items"] = {"item_list": reward["ItemsData"]}
            else:
                raise ValueError(f"Unsupported reward type: {reward['Type']}")
            if reward.get("Required_Permissions") or reward.get("Ignored_For_Permissions"):
                raise ValueError("Permission-restricted legacy rewards need manual migration")
            generated[f"reward/{path.stem}/{rid}.yml"] = value
    for rid, rarity in rarities.items():
        generated[f"rarity/{rid}.yml"] = {"base": {"name": rarity["Name"], "weight": rarity["Weight"],
                                                   "color": {"red": 255, "green": 255, "blue": 255, "alpha": 255}}}
    # Prepare everything before writing, so unsupported input leaves no partial migration.
    for name, data in generated.items():
        target = destination / "objects" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=150))
    print(f"Converted {len(generated)} objects. Database files were not touched.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    convert(args.source, args.destination)
