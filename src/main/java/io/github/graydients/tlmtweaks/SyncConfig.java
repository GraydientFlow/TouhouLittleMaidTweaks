// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks;

import io.github.graydients.tlmtweaks.sync.ServerSync;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SyncConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue CUSTOM;
    private static volatile boolean enabled;
    private static volatile boolean custom;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("pack_sync");
        ENABLED = builder.comment("Allow official maid catalogue sharing. Clients must opt in for this server.")
                .define("enabled", true);
        CUSTOM = builder.comment("Dangerous: permit player-added maid ZIP sharing. Every client must also opt in for this server.")
                .define("allow_custom_resource_packs", false);
        builder.pop();
        SPEC = builder.build();
    }

    private SyncConfig() {}

    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        enabled = SPEC.isLoaded() && ENABLED.get();
        custom = SPEC.isLoaded() && CUSTOM.get();
        ServerSync.policyChanged();
        io.github.graydients.tlmtweaks.sync.CustomServer.policyChanged();
    }

    public static boolean enabled() { return enabled; }
    public static boolean customConsent() { return custom; }
    public static boolean customTransferEnabled() { return enabled && custom; }
}
