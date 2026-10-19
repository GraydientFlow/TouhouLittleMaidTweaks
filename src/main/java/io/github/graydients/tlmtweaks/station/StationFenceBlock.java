// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A real fence boundary carrying a station. The original fence state lives in its block entity. */
public final class StationFenceBlock extends FenceBlock implements EntityBlock {
    public static final MapCodec<FenceBlock> CODEC = simpleCodec(StationFenceBlock::new);
    private final VoxelShape[] outlines = new VoxelShape[64], collisions = new VoxelShape[64];

    public StationFenceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(StationBlock.FACING, Direction.NORTH).setValue(StationBlock.HAS_MILK, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            int index = shapeIndex(state);
            if (outlines[index] == null) {
                Direction facing = state.getValue(StationBlock.FACING);
                outlines[index] = Shapes.or(StationShapes.outline(facing,true), super.getShape(state, null, BlockPos.ZERO, CollisionContext.empty()));
                collisions[index] = Shapes.or(StationShapes.collision(facing,true), super.getCollisionShape(state, null, BlockPos.ZERO, CollisionContext.empty()));
            }
        }
    }
    @Override public MapCodec<FenceBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(StationBlock.FACING, StationBlock.HAS_MILK);
    }
    private int shapeIndex(BlockState state) { return getAABBIndex(state) | state.getValue(StationBlock.FACING).get2DDataValue()<<4; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return outlines[shapeIndex(state)]; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return collisions[shapeIndex(state)]; }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // FenceBlock's fence-only occlusion shape wrongly culls the base beside connected rails.
        return outlines[shapeIndex(state)];
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation).setValue(StationBlock.FACING, rotation.rotate(state.getValue(StationBlock.FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(StationBlock.FACING, mirror.mirror(state.getValue(StationBlock.FACING)));
    }
    public static BlockState copyConnections(BlockState from, BlockState to) {
        for (var property : PROPERTY_BY_DIRECTION.values()) if (from.hasProperty(property) && to.hasProperty(property)) to = to.setValue(property, from.getValue(property));
        if (from.hasProperty(WATERLOGGED) && to.hasProperty(WATERLOGGED)) to = to.setValue(WATERLOGGED, from.getValue(WATERLOGGED));
        return to;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof StationBlockEntity station && station.originalFence().getBlock() instanceof FenceBlock) {
            BlockState restored = copyConnections(state, station.originalFence());
            return level.setBlock(pos, Block.updateFromNeighbourShapes(restored, level, pos), Block.UPDATE_ALL);
        }
        return super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult hit, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(StationContent.ITEM.get());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationBlockEntity(pos, state); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return StationBlock.collectMilk(stack, level, pos, player, hand);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return StationBlock.openMenu(level, pos, player);
    }
}
