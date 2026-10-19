// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.board;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Player seats and results saved on the center tile of each TLM board game. */
public final class CoPlay {
    public static final int FIRST_WIN = 1;
    public static final int SECOND_WIN = 2;
    public static final int DRAW = 3;

    private static final String KEY = MaidTweaks.ID + "_co_play";
    private static final String FIRST = "First";
    private static final String SECOND = "Second";
    private static final String TURN = "Turn";
    private static final String RESULT = "Result";
    private static final String MESSAGE = "message." + MaidTweaks.ID + ".coplay.";

    private CoPlay() {}

    public static boolean hasSession(TileEntityJoy board) {
        return data(board).hasUUID(FIRST);
    }

    public static boolean hasTwoPlayers(TileEntityJoy board) {
        return data(board).hasUUID(FIRST) && data(board).hasUUID(SECOND);
    }

    public static boolean hasMaid(ServerLevel level, TileEntityJoy board) {
        Entity sit = level.getEntity(board.getSitId());
        return sit instanceof EntitySit && sit.getFirstPassenger() instanceof EntityMaid;
    }

    /** A maid may take a waiting player's place, but cannot interrupt two players. */
    public static boolean maidMaySit(TileEntityJoy board) {
        if (hasTwoPlayers(board)) return false;
        if (hasSession(board)) clear(board);
        return true;
    }

    public static void clear(TileEntityJoy board) {
        board.getPersistentData().remove(KEY);
        board.refresh();
    }

    public static int turn(TileEntityJoy board) {
        return data(board).getInt(TURN);
    }

    public static void advance(TileEntityJoy board) {
        CompoundTag seats = data(board);
        seats.putInt(TURN, 1 - seats.getInt(TURN));
        save(board, seats);
    }

    /** Returns true only when the correct player can make the current move. */
    public static boolean ready(TileEntityJoy board, ServerLevel level, Player player, int currentSide,
                                Runnable resetBoard, String firstColor, String secondColor) {
        CompoundTag seats = data(board);
        if (!seats.hasUUID(FIRST)) {
            resetBoard.run();
            seats = new CompoundTag();
            seats.putUUID(FIRST, player.getUUID());
            seats.putInt(TURN, 0);
            save(board, seats);
            say(player, "waiting", color(firstColor));
            return false;
        }

        UUID first = seats.getUUID(FIRST);
        if (!seats.hasUUID(SECOND)) {
            if (first.equals(player.getUUID())) {
                say(player, "still_waiting");
                return false;
            }
            seats.putUUID(SECOND, player.getUUID());
            save(board, seats);
            ServerPlayer firstPlayer = level.getServer().getPlayerList().getPlayer(first);
            if (firstPlayer != null) say(firstPlayer, "started_first", color(firstColor));
            say(player, "started_second", color(secondColor));
            return false;
        }

        int side = first.equals(player.getUUID()) ? 0
                : seats.getUUID(SECOND).equals(player.getUUID()) ? 1 : -1;
        if (side < 0) {
            say(player, "occupied");
            return false;
        }
        if (seats.getInt(RESULT) != 0) {
            say(player, "finished", outcome(seats.getInt(RESULT), firstColor, secondColor));
            return false;
        }
        if (side != currentSide) {
            say(player, "wait_turn");
            return false;
        }
        return true;
    }

    public static void finish(TileEntityJoy board, ServerLevel level, int result,
                              String firstColor, String secondColor) {
        CompoundTag seats = data(board);
        seats.putInt(RESULT, result);
        save(board, seats);
        Component outcome = outcome(result, firstColor, secondColor);
        for (String seat : new String[]{FIRST, SECOND}) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(seats.getUUID(seat));
            if (player != null) say(player, "finished", outcome);
        }
    }

    private static Component outcome(int result, String firstColor, String secondColor) {
        return result == DRAW ? Component.translatable(MESSAGE + "draw")
                : Component.translatable(MESSAGE + "winner", color(result == FIRST_WIN ? firstColor : secondColor));
    }

    private static Component color(String name) {
        return Component.translatable(MESSAGE + "side." + name);
    }

    private static void say(Player player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(MESSAGE + key, args));
    }

    private static CompoundTag data(TileEntityJoy board) {
        return board.getPersistentData().getCompound(KEY);
    }

    private static void save(TileEntityJoy board, CompoundTag seats) {
        board.getPersistentData().put(KEY, seats);
        board.refresh();
    }
}
