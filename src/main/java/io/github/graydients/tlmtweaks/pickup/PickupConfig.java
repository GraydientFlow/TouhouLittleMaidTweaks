// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.pickup;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class PickupConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue DELAY;
    private static final ModConfigSpec.DoubleValue IMMEDIATE_RANGE;
    private static volatile PickupSettings current = PickupSettings.DEFAULTS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("maid_pickup");
        DELAY = builder.comment("Seconds after a new item drops before maids may pursue or magnet-collect it outside immediate pickup range.",
                        "Uses 20 elapsed game ticks per second. Set 0 to disable this extra delay.")
                .defineInRange("delay_seconds", PickupSettings.DEFAULTS.delaySeconds(), 0, 3600);
        IMMEDIATE_RANGE = builder.comment("Immediate dropped-item pickup reach beyond the maid's hitbox, bypassing only this extra delay.",
                        "A negative value uses the maid's normal pickup-range attribute (stock default 0.5). 0 requires hitbox contact. Accepts -1 through 16.")
                .defineInRange("immediate_pickup_range", PickupSettings.DEFAULTS.immediateRange(), -1, 16);
        builder.pop();
        SPEC = builder.build();
    }

    private PickupConfig() {}
    public static PickupSettings settings() { return current; }

    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        PickupSettings next = SPEC.isLoaded()
                ? new PickupSettings(DELAY.get(), IMMEDIATE_RANGE.get()) : PickupSettings.DEFAULTS;
        if (next.equals(current)) return;
        current = next;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> PickupProtocol.broadcast(server));
    }

    public static void acceptRemote(PickupSettings settings) { if (settings.valid()) current = settings; }

    public static boolean update(MinecraftServer server, PickupSettings settings) {
        if (!SPEC.isLoaded() || !settings.valid()) return false;
        PickupSettings previous = current;
        try {
            setValues(settings);
            SPEC.save();
            current = settings;
            PickupProtocol.broadcast(server);
            return true;
        } catch (RuntimeException failure) {
            setValues(previous);
            current = previous;
            MaidTweaks.LOG.error("Could not save maid pickup settings", failure);
            return false;
        }
    }

    private static void setValues(PickupSettings settings) {
        DELAY.set(settings.delaySeconds());
        IMMEDIATE_RANGE.set(settings.immediateRange());
    }
}
