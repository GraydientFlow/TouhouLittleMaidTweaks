// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidBrain.class,remap=false)
public abstract class MaidScheduleBrainMixin {
    @Inject(method="registerSchedule",at=@At("RETURN"))
    private static void schedule(Brain<EntityMaid> brain,EntityMaid maid,CallbackInfo callback) {
        brain.setSchedule(MaidSchedules.chart(maid.getSchedule()));
    }
}
