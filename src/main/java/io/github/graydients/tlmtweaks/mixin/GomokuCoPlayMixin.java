// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import io.github.graydients.tlmtweaks.board.ChessCoPlay;
import io.github.graydients.tlmtweaks.board.CoPlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockGomoku.class, remap = false)
public abstract class GomokuCoPlayMixin {
    @Shadow private static int[] getChessPos(double x, double y, GomokuPart part) {
        throw new AssertionError();
    }

    @Shadow private static boolean isClickChessBox(double x, double y, GomokuPart part, Direction direction) {
        throw new AssertionError();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void coPlay(ItemStack item, BlockState state, Level level, BlockPos pos, Player player,
                        InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        if (!(level instanceof ServerLevel server) || hand != InteractionHand.MAIN_HAND) return;
        GomokuPart part = state.getValue(BlockGomoku.PART);
        BlockPos center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (!(level.getBlockEntity(center) instanceof TileEntityGomoku board)) return;
        if (CoPlay.hasMaid(server, board)) {
            if (CoPlay.hasSession(board)) CoPlay.clear(board);
            return;
        }
        Vec3 location = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction facing = state.getValue(BlockGomoku.FACING);
        if (isClickChessBox(location.x, location.z, part, facing)) {
            if (!item.isEmpty() || !CoPlay.hasSession(board)) return;
            board.reset();
            CoPlay.clear(board);
            ChessCoPlay.resetSound(level, center);
            callback.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }
        if (!item.isEmpty()) {
            if (CoPlay.hasSession(board)) callback.setReturnValue(ItemInteractionResult.FAIL);
            return;
        }
        int[] point = getChessPos(location.x, location.z, part);
        if (!CoPlay.ready(board, server, player, CoPlay.turn(board), board::reset, "black", "white")) {
            callback.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }
        if (point == null) {
            callback.setReturnValue(ItemInteractionResult.SUCCESS);
            return;
        }
        int x = point[0];
        int y = point[1];
        byte[][] stones = board.getChessData();
        if (stones[x][y] != Point.EMPTY) {
            callback.setReturnValue(ItemInteractionResult.FAIL);
            return;
        }
        int side = CoPlay.turn(board);
        Point placed = new Point(x, y, side == 0 ? Point.BLACK : Point.WHITE);
        board.setChessData(x, y, placed.type);
        Statue result = MaidGomokuAI.getStatue(stones, placed);
        // Keeping the stock player-turn flag true also rejects delayed maid AI packets.
        board.setPlayerTurn(true);
        if (result == Statue.WIN) {
            CoPlay.finish(board, server, side == 0 ? CoPlay.FIRST_WIN : CoPlay.SECOND_WIN, "black", "white");
        } else if (result == Statue.DRAW) {
            CoPlay.finish(board, server, CoPlay.DRAW, "black", "white");
        } else {
            CoPlay.advance(board);
        }
        ChessCoPlay.moveSound(level, pos);
        callback.setReturnValue(ItemInteractionResult.SUCCESS);
    }

    @Inject(method = "startMaidSit", at = @At("HEAD"), cancellable = true)
    private void maidSeat(EntityMaid maid, BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        if (level.getBlockEntity(pos) instanceof TileEntityGomoku board && !CoPlay.maidMaySit(board))
            callback.cancel();
    }
}
