// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sleep.SleepConfig;
import io.github.graydients.tlmtweaks.sleep.SleepProtocol;
import io.github.graydients.tlmtweaks.sleep.SleepSettings;
import io.github.graydients.tlmtweaks.sleep.SleepStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClothSleepConfig {
    private static final String KEY = MaidTweaks.ID + ".sleep_config.";
    private static SleepStatus status;
    private static Object statusConnection;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> statusDimension;
    private ClothSleepConfig() {}

    public static void receive(SleepProtocol.State packet) {
        // An integrated server already owns this cache in the same JVM.
        if (Minecraft.getInstance().getSingleplayerServer() == null) SleepConfig.acceptRemote(packet.settings());
    }

    public static void receive(SleepProtocol.Status packet) {
        var minecraft = Minecraft.getInstance();
        status = packet.status(); statusConnection = minecraft.getConnection();
        statusDimension = minecraft.level == null ? null : minecraft.level.dimension();
    }

    public static void clearStatus() { status = null; statusConnection = null; statusDimension = null; }

    private static SleepStatus currentStatus() {
        var minecraft = Minecraft.getInstance();
        if (status == null || minecraft.level == null || minecraft.getConnection() != statusConnection
                || !minecraft.level.dimension().equals(statusDimension)) return null;
        long time = minecraft.level.getDayTime();
        return status.nightEnd() == time - Math.floorMod(time, 24000L) + 24000L ? status : null;
    }

    public static void add(AddClothConfigEvent event, me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder category) {
        Minecraft minecraft = Minecraft.getInstance();
        var entries = event.getEntryBuilder();
        if (minecraft.level == null || minecraft.player == null || !SleepConfig.SPEC.isLoaded()) {
            category.add(entries.startTextDescription(Component.translatable(KEY + "no_world")).build());
            return;
        }
        var connection = minecraft.getConnection();
        var integrated = minecraft.getSingleplayerServer();
        boolean editable = minecraft.player.hasPermissions(2)
                || integrated != null && integrated.isSingleplayerOwner(minecraft.player.getGameProfile());
        SleepSettings initial = SleepConfig.settings();
        SleepSettings defaults = SleepSettings.DEFAULTS;
        category.add(entries.startTextDescription(Component.translatable(KEY + (integrated == null ? "server_scope" : "world_scope"))).build());
        category.add(entries.startTextDescription(Component.translatable(KEY + "instruction")).build());
        category.add(new SleepStatusEntry(() -> currentStatus() == null ? Component.translatable(KEY + "loading") : currentStatus().queueMessage()));
        category.add(new SleepStatusEntry(() -> currentStatus() == null ? Component.translatable(KEY + "loading")
                : currentStatus().forecastMessage(SleepConfig.settings())));
        if (!editable) category.add(entries.startTextDescription(Component.translatable(KEY + "read_only")).build());
        var enabled = entries.startBooleanToggle(Component.translatable(KEY + "enabled"), initial.enabled())
                .setDefaultValue(defaults.enabled()).setTooltip(Component.translatable(KEY + "enabled.tip")).build();
        var automatic = entries.startBooleanToggle(Component.translatable(KEY + "auto"), initial.autoSleep())
                .setDefaultValue(defaults.autoSleep()).setTooltip(Component.translatable(KEY + "auto.tip")).build();
        var contribution = entries.startBooleanToggle(Component.translatable(KEY + "contribute"), initial.contributeToSleep())
                .setDefaultValue(defaults.contributeToSleep()).setTooltip(Component.translatable(KEY + "contribute.tip")).build();
        var operatorOnly = entries.startBooleanToggle(Component.translatable(KEY + "auto_operator_only"), initial.autoSleepOperatorOnly())
                .setDefaultValue(defaults.autoSleepOperatorOnly()).setTooltip(Component.translatable(KEY + "auto_operator_only.tip")).build();
        var weight = entries.startDoubleField(Component.translatable(KEY + "weight"), initial.maidWeight())
                .setDefaultValue(defaults.maidWeight()).setMin(0.01).setMax(64.0)
                .setErrorSupplier(value -> Double.isFinite(value) ? java.util.Optional.empty()
                        : java.util.Optional.of(Component.translatable(KEY + "invalid_weight")))
                .setTooltip(Component.translatable(KEY + "weight.tip"), Component.translatable(KEY + "weight.example")).build();
        var earliestTime = entries.startStrField(Component.translatable(KEY + "earliest_time"), initial.earliestSkipTime())
                .setDefaultValue(defaults.earliestSkipTime())
                .setErrorSupplier(value -> SleepSettings.validClock(value) ? java.util.Optional.empty()
                        : java.util.Optional.of(Component.translatable(KEY + "invalid_time")))
                .setTooltip(Component.translatable(KEY + "earliest_time.tip")).build();
        enabled.setEditable(editable);
        automatic.setEditable(editable);
        contribution.setEditable(editable);
        operatorOnly.setEditable(editable);
        weight.setEditable(editable);
        earliestTime.setEditable(editable);
        category.add(enabled);
        category.add(automatic);
        category.add(contribution);
        category.add(operatorOnly);
        category.add(weight);
        category.add(earliestTime);
        Runnable previousSave = event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave != null) previousSave.run();
            if (!editable || minecraft.getConnection() != connection) return;
            SleepSettings selected = new SleepSettings(enabled.getValue(), weight.getValue(), earliestTime.getValue(), automatic.getValue(), operatorOnly.getValue(), contribution.getValue());
            if (selected.valid() && !selected.equals(initial)) PacketDistributor.sendToServer(new SleepProtocol.Set(selected));
        });
    }
}
