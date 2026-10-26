# Graydient's Touhou Little Maid Tweaks

English | [简体中文](README.zh-CN.md)

An independent addon for [**Touhou Little Maid**](https://github.com/TartaricAcid/TouhouLittleMaid) (by tartaric_acid and contributors).

| Version | Minecraft | Loader | License |
|---|---|---|---|
| `1.1.0-a3` | `1.21.1` | NeoForge | [GPL-3.0-or-later](LICENSE) |

---

## About

I made this mod for a small private server where players wanted some quality-of-life changes. It tries to preserve Touhou Little Maid's original maid behavior while making small QoL and performance adjustments. The added features do not affect game balance.

**Project status:** 1.1.0-a3 has been tested on a private server. All automated tests have passed. It will be released if no further issues are found.

---

## Changes

1.1.0 changes to Storage I/O (Gap)

### New features

- **Stock limits:** Set Storage I/O chest → maid whitelist limits from 1–128 items.
- **Updated filter controls:** Scroll to adjust, shift-click to copy, or right-click while holding an item to add one virtual item. Counts of one are hidden and tooltips are shorter.
- **Multiple Storage I/O items:** Imports run first; exports keep the import reserve and send only excess.

### Improvements

- **Improved search with multiple Storage I/O items:** Share chest lookups for the same target while preserving separate chests and double-chest bindings.

---

## Features at a glance

| Feature | What it does |
|---|---|
| **Resource-pack sharing** | You can see your friends' custom maid models, chairs, and sounds. |
| **Multiplayer permissions** | You can view other players' maid equipment without anyone taking items or changing jobs. |
| **Animal management** | Maids won't kill the last of your sheep, and can manage pens together with unlimited cow milk. |
| **Work-priority seating** | Maids won't hog a chair or stay stuck sitting when there is work to do. |
| **Ghost fence gates** | Maids can walk through closed fence gates without letting animals escape. |
| **Night skipping** | Sleeping maids help skip the night so busy or AFK players don't hold the server up. |
| **Custom shift schedules** | Change when day-shift and night-shift maids work, relax, and sleep. |
| **Two-player board games** | Play Chess, Xiangqi, or Gomoku against another player. |
| **Automatic tool replacement** | Maids swap in a matching spare from their backpack when their tool breaks. |
| **Storage I/O virtual filters** | Set Storage I/O filters without giving up a real copy of a tool. |
| **Dropped-item pickup delay** | Maids wait a bit before running after items you just dropped. |

---

## Game environment

Create or select a game instance with the following environment. A dedicated
server needs the same Minecraft version and loader, running on Java 21.

| Environment | Required version |
|---|---|
| **Minecraft** | `1.21.1` |
| **Mod loader** | NeoForge `21.1.248+` (`21.1.x`) |
| **Java runtime** | `21` |

---

## Installation

### Required mods

Install these mods alongside this addon in the `mods` folder, using their
Minecraft 1.21.1 NeoForge versions.

| Mod | Required version | Install on |
|---|---|---|
| **Touhou Little Maid** | `1.5.3` | Client and server |
| **Cloth Config** | `15.0.140+` (`15.x`) | Client only |

Create, Farm & Cook, and Carry On are optional integrations. Build targets are in [project.json](project.json).

### Steps

1. Put this addon and Touhou Little Maid in the `mods` folder on every client and on the server. For singleplayer, only the client installation is needed.
2. Put Cloth Config in each client's `mods` folder.
3. Restart the game or server.

---

## Configuration and commands

Open **Touhou Little Maid > Config > Graydient's Maid Tweaks** in-game:

- **World hosts and server operators** can edit server rules.
- **Other players** can view server rules and change their own **Resource Packs** consent.
- **Languages:** **English** (`en_us`) and **Simplified Chinese** (`zh_cn`).
- **Config files:** Stored in `config/touhou_little_maid_tweaks-<module>.toml` (`server`, `jobs`, `schedule`, `sleep`, `pickup`, and `permissions`), with optional per-world overrides in `serverconfig/`.

| Command | What it does |
|---|---|
| `/maidsleep` | Counts all eligible sleeping maids in the current dimension toward skipping tonight. |
| `/maidsleep cancel` | Stops maids in the current dimension from counting toward night skipping until dawn. |
| `/maidsleep status` | Shows current sleep settings and eligible maid counts in the current dimension. |
| `/automaidsleep <true\|false>` | Toggles automatic maid-only night skipping (host/operators by default; `true` also clears tonight's cancel). |

---

## Feature details

### 1. Maid resource-pack sharing
*You can now see your friends' custom maid models, chairs, and sounds.*

- Syncs packs downloaded from TLM's official catalogue as well as custom maid, chair, and sound ZIPs across a server or LAN world.
- Each player opts in per server or world under **Resource Packs**, so local files are not touched unless you enable it.
- Custom packs can run JavaScript, so sharing them requires server permission and a 5-second confirmation prompt on the client.

---

### 2. Multiplayer permissions
*You can view other players' maid equipment without anyone taking items or changing jobs.*

| Action by a non-owner | Default |
|---|---|
| Open and view a maid's inventory, gear, and job settings | Allowed |
| Take or move items | Owner only |
| Change settings or jobs | Owner only |
| Pick the maid up with Carry On | Owner only |

The host or a server operator can allow any of these restricted actions under **Player Permissions**.

---

### 3. Animal management
*Maids won't kill the last of your sheep, and can manage whole pens together with unlimited cow milk.*

- **Smarter population limits:** Maids keep at least **two adults of each species** by default and never kill baby animals. You can set limits by **Animal volume size**, **Animal footprint size**, or **Count per type** (default: `1.5` cows per 4 floor blocks; works with both Touhou Little Maid and Farm & Cook).
- **Animal Management Station:** Place a station inside a fenced pen, against the outside of a fence, or directly on a fence post. Nearby breeding maids will manage that pen together using shared settings without needing manual assignment.
- **Shared pen settings:** Stations watching the same pen share its rules. Breaking a fence-mounted station restores the original fence. Disabling stations makes them decorative: no work, milk collection, or settings menu.
- **Unlimited milk:** Right-click an active station with an **empty bucket** for unlimited milk as long as the enclosed pen has at least one adult cow.

```text
Crafting recipe:
S S S    S = any wooden slab
. B .    B = barrel
. S .    . = empty
```

---

### 4. Work-priority seating and seat locking
*Maids won't hog a chair or get stuck sitting when there is work to do.*

- Idle maids can sit in empty chairs (including Create seats), and stand up automatically when their job needs them to move.
- Working maids who need a seat (like fishing maids searching across their work area, even around blocked sight lines) will take over unlocked leisure seats.
- To lock a maid in a seat, turn **Entity Riding / Allowed to sit** off while she is sitting.

---

### 5. Ghost fence gates
*Maids walk right through closed fence gates while your animals stay inside.*

- Cycle a maid's fence-gate button through **Off / On / Ghost**.
- In **Ghost** mode, maids can walk through closed fence gates while the gate stays solid for animals and mobs.

---

### Other tweaks

- **Night skipping:** Enable under **Maid Sleep** (master switch is off by default). **Maid-assisted skipping** (on by default) lets sleeping maids count toward the sleep requirement at `0.5` players per maid. **Auto-skip** (off by default) lets maids skip the night on their own. Both manual and automatic maid-only skips wait until `22:30` by default; helping a sleeping player follows normal player timing. Sleep commands only change counting permission, never jobs, movement, or bedtime.
- **Custom shift schedules:** Edit **HH:MM** start times under **Maid Schedules** (Day defaults: `06:00` work, `18:00` leisure, `22:00` sleep; Night defaults: `18:00` work, `14:00` leisure, `06:00` sleep).
- **Two-player board games:** Right-click an empty Chess, Xiangqi, or Gomoku board with an empty hand to play against another player. Click the reset area to start over.
- **Automatic tool replacement:** Swaps in an identical spare tool from the maid's backpack when her equipped tool breaks, keeping its enchantments and durability.
- **Storage I/O stock limits:** Left-click a filter with a stack, or shift-click an inventory item, to copy its type and count without consuming it. Right-click adds one virtual item at a time. For chest → maid whitelist transfers, scroll over the reference to set a carried limit of **1–128**. Existing filters start at **1**. Multiple equipped Storage I/O items import first; exports keep the highest import reserve for each item and send only excess. Export references stay at **1**. Blacklist imports retain native type filtering. Legacy physical filter items are refunded once.
- **Dropped-item pickup delay:** Maids wait **15 seconds** by default before chasing or magnet-pulling newly dropped items outside immediate reach, while still picking up items right at their feet.

---

## Reposting, platforms, and upstream

- **Modpacks:** You can include this mod in any modpack without asking me first.
- **Reposting:** Please don't repost or re-upload this mod as a standalone project on other platforms (like Modrinth, CurseForge, MCMOD / MC百科, or other download sites) without asking me first. If you want this mod hosted on a specific platform (including Chinese platforms), message me or open an issue and I will create an account and host it there myself.
- **Upstream Touhou Little Maid:** If the **Touhou Little Maid** team (`tartaric_acid` and contributors) wants to take any ideas or features from this addon, I'm open to the idea and happy to re-license my original code under **MIT** for them.

---

## License and credits

- Licensed under [GPL-3.0-or-later](LICENSE); see [COPYRIGHT](COPYRIGHT) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full credits.
- Credits include Touhou Little Maid (MIT), Morning Yellow's Chess and Xiangqi engines (GPL-2.0-or-later), and anlingyi's Gomoku component (Apache-2.0).
- No upstream assets or dependency JARs are bundled.
