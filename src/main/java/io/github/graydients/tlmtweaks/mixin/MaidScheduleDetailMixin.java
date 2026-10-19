// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=EntityMaid.class,remap=false)
public abstract class MaidScheduleDetailMixin {
    @Inject(method="getScheduleDetail",at=@At("HEAD"),cancellable=true)
    private void activity(CallbackInfoReturnable<Activity> callback) {
        var maid=(EntityMaid)(Object)this;
        callback.setReturnValue(MaidSchedules.activity(maid.getSchedule(), (int)Math.floorMod(maid.level().getDayTime(),24000L)));
    }
}
