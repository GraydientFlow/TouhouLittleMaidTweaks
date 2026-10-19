// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import com.mojang.serialization.MapCodec;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);
    public static final BooleanProperty HAS_MILK = BooleanProperty.create("has_milk");
    public StationBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_MILK, false)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HAS_MILK); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StationShapes.outline(state.getValue(FACING),false);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StationShapes.collision(state.getValue(FACING),false);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationBlockEntity(pos, state); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return collectMilk(stack, level, pos, player, hand);
    }
    static ItemInteractionResult collectMilk(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!JobConfig.stationsEnabled()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!stack.is(Items.BUCKET)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            if (StationManager.get(server).hasMilk(station)) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.MILK_BUCKET)));
                level.playSound(null, pos, SoundEvents.COW_MILK, SoundSource.BLOCKS, 1, 1);
                level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            } else player.displayClientMessage(Component.translatable("message." + MaidTweaks.ID + ".station.no_milk"), true);
        }
        // Reserve the bucket interaction even when empty; do not open the settings menu.
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return openMenu(level, pos, player);
    }
    static InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        if (!JobConfig.stationsEnabled()) return InteractionResult.PASS;
        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().is(Items.BUCKET)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StationBlockEntity station)
            serverPlayer.openMenu(station, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
