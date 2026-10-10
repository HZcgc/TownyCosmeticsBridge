#!/usr/bin/env python3
"""Update a stopped server's EC7/DeluxeMenus config copies; never read databases."""
import argparse
import json
from pathlib import Path
import re
import yaml

PINK = "#FF55FF"
LIME = "#55FF55"
BRAND = f"<{PINK}><b>TOWNY</b></{PINK}><{LIME}><b>REBORN</b></{LIME}>"
PREFIX = BRAND + " <dark_gray>»</dark_gray> <gray>"
LEGACY_BRAND = "&#FF55FF&lTOWNY&#55FF55&lREBORN"
CRATES = {"daily": "Daily", "vote": "Vote", "cosmetic": "Cosmetic", "patron": "Supporter"}
KEY_COMMAND = re.compile(
    r"(?P<prefix>\[console\]\s+)/?crates\s+key\s+give\s+"
    r"(?P<player>[^\s'\"]+)\s+(?P<key>[^\s'\"]+)\s+(?P<amount>\d+)"
    r"(?:\s+-sf)?(?=['\"\r\n]|$)"
)


def name(label, kind):
    return f"<{PINK}><b>{label}</b></{PINK}> <{LIME}><b>{kind}</b></{LIME}>"


def configure(crates_root: Path, menus_root: Path):
    pending = {}

    def read(path):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError(f"Expected a configuration mapping: {path}")
        return data

    def queue(path, data):
        content = yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=150).encode("utf-8")
        if not path.exists() or path.read_bytes() != content:
            pending[path] = content

    # Build the entire patch before writing, so invalid input cannot leave half an update.
    for crate_id, label in CRATES.items():
        path = crates_root / "objects/crate" / f"{crate_id}.yml"
        data = read(path)
        data["display"]["name"] = name(label, "Crate")
        data["preview"]["config"] = "inventory:towny_reborn"
        data["hologram"]["text"] = [
            BRAND, "%crate_name%", "", "%crate_description%", "",
            f"<{PINK}>Left-Click</{PINK}><gray> to preview rewards</gray>",
            f"<{LIME}>Right-Click</{LIME}><gray> to choose how many to open</gray>",
        ]
        queue(path, data)
        path = crates_root / "objects/key" / f"{crate_id}.yml"
        data = read(path)
        data["display"]["name"] = name(label, "Key")
        data["display"]["lore"] = [BRAND, "<gray>Stored digitally in your key balance.</gray>"]
        queue(path, data)
        for path in sorted((crates_root / "objects/reward" / crate_id).glob("*.yml")):
            data = read(path)
            preview = data.get("preview", {})
            plain_name = re.sub(r"<[^>]*>", "", preview.get("name", ""))
            # Named relics already carry the Towny palette in their item data.
            # Give unnamed vanilla icons a preview label without changing the awarded item.
            payload = preview.get("icon", {}).get("Data", {}).get("Value", "")
            if preview.get("use_item_data", False) and "minecraft:custom_name" not in payload:
                plain_name = path.stem.replace("_", " ").title()
                preview["use_item_data"] = False
            # Keep item-provided names and SNBT icons intact. Only the display label changes.
            if plain_name and not preview.get("use_item_data", False):
                preview["name"] = f"<gradient:{PINK}:{LIME}><b>{plain_name}</b></gradient>"
                queue(path, data)

    # A configured opening cooldown caps native EC7 batches at one, even when expired.
    # Keep the separate interaction delay and all reward limits; remove only this cap.
    for path in sorted((crates_root / "objects/crate").glob("*.yml")):
        data = yaml.safe_load(pending[path]) if path in pending else read(path)
        for scope in ("global", "individual"):
            data.setdefault("cooldowns", {}).setdefault(scope, {})["enabled"] = False
        queue(path, data)

    path = crates_root / "config/crates.core.yml"
    data = read(path) if path.exists() else {}
    data["batch-enabled"] = True
    aliases = data.get("command_aliases", "crate,crates,case,cases")
    aliases = aliases.split(",") if isinstance(aliases, str) else list(aliases)
    if "crate" not in aliases:
        aliases.append("crate")
    data["command_aliases"] = ",".join(aliases)
    queue(path, data)
    path = crates_root / "config/crates.batch.yml"
    data = read(path)
    data["max_batch_size"] = 20
    queue(path, data)
    path = crates_root / "config/engine.core.yml"
    data = read(path)
    data["prefix"] = PREFIX
    queue(path, data)

    # These legacy fields are retained by upgraded installations. No database options change.
    path = crates_root / "engine.yml"
    if path.exists():
        data = read(path)
        data["Plugin"]["Name"] = "Towny Reborn"
        data["Plugin"]["Prefix"] = PREFIX
        queue(path, data)

    # A separate preview profile leaves Halloween's existing default profile untouched.
    path = crates_root / "objects/preview/inventory/towny_reborn.yml"
    data = read(crates_root / "objects/preview/inventory/default.yml")
    settings = data["Settings"]
    settings["name"] = "Towny Reborn"
    inventory = settings["inventory"]
    inventory["title"] = BRAND + " <dark_gray>»</dark_gray> %crate_name%"
    inventory["items"]["background_pane_gray"]["icon"]["Material"] = "minecraft:magenta_stained_glass_pane"
    inventory["buttons"]["previous_page"]["icon"]["Display_Name"] = f"<{PINK}>← Previous Page</{PINK}>"
    inventory["buttons"]["next_page"]["icon"]["Display_Name"] = f"<{LIME}>Next Page →</{LIME}>"
    queue(path, data)

    # Twenty amount slots: the stock 15-slot layout hides amounts 16-20.
    slots = [row * 9 + column for row in range(4) for column in range(2, 7)]
    data = {
        "Settings": {"MenuType": "minecraft:generic_9x4",
                     "Title": BRAND + " <dark_gray>»</dark_gray> <white>Opening Amount</white>",
                     "AutoRefresh": {"Interval": 0}, "PlaceholderAPI": {"Enabled": False}},
        "Buttons": {
            "background": {"Slots": ",".join(str(slot) for slot in range(36) if slot not in (0, 8, 27, 35)), "States": {"default": {"Icon": {
                "Material": "minecraft:black_stained_glass_pane", "Lore": [], "Hide_Components": True, "Hide_Tooltip": True}}}},
            "corners": {"Slots": "0,8,27,35", "States": {"default": {"Icon": {
                "Material": "minecraft:magenta_stained_glass_pane", "Lore": [], "Hide_Components": True, "Hide_Tooltip": True}}}},
        },
        "batch": {"amount_slots": ",".join(map(str, slots)), "amount_indicator": True,
                  "amount_item": {"Material": "minecraft:lime_stained_glass_pane",
                                  "Display_Name": name("OPEN", "%amount%X"),
                                  "Lore": ["<gray>Click to select %amount% openings.</gray>",
                                           "<gray>One virtual key is used per opening.</gray>"]}},
    }
    queue(crates_root / "menu/batch_amount_selection_menu.yml", data)

    path = crates_root / "lang/lang_en.yml"
    data = read(path)
    data["crates"]["pipeline"]["start"] = {
        "self": f"Opening <{LIME}>%crate_name%</{LIME}>.",
        "others": f"Opening <{LIME}>%crate_name%</{LIME}> for <white>%player_name%</white>.",
    }
    header = data["keys"]["balance"]["header"]
    for scope in ("self", "others"):
        title = "YOUR KEYS" if scope == "self" else "KEYS FOR %player_name%"
        header[scope] = ["[prefix=\"false\"]", BRAND + f" <dark_gray>»</dark_gray> <{LIME}><b>{title}</b></{LIME}>",
                         " ", f"<gray><i>I - Inventory / <{LIME}>V - Virtual</{LIME}> / U - Unclaimed</i></gray>", " "]
    queue(path, data)

    path = menus_root / "gui_menus/crates_menu.yml"
    data = read(path)
    data["menu_title"] = LEGACY_BRAND + " &8» &fCrates"
    data["register_command"] = False
    items = data["items"]
    info = items["information"]
    info["display_name"] = "&#FF55FF&lVIRTUAL &#55FF55&lCRATE KEYS"
    info["lore"] = ["", "&7Your keys are stored digitally.",
                    "&7Use them here or at the crates at spawn.", "",
                    "&8• &7Total Keys: &#55FF55&l%ec_totalkeys_daily,vote,patron,cosmetic%", "",
                    "&#FF55FFLeft Click &8» &7Preview rewards",
                    "&#55FF55Right Click &8» &7Choose how many to open", "",
                    "&8Selection appears with 2+ available openings."]
    for crate_id, label in CRATES.items():
        item_id = "supporter_crate" if crate_id == "patron" else crate_id + "_crate"
        item = items[item_id]
        item["display_name"] = f"&#FF55FF&l{label} &#55FF55&lCrate"
        item["material"] = "MAGENTA_SHULKER_BOX" if crate_id in ("daily", "patron") else "LIME_SHULKER_BOX"
        lore = item["lore"]
        for index, line in enumerate(lore):
            if "Your Keys:" in line:
                lore[index] = f"&8• &7Your Keys: &#55FF55&l%ec_keyz_{crate_id}%"
            elif "Left Click" in line:
                lore[index] = "&#FF55FFLeft Click &8» &7Preview rewards"
            elif "Right Click" in line:
                lore[index] = "&#55FF55Right Click &8» &7Choose how many to open"
        item["left_click_commands"] = ["[sound] UI_BUTTON_CLICK", "[close]",
                                       f"[console] crate preview {crate_id} %player_name%"]
        item["right_click_commands"] = ["[sound] BLOCK_ENDER_CHEST_OPEN", "[close]",
                                        f"[console] crate open {crate_id} %player_name%"]
    items["close"]["display_name"] = "&#FF55FF&lClose"
    items["border"]["material"] = "BLACK_STAINED_GLASS_PANE"
    items["border"]["slots"] = [1, 2, 3, 5, 6, 7, 9, 11, 13, 15, 17, 19, 20, 21, 23, 24, 25]
    items["brand_corners"] = {"material": "MAGENTA_STAINED_GLASS_PANE", "slots": [0, 8, 18, 26], "display_name": " "}
    # The public menu must never expose seasonal crates.
    if any("halloween" in str(key).lower() or "halloween" in str(value).lower() for key, value in items.items()):
        raise ValueError("Remove the Halloween menu entry before applying this configuration update")
    queue(path, data)

    # Keep every recipient, key and amount while fixing payouts in the other DeluxeMenus.
    command_count = 0
    for path in sorted(menus_root.rglob("*.yml")):
        if path in pending:
            continue
        old = path.read_text(encoding="utf-8")
        updated, count = KEY_COMMAND.subn(
            lambda m: f"{m['prefix']}cratekey give {m['player']} {m['key']} {m['amount']}", old)
        if count:
            yaml.safe_load(updated)
            pending[path] = updated.encode("utf-8")
            command_count += count

    for path, content in pending.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    return {"changed_files": [str(path) for path in pending], "updated_menu_key_commands": command_count}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("excellentcrates", type=Path)
    parser.add_argument("deluxemenus", type=Path)
    args = parser.parse_args()
    print(json.dumps(configure(args.excellentcrates, args.deluxemenus), indent=2))
