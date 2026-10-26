# Agent code map

Start here when working in this repository as a coding agent, regardless of
model or harness. This is an agent-first navigation guide to the implementation,
state and constraints. [README.md](README.md) explains player-facing behavior.
Find the relevant feature below, then read its entry point, callers and state
types. Do not read the entire source tree to get started.

## Layout and entry points

- [project.json](project.json) owns identity, version and build targets. Generated
  `io.github.graydients.tlmtweaks.BuildInfo` exposes public static final strings
  `MOD_ID` from `mod_id` and `VERSION` from `version`. Loader metadata uses `${...}`
  placeholders resolved from the same project fields.
- Java lives under [src/main/java/io/github/graydients/tlmtweaks](src/main/java/io/github/graydients/tlmtweaks/).
  Paths in the table below are relative to that package.
- [MaidTweaks.java](src/main/java/io/github/graydients/tlmtweaks/MaidTweaks.java)
  registers server configs, content, menus and network payloads. Read it when
  adding a module or changing message registration.
- [client/ClientBootstrap.java](src/main/java/io/github/graydients/tlmtweaks/client/ClientBootstrap.java)
  registers screens and rendering;
  [client/ClothMaidConfig.java](src/main/java/io/github/graydients/tlmtweaks/client/ClothMaidConfig.java)
  assembles the single-level configuration submenus.
- [Mixin registration](src/main/resources/touhou_little_maid_tweaks.mixins.json)
  lists common and client hooks. `mixin/` connects native TLM/Minecraft behavior
  to feature classes; match a hook to its delegate before changing either.

## Find the feature

| Change | Start here | Follow when relevant |
|---|---|---|
| Regular breeding/slaughter | [jobs/AnimalWork.java](src/main/java/io/github/graydients/tlmtweaks/jobs/AnimalWork.java), `RegularBreeding`, `AnimalSettings` | `AnimalTaskMixin`, `AnimalSweepMixin`, `JobEvents.beforeHit`; `StationMembership` excludes managed pen animals |
| Station pens, capacity and milk | [station/StationManager.java](src/main/java/io/github/graydients/tlmtweaks/station/StationManager.java) | `PenSelection`/`PenScan` select and scan; `StationAnimals`/`SpeciesCapacity` count and allocate; `StationBlockEntity` persists; `StationSettings` holds rules |
| Station placement, shape or appearance | [station/StationBlock.java](src/main/java/io/github/graydients/tlmtweaks/station/StationBlock.java), `StationItem`, `StationFenceBlock`, `StationShapes` | `client/StationFenceModel`, `ClientBootstrap`, blockstates/models, recipe, loot tables and block tags |
| Seats, fishing and work priority | [jobs/SeatWork.java](src/main/java/io/github/graydients/tlmtweaks/jobs/SeatWork.java), `ChairUse`, `FishingSeats` | `SeatWorkBrainMixin`, `SeatWorkEntityMixin`, `SeatSelectionMixin`, `FishingSeatCastMixin`; board/Create seat hooks and `JobEvents.keepLockedSeat` |
| Broken tool replacement | [jobs/ToolReplacement.java](src/main/java/io/github/graydients/tlmtweaks/jobs/ToolReplacement.java) | `ItemBreakMixin`; preserve actual-break detection and spare item components |
| Shift clocks and activity | [schedule/MaidSchedules.java](src/main/java/io/github/graydients/tlmtweaks/schedule/MaidSchedules.java), `ScheduleSettings` | `ScheduleConfig`/`ScheduleProtocol`, `MaidSchedule*Mixin`, `client/ScheduleTooltip`; seats and sleep consume these schedules |
| Sleep commands and night skipping | [sleep/MaidSleep.java](src/main/java/io/github/graydients/tlmtweaks/sleep/MaidSleep.java), `SleepNight`, `SleepCommands` | `SleepProgress` counts readiness; `SleepForecast`/`SleepBedPlan` diagnose; `SleepStatus` reports; `MaidSleepLevelMixin` integrates native sleep |
| Official/custom pack sharing | [sync/ServerSync.java](src/main/java/io/github/graydients/tlmtweaks/sync/ServerSync.java), `CustomServer`; `client/ClientSync`, `CustomClient` | `Protocol`/`CustomProtocol`, `ManagedPacks`, journals, `ConnectionSession`, `ClientPreferences`; download/reload mixins |
| Non-owner access and Carry On | [permissions/PlayerPermissions.java](src/main/java/io/github/graydients/tlmtweaks/permissions/PlayerPermissions.java) | `PermissionConfig`/`PermissionProtocol`, `MaidPermission*Mixin`, `client/ClothPermissionConfig` |
| Wireless I/O reference filters | [storage/StorageFilters.java](src/main/java/io/github/graydients/tlmtweaks/storage/StorageFilters.java) | `WirelessTransfers`, `WirelessIOFilterMixin`, `WirelessIOFilterScreenMixin`, `MaidWirelessIOContextMixin`, `WirelessIOTransferMixin`; stock limits, ordered equipped IO batches and one-time physical refunds |
| Dropped-item pickup and magnets | [pickup/PickupDelay.java](src/main/java/io/github/graydients/tlmtweaks/pickup/PickupDelay.java) | `ItemDropTimeMixin`, `MaidPickupDelayMixin`, `MaidPickupTargetMixin`; `PickupConfig`/`PickupSettings` |
| Ghost fence gates | [gates/GhostGates.java](src/main/java/io/github/graydients/tlmtweaks/gates/GhostGates.java), `GateProtocol` | `GhostGate*Mixin`, `GateConfigScreenMixin`, `client/GhostGateView` |
| Player board games | [board/CoPlay.java](src/main/java/io/github/graydients/tlmtweaks/board/CoPlay.java), `ChessCoPlay` | `WChessCoPlayMixin`, `CChessCoPlayMixin`, `GomokuCoPlayMixin`; board-seat hooks handle maid occupancy |

## State and control flow

`MaidTweaks.ID` is `touhou_little_maid_tweaks`. Registry IDs, resources, packets,
config filenames and saved-data keys use this namespace. The display name is
**Graydient's Touhou Little Maid Tweaks**, and the Java package is
`io.github.graydients.tlmtweaks`.

Configurable modules generally use `*Config` for persisted server rules,
`*Settings` for validated values, `*Protocol` for messages, and `client/Cloth*Config`
for editing. Server handlers validate permissions and values before applying a
change, then return authoritative state. A UI check never replaces server checks.
Config reload/save paths refresh affected caches; disconnects clear remote state.

Animal settings belong to individual maids; station membership and shared pen
rules flow through `StationManager`. `StationMenu`/`StationProtocol` connect them
to `client/StationScreen`, `StationForm` and `AnimalTable`. `AnimalWorkForm` also
serves the regular animal controls. Preserve saved keys and per-maid overrides.

Pack transfers use bounded workers for file/network work and schedule game/UI
changes on the game thread. Connection generations, consent, ownership journals
and shutdown cleanup prevent stale transfers from changing another connection.

## Constraints worth checking before edits

- Keep regular animal selection cached and staggered; recheck live protection
  before a hit. Pen scans and populations are shared, budgeted and limited to
  loaded chunks. Reuse existing indexes instead of adding per-maid world scans.
- Seat recovery follows the job's own available work. Preserve deliberate locks,
  explicit standby and valid job seats. Fishing searches are bounded and cached.
- `schedule/` changes actual activity hours. `sleep/` commands only change whether
  sleeping maids count toward skipping; they never assign work or force sleep.
- TLM is required; Create, Farm & Cook and Carry On are optional. Keep optional
  hooks and client classes safe to load on servers without those integrations.
- Preserve native item components, ownership and manual pack files. Filter
  references must never become extractable real items.
- Keep player text in both locale files under
  [assets/touhou_little_maid_tweaks/lang](src/main/resources/assets/touhou_little_maid_tweaks/lang/).
  Resource JSON lives under `src/main/resources/assets/` and `data/`; loader
  metadata is [neoforge.mods.toml](src/main/resources/META-INF/neoforge.mods.toml).

## Agent workflow and checks

`main` tracks stable releases; `dev` integrates changes for the next release.
Start feature work from `dev` and merge accepted changes back there. Keep source
release tags immutable. Build tools, tests and technical documents have separate
private history in the local parent workspace; they are not part of this tree.
Stable promotion, remote pushes and deployment need the applicable owner request.

Use the Java and dependency targets in `project.json` and the loader descriptor
when preparing a development environment. This repository supplies production
source and resources; build scripts and test suites are not included.

Keep changes focused on the affected feature, its callers and saved/network data.
Check both server behavior and the corresponding client controls when changing a
shared setting. For documentation changes, check links and named entry points.
Describe the environment and checks used, their results and any unverified
behavior. Update this map when moving responsibilities or entry points.
