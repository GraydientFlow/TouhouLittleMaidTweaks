// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import io.github.graydients.tlmtweaks.board.ChessCoPlay;
import io.github.graydients.tlmtweaks.board.CoPlay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockCChess.class, remap = false)
public abstract class CChessCoPlayMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void coPlay(ItemStack item, BlockState state, Level level, BlockPos pos, Player player,
                        InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        ItemInteractionResult result = ChessCoPlay.chinese(item, state, level, pos, player, hand, hit);
        if (result != null) callback.setReturnValue(result);
    }

    @Inject(method = "maidMove", at = @At("HEAD"), cancellable = true)
    private static void noMaidMove(ServerPlayer player, Level level, BlockPos pos, int move,
                                   boolean maidLost, boolean playerLost, CallbackInfo callback) {
        if (level.getBlockEntity(pos) instanceof TileEntityCChess board && CoPlay.hasSession(board))
            callback.cancel();
    }

    @Inject(method = "startMaidSit", at = @At("HEAD"), cancellable = true)
    private void maidSeat(EntityMaid maid, BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        if (level.getBlockEntity(pos) instanceof TileEntityCChess board && !CoPlay.maidMaySit(board))
            callback.cancel();
    }
}
