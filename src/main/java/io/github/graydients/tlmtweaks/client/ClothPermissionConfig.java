// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.permissions.PermissionConfig;
import io.github.graydients.tlmtweaks.permissions.PermissionProtocol;
import io.github.graydients.tlmtweaks.permissions.PermissionSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClothPermissionConfig {
    private static final String KEY = MaidTweaks.ID + ".permissions_config.";
    private ClothPermissionConfig() {}

    public static void receive(PermissionProtocol.State packet) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) PermissionConfig.acceptRemote(packet.settings());
    }

    public static void add(AddClothConfigEvent event, me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder category) {
        Minecraft minecraft = Minecraft.getInstance();
        var entries = event.getEntryBuilder();
        if (minecraft.level == null || minecraft.player == null || !PermissionConfig.SPEC.isLoaded()) {
            category.add(entries.startTextDescription(Component.translatable(KEY + "no_world")).build());
            return;
        }
        var connection = minecraft.getConnection();
        var integrated = minecraft.getSingleplayerServer();
        boolean editable = minecraft.player.hasPermissions(2)
                || integrated != null && integrated.isSingleplayerOwner(minecraft.player.getGameProfile());
        PermissionSettings initial = PermissionConfig.settings();
        category.add(entries.startTextDescription(Component.translatable(KEY + "scope")).build());
        if (!editable) category.add(entries.startTextDescription(Component.translatable(KEY + "read_only")).build());
        var carry = entries.startBooleanToggle(Component.translatable(KEY + "carry"), initial.carry())
                .setDefaultValue(PermissionSettings.DEFAULTS.carry()).setTooltip(Component.translatable(KEY + "carry.tip")).build();
        var view = entries.startBooleanToggle(Component.translatable(KEY + "view"), initial.view())
                .setDefaultValue(PermissionSettings.DEFAULTS.view()).setTooltip(Component.translatable(KEY + "view.tip")).build();
        var items = entries.startBooleanToggle(Component.translatable(KEY + "items"), initial.moveItems())
                .setDefaultValue(PermissionSettings.DEFAULTS.moveItems()).setTooltip(Component.translatable(KEY + "items.tip")).build();
        var settings = entries.startBooleanToggle(Component.translatable(KEY + "settings"), initial.changeSettings())
                .setDefaultValue(PermissionSettings.DEFAULTS.changeSettings()).setTooltip(Component.translatable(KEY + "settings.tip")).build();
        for (var entry : java.util.List.of(carry, view, items, settings)) {
            entry.setEditable(editable);
            category.add(entry);
        }
        Runnable previousSave = event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave != null) previousSave.run();
            if (!editable || minecraft.getConnection() != connection) return;
            var selected = new PermissionSettings(carry.getValue(), view.getValue(), items.getValue(), settings.getValue());
            if (!selected.equals(initial)) PacketDistributor.sendToServer(new PermissionProtocol.Set(selected));
        });
    }
}
