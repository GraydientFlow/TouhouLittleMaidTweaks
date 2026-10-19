// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.permissions;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class PermissionConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue CARRY, VIEW, MOVE_ITEMS, CHANGE_SETTINGS;
    private static volatile PermissionSettings current = PermissionSettings.DEFAULTS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Permissions for other players' tamed maids. Owners keep their normal access.")
                .push("player_permissions");
        CARRY = builder.comment("Allow Carry On to pick up another player's maid. Normal Carry On restrictions still apply.")
                .define("allow_carry", PermissionSettings.DEFAULTS.carry());
        VIEW = builder.comment("Allow opening another player's inventory, equipment, job and gear/settings tabs.")
                .define("allow_view", PermissionSettings.DEFAULTS.view());
        MOVE_ITEMS = builder.comment("Allow moving items in another player's maid menus. Requires allow_view.")
                .define("allow_item_changes", PermissionSettings.DEFAULTS.moveItems());
        CHANGE_SETTINGS = builder.comment("Allow changing another player's maid jobs and settings. Requires allow_view.")
                .define("allow_setting_changes", PermissionSettings.DEFAULTS.changeSettings());
        builder.pop();
        SPEC = builder.build();
    }

    private PermissionConfig() {}
    public static PermissionSettings settings() { return current; }
    public static void acceptRemote(PermissionSettings settings) { current = settings; }
    public static void clearRemote() { current = PermissionSettings.DEFAULTS; }

    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var next = SPEC.isLoaded()
                ? new PermissionSettings(CARRY.get(), VIEW.get(), MOVE_ITEMS.get(), CHANGE_SETTINGS.get())
                : PermissionSettings.DEFAULTS;
        if (next.equals(current)) return;
        current = next;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> PermissionProtocol.broadcast(server));
    }

    public static boolean update(MinecraftServer server, PermissionSettings settings) {
        if (!SPEC.isLoaded()) return false;
        var previous = current;
        try {
            setValues(settings);
            SPEC.save();
            current = settings;
            PermissionProtocol.broadcast(server);
            return true;
        } catch (RuntimeException failure) {
            setValues(previous);
            current = previous;
            MaidTweaks.LOG.error("Could not save maid player permissions", failure);
            return false;
        }
    }

    private static void setValues(PermissionSettings settings) {
        CARRY.set(settings.carry());
        VIEW.set(settings.view());
        MOVE_ITEMS.set(settings.moveItems());
        CHANGE_SETTINGS.set(settings.changeSettings());
    }
}
