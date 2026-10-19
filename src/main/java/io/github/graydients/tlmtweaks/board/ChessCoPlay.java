// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.board;

import com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import com.github.tartaricacid.touhoulittlemaid.util.CChessUtil;
import com.github.tartaricacid.touhoulittlemaid.util.WChessUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server-side player turns for TLM's Western and Chinese chess boards. */
public final class ChessCoPlay {
    private ChessCoPlay() {}

    public static ItemInteractionResult western(ItemStack item, BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || hand != InteractionHand.MAIN_HAND) return null;
        GomokuPart part = state.getValue(com.github.tartaricacid.touhoulittlemaid.block.BlockWChess.PART);
        BlockPos center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (!(level.getBlockEntity(center) instanceof TileEntityWChess board)) return null;
        if (CoPlay.hasMaid(server, board)) {
            if (CoPlay.hasSession(board)) CoPlay.clear(board);
            return null;
        }
        Vec3 click = clickPosition(state, pos, hit, part);
        if (WChessUtil.isClickResetArea(click)) {
            if (!item.isEmpty() || !CoPlay.hasSession(board)) return null;
            board.reset();
            CoPlay.clear(board);
            resetSound(level, center);
            return ItemInteractionResult.SUCCESS;
        }
        if (!item.isEmpty()) return CoPlay.hasSession(board) ? ItemInteractionResult.FAIL : null;
        int square = WChessUtil.getClickPosition(click);
        Position position = board.getChessData();
        if (!CoPlay.ready(board, server, player, position.sdPlayer, board::reset, "white", "black"))
            return ItemInteractionResult.SUCCESS;
        if (square < 0 || !Position.IN_BOARD(square)) return ItemInteractionResult.SUCCESS;

        byte[] pieces = position.squares;
        int selected = board.getSelectChessPoint();
        if (selected < 0 || selected >= pieces.length) selected = 0;
        boolean firstSide = position.sdPlayer == 0;
        boolean selectedOwn = firstSide ? WChessUtil.isWhite(pieces[selected]) : WChessUtil.isBlack(pieces[selected]);
        boolean clickedOwn = firstSide ? WChessUtil.isWhite(pieces[square]) : WChessUtil.isBlack(pieces[square]);
        if (!selectedOwn || clickedOwn) {
            if (clickedOwn) {
                board.setSelectChessPoint(square);
                board.refresh();
                moveSound(level, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        int move = Position.MOVE(selected, square);
        if (!position.legalMove(move)) return ItemInteractionResult.FAIL;
        int movingPiece = pieces[selected];
        if (!position.makeMove(move)) {
            invalidMove(level, pos);
            return ItemInteractionResult.FAIL;
        }
        if (position.captured() || Position.PIECE_TYPE(movingPiece) == Position.PIECE_PAWN)
            position.setIrrev();
        board.addChessCounter();
        board.setSelectChessPoint(square);
        moveSound(level, pos);
        if (position.isMate()) {
            CoPlay.finish(board, server, position.inCheck() ? win(firstSide) : CoPlay.DRAW, "white", "black");
        } else if (WChessUtil.reachMoveLimit(position) || WChessUtil.isRepeat(position)) {
            CoPlay.finish(board, server, CoPlay.DRAW, "white", "black");
        } else {
            board.refresh();
        }
        return ItemInteractionResult.SUCCESS;
    }

    public static ItemInteractionResult chinese(ItemStack item, BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || hand != InteractionHand.MAIN_HAND) return null;
        GomokuPart part = state.getValue(com.github.tartaricacid.touhoulittlemaid.block.BlockCChess.PART);
        BlockPos center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        if (!(level.getBlockEntity(center) instanceof TileEntityCChess board)) return null;
        if (CoPlay.hasMaid(server, board)) {
            if (CoPlay.hasSession(board)) CoPlay.clear(board);
            return null;
        }
        Vec3 click = clickPosition(state, pos, hit, part);
        if (CChessUtil.isClickResetArea(click)) {
            if (!item.isEmpty() || !CoPlay.hasSession(board)) return null;
            board.reset();
            CoPlay.clear(board);
            resetSound(level, center);
            return ItemInteractionResult.SUCCESS;
        }
        if (!item.isEmpty()) return CoPlay.hasSession(board) ? ItemInteractionResult.FAIL : null;
        int square = CChessUtil.getClickPosition(click);
        var position = board.getChessData();
        if (!CoPlay.ready(board, server, player, position.sdPlayer, board::reset, "red", "black"))
            return ItemInteractionResult.SUCCESS;
        if (square < 0 || !com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position.IN_BOARD(square))
            return ItemInteractionResult.SUCCESS;

        byte[] pieces = position.squares;
        int selected = board.getSelectChessPoint();
        if (selected < 0 || selected >= pieces.length) selected = 0;
        boolean firstSide = position.sdPlayer == 0;
        boolean selectedOwn = firstSide ? CChessUtil.isRed(pieces[selected]) : CChessUtil.isBlack(pieces[selected]);
        boolean clickedOwn = firstSide ? CChessUtil.isRed(pieces[square]) : CChessUtil.isBlack(pieces[square]);
        if (!selectedOwn || clickedOwn) {
            if (clickedOwn) {
                board.setSelectChessPoint(square);
                board.refresh();
                moveSound(level, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        int move = com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position.MOVE(selected, square);
        if (!position.legalMove(move)) return ItemInteractionResult.FAIL;
        if (!position.makeMove(move)) {
            invalidMove(level, pos);
            return ItemInteractionResult.FAIL;
        }
        if (position.captured()) position.setIrrev();
        board.addChessCounter();
        board.setSelectChessPoint(square);
        moveSound(level, pos);
        if (position.isMate()) {
            CoPlay.finish(board, server, win(firstSide), "red", "black");
        } else if (CChessUtil.reachMoveLimit(position) || CChessUtil.isRepeat(position)) {
            CoPlay.finish(board, server, CoPlay.DRAW, "red", "black");
        } else {
            board.refresh();
        }
        return ItemInteractionResult.SUCCESS;
    }

    private static int win(boolean firstSide) {
        return firstSide ? CoPlay.FIRST_WIN : CoPlay.SECOND_WIN;
    }

    private static Vec3 clickPosition(BlockState state, BlockPos pos, BlockHitResult hit, GomokuPart part) {
        return hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())
                .add(part.getPosX() - 0.5, 0, part.getPosY() - 0.5)
                .yRot(state.getValue(BlockJoy.FACING).toYRot() * Mth.DEG_TO_RAD);
    }

    public static void moveSound(Level level, BlockPos pos) {
        level.playSound(null, pos, InitSounds.GOMOKU.get(), SoundSource.BLOCKS,
                1.0f, 0.8f + level.random.nextFloat() * 0.4f);
    }

    public static void resetSound(Level level, BlockPos pos) {
        level.playSound(null, pos, InitSounds.GOMOKU_RESET.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static void invalidMove(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS,
                1.0f, 0.8f + level.random.nextFloat() * 0.4f);
    }
}
