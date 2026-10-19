// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class SleepConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue AUTO_SLEEP;
    private static final ModConfigSpec.BooleanValue AUTO_SLEEP_OPERATOR_ONLY;
    private static final ModConfigSpec.BooleanValue CONTRIBUTE;
    private static final ModConfigSpec.DoubleValue WEIGHT;
    private static final ModConfigSpec.ConfigValue<String> EARLIEST_TIME;
    private static volatile SleepSettings current = SleepSettings.DEFAULTS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("maid_sleep");
        ENABLED = builder.comment("Enable the maid sleep module; its automatic skip and player-contribution switches are independent.",
                        "Commands and automatic permission never change maid timetables or movement.")
                .define("enabled", SleepSettings.DEFAULTS.enabled());
        AUTO_SLEEP = builder.comment("Let scheduled sleeping maids skip night without a sleeping player, after earliest_skip_time.",
                        "/maidsleep cancel excludes their sleep tonight; they still follow their normal timetable.")
                .define("auto_sleep", SleepSettings.DEFAULTS.autoSleep());
        AUTO_SLEEP_OPERATOR_ONLY = builder.comment("Require a world host or operator to use /automaidsleep.",
                        "Set false to let every player change the shared auto-skip setting through that command.")
                .define("auto_sleep_operator_only", SleepSettings.DEFAULTS.autoSleepOperatorOnly());
        CONTRIBUTE = builder.comment("Recommended: let scheduled sleeping maids contribute to playersSleepingPercentage.",
                        "Requires at least one player to complete the normal sleep delay; never skips alone.",
                        "Independent of auto_sleep. If false, automatic maid and normal player thresholds are separate.")
                .define("contribute_to_sleeping_percentage", SleepSettings.DEFAULTS.contributeToSleep());
        WEIGHT = builder.comment("Player credit per eligible sleeping maid, for auto-skip and enabled player-percentage contributions.",
                        "At 0.5, two online players require four maids. Players are counted across the server; maids must share a dimension.")
                .defineInRange("maid_sleeping_weight", SleepSettings.DEFAULTS.maidWeight(), 0.01, 64);
        EARLIEST_TIME = builder.comment("Earliest manual or automatic maid-only skip time, HH:mm from 18:00 through 05:59. Player-assisted sleep uses normal timing.",
                        "Default 22:30 allows maids time to pathfind to their beds after 22:00 bedtime. Each eligible maid must continuously sleep in a maid bed for 100 game ticks.")
                .define("earliest_skip_time", SleepSettings.DEFAULTS.earliestSkipTime(),
                        value -> value instanceof String clock && SleepSettings.validClock(clock));
        builder.pop();
        SPEC = builder.build();
    }

    private SleepConfig() {}
    public static SleepSettings settings() { return current; }

    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        SleepSettings next = SPEC.isLoaded()
                ? new SleepSettings(ENABLED.get(), WEIGHT.get(), EARLIEST_TIME.get(), AUTO_SLEEP.get(), AUTO_SLEEP_OPERATOR_ONLY.get(), CONTRIBUTE.get()) : SleepSettings.DEFAULTS;
        if (next.equals(current)) return;
        boolean commandsChanged = next.autoSleepOperatorOnly() != current.autoSleepOperatorOnly();
        current = next;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> refreshed(server, commandsChanged));
    }

    public static void acceptRemote(SleepSettings settings) {
        if (settings.valid()) current = settings;
    }

    public static boolean update(MinecraftServer server, SleepSettings settings) {
        if (!SPEC.isLoaded() || !settings.valid()) return false;
        SleepSettings previous = current;
        try {
            setValues(settings);
            SPEC.save();
            current = settings;
            refreshed(server, settings.autoSleepOperatorOnly() != previous.autoSleepOperatorOnly());
            return true;
        } catch (RuntimeException failure) {
            setValues(previous);
            current = previous;
            MaidTweaks.LOG.error("Could not save maid sleep settings", failure);
            return false;
        }
    }

    private static void setValues(SleepSettings settings) {
        ENABLED.set(settings.enabled());
        AUTO_SLEEP.set(settings.autoSleep());
        AUTO_SLEEP_OPERATOR_ONLY.set(settings.autoSleepOperatorOnly());
        CONTRIBUTE.set(settings.contributeToSleep());
        WEIGHT.set(settings.maidWeight());
        EARLIEST_TIME.set(settings.earliestSkipTime());
    }

    private static void refreshed(MinecraftServer server, boolean commandsChanged) {
        MaidSleep.reconfigure(server);
        SleepProtocol.broadcast(server);
        if (commandsChanged && server.getPlayerList() != null)
            for (var player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
    }
}
