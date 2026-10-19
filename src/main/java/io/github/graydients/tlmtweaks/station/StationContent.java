// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StationContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MaidTweaks.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MaidTweaks.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MaidTweaks.ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MaidTweaks.ID);
    public static final DeferredHolder<Block, StationBlock> BLOCK = BLOCKS.register("animal_management_station", () ->
            new StationBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN).noOcclusion()));
    // Two connection families preserve vanilla wood/non-wood fence compatibility.
    public static final DeferredHolder<Block, StationFenceBlock> WOOD_FENCE = BLOCKS.register("animal_station_wood_fence", () ->
            new StationFenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN).noOcclusion()));
    public static final DeferredHolder<Block, StationFenceBlock> FENCE = BLOCKS.register("animal_station_fence", () ->
            new StationFenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN).noOcclusion()));
    public static boolean isStation(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof StationBlock || state.getBlock() instanceof StationFenceBlock;
    }
    public static final DeferredHolder<Item, BlockItem> ITEM = ITEMS.register("animal_management_station", () -> new StationItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> ENTITY = ENTITIES.register("animal_management_station", () ->
            BlockEntityType.Builder.of(StationBlockEntity::new, BLOCK.get(), WOOD_FENCE.get(), FENCE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<StationMenu>> MENU = MENUS.register("animal_station", () ->
            IMenuTypeExtension.create((id, inv, data) -> new StationMenu(id, inv, data.readBlockPos())));
    private StationContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); }
}
