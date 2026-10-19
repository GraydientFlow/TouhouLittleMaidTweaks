// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Reuses native placement, consumption, collision checks and NeoForge's placement transaction. */
public final class StationItem extends BlockItem {
    public StationItem(Block block, Properties properties) { super(block, properties); }
    private static final class FenceContext extends BlockPlaceContext {
        final BlockState fence;
        FenceContext(UseOnContext context, BlockState fence) { super(context); replaceClicked = true; this.fence = fence; }
        @Override public boolean canPlace() {
            return getLevel().getBlockState(getClickedPos()) == fence
                    && (getPlayer() == null || getPlayer().mayUseItemAt(getClickedPos(), getClickedFace(), getItemInHand()));
        }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        BlockState fence = context.getLevel().getBlockState(context.getClickedPos());
        if (fence.getBlock() instanceof FenceBlock && !(fence.getBlock() instanceof StationFenceBlock) && !fence.hasBlockEntity())
            return place(new FenceContext(context, fence));
        return super.useOn(context);
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        if (!(context instanceof FenceContext onFence)) return super.getPlacementState(context);
        var block = onFence.fence.is(BlockTags.WOODEN_FENCES) ? StationContent.WOOD_FENCE.get() : StationContent.FENCE.get();
        BlockState state = StationFenceBlock.copyConnections(onFence.fence, block.defaultBlockState())
                .setValue(StationBlock.FACING, context.getHorizontalDirection().getOpposite());
        return canPlace(context, state) ? state : null;
    }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        if (!super.placeBlock(context, state)) return false;
        BlockPos pos = context.getClickedPos();
        if (context instanceof FenceContext onFence && context.getLevel().getBlockEntity(pos) instanceof StationBlockEntity station)
            station.setOriginalFence(onFence.fence);
        return true;
    }
}
