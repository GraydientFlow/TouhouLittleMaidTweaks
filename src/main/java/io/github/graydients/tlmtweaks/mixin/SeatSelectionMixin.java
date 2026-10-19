// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindSitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.ChairUse;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.SeatWork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidFindSitTask.class, remap = false)
public abstract class SeatSelectionMixin {
    @Shadow @Final private float speedModifier;
    @Shadow private long chatBubbleKey;

    @Redirect(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at = @At(value = "INVOKE", target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    private Entity seekWorkFromSeat(EntityMaid maid) {
        return ((SeatWork.Context) maid).graydients$seatWork() ? null : maid.getVehicle();
    }

    @Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At("HEAD"), cancellable = true)
    private void seekAndBoard(ServerLevel level, EntityMaid maid, long time, CallbackInfo callback) {
        if (!JobConfig.seatRecovery()) return;
        boolean casual = (Object) this instanceof ChairUse.CasualSeat;
        if (!ChairUse.seek(maid, casual, speedModifier) && !casual)
            chatBubbleKey = maid.getChatBubbleManager().addTextChatBubbleIfTimeout(
                    "chat_bubble.touhou_little_maid.inner.fishing.no_sit", chatBubbleKey);
        callback.cancel();
    }
}
