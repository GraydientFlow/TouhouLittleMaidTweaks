// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.jobs.AnimalSettings;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.JobProtocol;
import io.github.graydients.tlmtweaks.jobs.WorkSettings;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative work module in TLM's existing Cloth configuration screen. */
public final class ClothWorkConfig {
    private static final String KEY = MaidTweaks.ID + ".work_config.";
    private ClothWorkConfig() {}
    private static Component text(String key) { return Component.translatable(KEY + key); }
    public static void receive(JobProtocol.WorkState packet) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) JobConfig.acceptRemote(packet.settings());
    }
    public static void add(AddClothConfigEvent event, SubCategoryBuilder category, SubCategoryBuilder animalsCategory) {
        var minecraft = Minecraft.getInstance();
        var entries = event.getEntryBuilder();
        if (minecraft.level == null || minecraft.player == null || !JobConfig.SPEC.isLoaded()) {
            category.add(entries.startTextDescription(text("no_world")).build());
            animalsCategory.add(entries.startTextDescription(text("no_world")).build());
            return;
        }
        var connection = minecraft.getConnection();
        var integrated = minecraft.getSingleplayerServer();
        boolean editable = minecraft.player.hasPermissions(2)
                || integrated != null && integrated.isSingleplayerOwner(minecraft.player.getGameProfile());
        var initial = JobConfig.settings();
        var defaults = WorkSettings.DEFAULTS;
        category.add(entries.startTextDescription(text(integrated == null ? "server_scope" : "world_scope")).build());
        animalsCategory.add(entries.startTextDescription(text(integrated == null ? "server_scope" : "world_scope")).build());
        if (!editable) {
            category.add(entries.startTextDescription(text("read_only")).build());
            animalsCategory.add(entries.startTextDescription(text("read_only")).build());
        }
        var seats = entries.startBooleanToggle(text("avoid_seats"), initial.seatRecovery())
                .setDefaultValue(defaults.seatRecovery()).setTooltip(text("avoid_seats.tip")).build();
        var lock = entries.startBooleanToggle(text("lock_seats"), initial.seatLock())
                .setDefaultValue(defaults.seatLock()).setTooltip(text("lock_seats.tip")).build();
        var animals = entries.startBooleanToggle(text("animal_jobs"), initial.animalJobs())
                .setDefaultValue(defaults.animalJobs()).setTooltip(text("animal_jobs.tip")).build();
        var stations = entries.startBooleanToggle(text("stations"), initial.stations())
                .setDefaultValue(defaults.stations()).setTooltip(text("stations.tip")).build();
        var milk = entries.startBooleanToggle(text("milk"), initial.milk())
                .setDefaultValue(defaults.milk()).setTooltip(text("milk.tip")).build();
        var butcher = entries.startBooleanToggle(text("butcher_without_food"), initial.butcherWithoutFood())
                .setDefaultValue(defaults.butcherWithoutFood()).setTooltip(text("butcher_without_food.tip")).build();
        var tools = entries.startBooleanToggle(text("tools"), initial.replaceTools())
                .setDefaultValue(defaults.replaceTools()).setTooltip(text("tools.tip")).build();
        for (var entry : List.of(seats, lock, tools)) {
            entry.setEditable(editable); category.add(entry);
        }
        for (var entry : List.of(animals, butcher, stations, milk)) {
            entry.setEditable(editable); animalsCategory.add(entry);
        }
        var animalDefaults = initial.animalDefaults();
        var standardDefaults = defaults.animalDefaults();
        animalsCategory.add(entries.startTextDescription(text("defaults.tip")).build());
        var width = entries.startIntField(text("width"), animalDefaults.searchWidth())
                .setDefaultValue(standardDefaults.searchWidth()).setMin(2).setMax(64).setTooltip(text("width.tip")).build();
        var method = entries.startSelector(text("method"), new Integer[]{0, 2, 1}, animalDefaults.method())
                .setDefaultValue(standardDefaults.method()).setNameProvider(value -> text("method." + value))
                .setTooltip(text("method.tip")).build();
        var count = entries.startIntField(text("count"), animalDefaults.keepCount())
                .setDefaultValue(standardDefaults.keepCount()).setMin(0).setMax(10000).setTooltip(text("count.tip")).build();
        var density = entries.startDoubleField(text("density"), animalDefaults.density())
                .setDefaultValue(standardDefaults.density()).setMin(0).setMax(64)
                .setErrorSupplier(value -> Double.isFinite(value) ? Optional.empty() : Optional.of(text("invalid_density")))
                .setTooltip(text("density.tip")).build();
        var breeding = entries.startBooleanToggle(text("limit_breeding"), animalDefaults.limitBreeding())
                .setDefaultValue(standardDefaults.limitBreeding()).setTooltip(text("limit_breeding.tip")).build();
        var pair = entries.startBooleanToggle(text("protect_pair"), animalDefaults.protectPair())
                .setDefaultValue(standardDefaults.protectPair()).setTooltip(text("protect_pair.tip")).build();
        width.setEditable(editable); method.setEditable(editable); count.setEditable(editable); density.setEditable(editable); breeding.setEditable(editable); pair.setEditable(editable);
        animalsCategory.add(width); animalsCategory.add(method); animalsCategory.add(density); animalsCategory.add(count); animalsCategory.add(breeding); animalsCategory.add(pair);
        Runnable previousSave = event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave != null) previousSave.run();
            if (!editable || minecraft.getConnection() != connection) return;
            var selected = new WorkSettings(animals.getValue(), seats.getValue(), lock.getValue(), stations.getValue(),
                    milk.getValue(), tools.getValue(), butcher.getValue(), new AnimalSettings(width.getValue(), density.getValue(), breeding.getValue(), pair.getValue(), method.getValue(), count.getValue()));
            if (selected.valid() && !selected.equals(initial)) PacketDistributor.sendToServer(new JobProtocol.SetWork(selected));
        });
    }
}
