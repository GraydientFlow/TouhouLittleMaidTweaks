// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.graydients.tlmtweaks.jobs.ChairUse;
import io.github.graydients.tlmtweaks.jobs.SeatWork;
import java.util.List;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MaidBrain.class, remap = false)
public abstract class SeatWorkBrainMixin {
    @Redirect(method = "registerRideWorkGoals", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/api/task/IMaidTask;createRideBrainTasks(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/util/List;"))
    private static List<Pair<Integer, BehaviorControl<? super EntityMaid>>> workWhileSeated(IMaidTask job, EntityMaid maid) {
        return SeatWork.tasks(job, maid, job.createRideBrainTasks(maid));
    }

    @Redirect(method = "registerWorkGoals", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/api/task/IMaidTask;createBrainTasks(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/util/List;"))
    private static List<Pair<Integer, BehaviorControl<? super EntityMaid>>> workThenRest(IMaidTask job, EntityMaid maid) {
        return ChairUse.casualTasks(job.createBrainTasks(maid));
    }

    @Redirect(method = "registerIdleGoals", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;addActivity(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;)V"))
    private static void restInVacantChair(Brain<EntityMaid> brain, Activity activity,
            ImmutableList<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks) {
        brain.addActivity(activity, ImmutableList.copyOf(ChairUse.casualTasks(tasks)));
    }
}
