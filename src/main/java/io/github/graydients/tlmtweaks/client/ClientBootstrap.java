// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.jobs.AnimalConfigMenu;
import io.github.graydients.tlmtweaks.station.StationContent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = MaidTweaks.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientBootstrap {
    // ItemRenderer consumes ARGB alpha: RGB alone makes the held shelf transparent.
    private static final int SPRUCE_LECTERN_TINT = 0xFFA99D95;
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event) {
        event.register(AnimalConfigMenu.TYPE.get(), AnimalConfigScreen::new);
        event.register(StationContent.MENU.get(), StationScreen::new);
    }

    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 0 ? SPRUCE_LECTERN_TINT : -1,
                StationContent.BLOCK.get(), StationContent.WOOD_FENCE.get(), StationContent.FENCE.get());
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? SPRUCE_LECTERN_TINT : -1, StationContent.ITEM.get());
    }
    @SubscribeEvent public static void fenceModels(ModelEvent.ModifyBakingResult event) {
        for (var block : new net.minecraft.world.level.block.Block[]{StationContent.WOOD_FENCE.get(), StationContent.FENCE.get()})
            for (var state : block.getStateDefinition().getPossibleStates())
                event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (key, model) -> new StationFenceModel(model));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(MaidTweaks.ID).orElseThrow().registerExtensionPoint(
                IConfigScreenFactory.class, (container, parent) -> new SyncSettingsScreen(parent));
        event.enqueueWork(ClientSync::prepare);
    }
}
