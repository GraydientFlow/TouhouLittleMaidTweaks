// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ride.MaidRideFindWaterTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFishing;
import io.github.graydients.tlmtweaks.jobs.FishingSeats;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidRideFindWaterTask.class, remap = false)
public abstract class FishingSeatCastMixin {
    @Shadow private BlockPos waterPos;

    @Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At("HEAD"), cancellable = true)
    private void useCurrentSeat(ServerLevel level, EntityMaid maid, long time, CallbackInfo callback) {
        if (!JobConfig.seatRecovery() || !(maid.getTask() instanceof TaskFishing) || !maid.isPassenger()) return;
        waterPos = FishingSeats.waterAt(maid, maid.getVehicle());
        if (waterPos == null) callback.cancel();
        // Actual alternative work, rather than an unusable seat alone, triggers a dismount.
    }
}
