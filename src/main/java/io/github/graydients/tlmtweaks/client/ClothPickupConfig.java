// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.pickup.PickupConfig;
import io.github.graydients.tlmtweaks.pickup.PickupProtocol;
import io.github.graydients.tlmtweaks.pickup.PickupSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClothPickupConfig {
    private static final String KEY = MaidTweaks.ID + ".pickup_config.";
    private ClothPickupConfig() {}

    public static void receive(PickupProtocol.State packet) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) PickupConfig.acceptRemote(packet.settings());
    }

    public static void add(AddClothConfigEvent event, me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder category) {
        Minecraft minecraft = Minecraft.getInstance();
        var entries = event.getEntryBuilder();
        if (minecraft.level == null || minecraft.player == null || !PickupConfig.SPEC.isLoaded()) {
            category.add(entries.startTextDescription(Component.translatable(KEY + "no_world")).build());
            return;
        }
        var connection = minecraft.getConnection();
        var integrated = minecraft.getSingleplayerServer();
        boolean editable = minecraft.player.hasPermissions(2)
                || integrated != null && integrated.isSingleplayerOwner(minecraft.player.getGameProfile());
        PickupSettings initial = PickupConfig.settings();
        category.add(entries.startTextDescription(Component.translatable(KEY + (integrated == null ? "server_scope" : "world_scope"))).build());
        category.add(entries.startTextDescription(Component.translatable(KEY + "notice")).build());
        if (!editable) category.add(entries.startTextDescription(Component.translatable(KEY + "read_only")).build());
        var delay = entries.startIntField(Component.translatable(KEY + "delay"), initial.delaySeconds())
                .setDefaultValue(PickupSettings.DEFAULTS.delaySeconds()).setMin(0).setMax(3600)
                .setTooltip(Component.translatable(KEY + "delay.tip")).build();
        var range = entries.startDoubleField(Component.translatable(KEY + "range"), initial.immediateRange())
                .setDefaultValue(PickupSettings.DEFAULTS.immediateRange()).setMin(-1.0).setMax(16.0)
                .setErrorSupplier(value -> new PickupSettings(0, value).valid() ? java.util.Optional.empty()
                        : java.util.Optional.of(Component.translatable(KEY + "invalid_range")))
                .setTooltip(Component.translatable(KEY + "range.tip")).build();
        delay.setEditable(editable);
        range.setEditable(editable);
        category.add(delay);
        category.add(range);
        Runnable previousSave = event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave != null) previousSave.run();
            if (!editable || minecraft.getConnection() != connection) return;
            PickupSettings selected = new PickupSettings(delay.getValue(), range.getValue());
            if (selected.valid() && !selected.equals(initial)) PacketDistributor.sendToServer(new PickupProtocol.Set(selected));
        });
    }
}
