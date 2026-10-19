// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.station.StationManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server defaults; explicit maid settings are saved by TLM task data. */
public final class JobConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED, BREED_LIMIT, PROTECT_PAIR, REPLACE_TOOLS,
            SEAT_RECOVERY, SEAT_LOCK, STATIONS, MILK, BUTCHER_NO_FOOD;
    private static final ModConfigSpec.IntValue WIDTH, METHOD, KEEP_COUNT, SCAN_RADIUS, SCAN_CELLS, SCAN_NODES, MAINTENANCE_MICROS;
    private static final ModConfigSpec.DoubleValue DENSITY;
    private static WorkSettings applied = WorkSettings.DEFAULTS;
    private static volatile AnimalSettings cachedDefaults = WorkSettings.DEFAULTS.animalDefaults();
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("animal_jobs");
        ENABLED = builder.comment("Replace regular breeding/slaughter policy. Off restores stock TLM behavior.").define("enabled", true);
        WIDTH = builder.comment("Square horizontal search width in blocks. No fence or floor scan.").defineInRange("default_search_width", 16, 2, 64);
        METHOD = builder.comment("Default unfenced animal rule: 0 volume, 1 adult count per type, 2 footprint.").defineInRange("default_method", 0, 0, 2);
        KEEP_COUNT = builder.comment("Default adult limit for each type when using count mode. Babies are ignored.").defineInRange("default_keep_count", 10, 0, 10000);
        DENSITY = builder.comment("Default cows per four search blocks in weighted modes. Legacy volume values retain their capacity; pair protection takes priority.").defineInRange("default_density", 1.5, 0.0, 64.0);
        BREED_LIMIT = builder.comment("Limit feeding for breeding by density. False removes the stock fixed cutoff.").define("default_limit_breeding", false);
        PROTECT_PAIR = builder.comment("Keep at least two adults of each entity type in the search area.").define("default_protect_pair", true);
        BUTCHER_NO_FOOD = builder.comment("Allow over-capacity slaughter without breeding food. Feeding still requires food; babies and protected pairs remain safe.").define("butcher_without_food", false);
        builder.pop().push("stations");
        STATIONS = builder.comment("Enable shared pen management. Requires animal_jobs.enabled; existing station blocks and policies are retained when off.").define("enabled", true);
        MILK = builder.comment("Allow unlimited station milk while its verified pen has an adult cow. Requires stations.enabled.").define("infinite_milk", true);
        SCAN_RADIUS = builder.comment("Maximum horizontal flood radius; exhaustion is unverified, never closed.").defineInRange("scan_radius", 32, 4, 128);
        SCAN_CELLS = builder.comment("Maximum mapped floor cells per connected pen.").defineInRange("max_floor_cells", 4096, 16, 16384);
        SCAN_NODES = builder.comment("Aggregate scan nodes per server tick across all dimensions.").defineInRange("scan_nodes_per_tick", 128, 1, 2048);
        MAINTENANCE_MICROS = builder.comment("Soft aggregate maintenance budget; one entity query cannot be interrupted.").defineInRange("maintenance_budget_microseconds", 500, 50, 5000);
        builder.pop().push("tools");
        REPLACE_TOOLS = builder.comment("After breakage, move an identical item from maid backpack inventory into the empty equipment slot.").define("auto_same_item_replacement", true);
        builder.pop().push("seats");
        SEAT_RECOVERY = builder.comment("Allow vacant leisure seats; available work takes priority over unlocked leisure occupants.").define("avoid_hogging", true);
        SEAT_LOCK = builder.comment("Turning off Allowed to sit while seated locks that seat. Off restores normal dismount behavior.").define("allow_lock", true);
        builder.pop();
        SPEC = builder.build();
    }
    private JobConfig() {}
    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> applyChanges(server, settings()));
    }
    public static WorkSettings settings() {
        return SPEC.isLoaded() ? new WorkSettings(ENABLED.get(), SEAT_RECOVERY.get(), SEAT_LOCK.get(), STATIONS.get(),
                MILK.get(), REPLACE_TOOLS.get(), BUTCHER_NO_FOOD.get(), defaults()) : WorkSettings.DEFAULTS;
    }
    public static void acceptRemote(WorkSettings value) { if (SPEC.isLoaded() && value.valid()) setValues(value); }
    public static boolean update(MinecraftServer server, WorkSettings value) {
        if (!SPEC.isLoaded() || !value.valid()) return false;
        WorkSettings previous = settings();
        try { setValues(value); SPEC.save(); }
        catch (RuntimeException failure) {
            setValues(previous);
            MaidTweaks.LOG.error("Could not save maid work settings", failure);
            return false;
        }
        applyChanges(server, value);
        return true;
    }
    private static void setValues(WorkSettings value) {
        ENABLED.set(value.animalJobs()); SEAT_RECOVERY.set(value.seatRecovery()); SEAT_LOCK.set(value.seatLock());
        STATIONS.set(value.stations()); MILK.set(value.milk()); REPLACE_TOOLS.set(value.replaceTools());
        BUTCHER_NO_FOOD.set(value.butcherWithoutFood());
        var defaults = value.animalDefaults();
        WIDTH.set(defaults.searchWidth()); DENSITY.set(defaults.density()); METHOD.set(defaults.method()); KEEP_COUNT.set(defaults.keepCount());
        BREED_LIMIT.set(defaults.limitBreeding()); PROTECT_PAIR.set(defaults.protectPair());
    }
    private static void applyChanges(MinecraftServer server, WorkSettings next) {
        WorkSettings previous = applied;
        if (previous.equals(next)) return;
        applied = next;
        boolean animalChanged = previous.animalJobs() != next.animalJobs() || previous.butcherWithoutFood() != next.butcherWithoutFood()
                || !previous.animalDefaults().equals(next.animalDefaults());
        boolean seatsChanged = previous.seatRecovery() != next.seatRecovery();
        boolean stationsChanged = (previous.animalJobs() && previous.stations()) != (next.animalJobs() && next.stations());
        if (animalChanged || stationsChanged) AnimalWork.clearCache();
        if (stationsChanged || previous.milk() != next.milk()) StationManager.configurationChanged(stationsChanged);
        if (animalChanged || seatsChanged || previous.seatLock() != next.seatLock()) {
            for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
                if (!(entity instanceof EntityMaid maid)) continue;
                if (!next.seatLock() && !maid.isRideable() && maid.isPassenger()) maid.stopRiding();
                if (seatsChanged || animalChanged && AnimalWork.supports(maid.getTask())) maid.refreshBrain(level);
            }
        }
        JobProtocol.broadcast(server);
    }
    public static int scanRadius() { return SPEC.isLoaded() ? SCAN_RADIUS.get() : 32; }
    public static int scanCells() { return SPEC.isLoaded() ? SCAN_CELLS.get() : 4096; }
    public static int scanNodes() { return SPEC.isLoaded() ? SCAN_NODES.get() : 128; }
    public static int maintenanceMicros() { return SPEC.isLoaded() ? MAINTENANCE_MICROS.get() : 500; }
    public static boolean enabled() { return SPEC.isLoaded() && ENABLED.get(); }
    public static boolean seatRecovery() { return SPEC.isLoaded() && SEAT_RECOVERY.get(); }
    public static boolean seatLock() { return SPEC.isLoaded() && SEAT_LOCK.get(); }
    public static boolean stationsEnabled() { return enabled() && STATIONS.get(); }
    public static boolean milkEnabled() { return stationsEnabled() && MILK.get(); }
    public static boolean butcherWithoutFood() { return enabled() && BUTCHER_NO_FOOD.get(); }
    public static boolean replaceTools() { return SPEC.isLoaded() && REPLACE_TOOLS.get(); }
    public static AnimalSettings defaults() {
        if (!SPEC.isLoaded()) return WorkSettings.DEFAULTS.animalDefaults();
        int width = WIDTH.get(), method = METHOD.get(), count = KEEP_COUNT.get(); double density = DENSITY.get();
        boolean breeding = BREED_LIMIT.get(), pair = PROTECT_PAIR.get();
        var result = cachedDefaults;
        if (result.searchWidth() != width || result.density() != density
                || result.limitBreeding() != breeding || result.protectPair() != pair || result.method() != method || result.keepCount() != count)
            cachedDefaults = result = new AnimalSettings(width, density, breeding, pair, method, count);
        return result;
    }
}
