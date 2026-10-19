// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBoardGameTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.ChairUse;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidBoardGameTask.class, remap = false)
public abstract class BoardSeatPriorityMixin {
    @Unique private EntityMaid graydients$worker;

    @Inject(method = "findGameBlock", at = @At("HEAD"))
    private void rememberWorker(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<BlockPos> callback) { graydients$worker = maid; }
    @Inject(method = "findGameBlock", at = @At("RETURN"))
    private void clearWorker(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<BlockPos> callback) { graydients$worker = null; }

    @Inject(method = "isOccupied", at = @At("RETURN"), cancellable = true)
    private void allowWorkPriority(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (!JobConfig.seatRecovery() || !callback.getReturnValueZ() || graydients$worker == null) return;
        var seat = ChairUse.boardSeat(level, pos);
        if (seat != null && ChairUse.canTake(graydients$worker, seat)) callback.setReturnValue(false);
    }

    @Redirect(method = "lambda$start$0", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/api/block/IBoardGameBlock;startMaidSit(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private static void claimBoard(IBoardGameBlock block, EntityMaid maid, BlockState state, Level level, BlockPos pos) {
        ChairUse.useBoard(block, maid, state, level, pos);
    }
}
