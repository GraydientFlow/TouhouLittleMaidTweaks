// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** One TLM config heading with independently saved modules. */
@EventBusSubscriber(modid = MaidTweaks.ID, value = Dist.CLIENT)
public final class ClothMaidConfig {
    private ClothMaidConfig() {}

    @SubscribeEvent
    public static void add(AddClothConfigEvent event) {
        var category = event.getRoot().getOrCreateCategory(Component.translatable(MaidTweaks.ID + ".config.category"));
        var entries = event.getEntryBuilder();
        var work = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".work_config.category")).setExpanded(true);
        var animals = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".work_config.animals")).setExpanded(true);
        ClothWorkConfig.add(event, work, animals);
        category.addEntry(work.build());
        category.addEntry(animals.build());
        var schedule = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".schedule_config.category")).setExpanded(true);
        ClothScheduleConfig.add(event, schedule);
        category.addEntry(schedule.build());
        var sleep = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".sleep_config.category")).setExpanded(true);
        ClothSleepConfig.add(event, sleep);
        category.addEntry(sleep.build());
        var pickup = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".pickup_config.category")).setExpanded(true);
        ClothPickupConfig.add(event, pickup);
        category.addEntry(pickup.build());
        var permissions = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".permissions_config.category")).setExpanded(true);
        ClothPermissionConfig.add(event, permissions);
        category.addEntry(permissions.build());
        var packs = entries.startSubCategory(Component.translatable(MaidTweaks.ID + ".settings.cloth.category")).setExpanded(true);
        ClothPackSyncConfig.add(event, packs);
        category.addEntry(packs.build());
    }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClothSleepConfig.clearStatus();
        if (net.minecraft.client.Minecraft.getInstance().getSingleplayerServer()==null)
            io.github.graydients.tlmtweaks.schedule.MaidSchedules.reset();
        if (net.minecraft.client.Minecraft.getInstance().getSingleplayerServer()==null)
            io.github.graydients.tlmtweaks.permissions.PermissionConfig.clearRemote();
    }
}
