// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-19.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.FishingSeats;
import io.github.graydients.tlmtweaks.jobs.ChairUse;
import io.github.graydients.tlmtweaks.jobs.SeatWork;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class SeatWorkEntityMixin extends TamableAnimal implements SeatWork.Context, FishingSeats.Context, ChairUse.Context {
    protected SeatWorkEntityMixin(EntityType<? extends TamableAnimal> type, Level level) { super(type, level); }

    @Override public boolean graydients$boardingCoolingDown() { return boardingCooldown > 0; }

    @Unique private ChairUse.Approach graydients$seatApproach;
    @Override public ChairUse.Approach graydients$seatApproach() { return graydients$seatApproach; }
    @Override public void graydients$seatApproach(ChairUse.Approach approach) { graydients$seatApproach = approach; }

    @Unique private boolean graydients$checkingSeatWork;
    @Unique private WalkTarget graydients$pendingSeatWork;
    @Unique private boolean graydients$nativeRidingWork;
    @Unique private Activity graydients$lastSchedule;
    @Unique private FishingSeats graydients$fishingSeats;

    @Override public FishingSeats graydients$fishingSeats() {
        if (graydients$fishingSeats == null) graydients$fishingSeats = new FishingSeats();
        return graydients$fishingSeats;
    }

    @Override public void graydients$seatWork(boolean active) { graydients$checkingSeatWork = active; }
    @Override public boolean graydients$seatWork() { return graydients$checkingSeatWork; }
    @Override public void graydients$nativeRidingWork(boolean active) { graydients$nativeRidingWork = active; }
    @Override public boolean graydients$nativeRidingWork() { return graydients$nativeRidingWork; }
    @Override public void graydients$seatWorkTarget(WalkTarget target) { graydients$pendingSeatWork = target; }

    @Inject(method = "canBrainMoving", at = @At("HEAD"), cancellable = true)
    private void allowJobSelection(CallbackInfoReturnable<Boolean> callback) {
        if (graydients$checkingSeatWork) {
            var maid = (EntityMaid) (Object) this;
            callback.setReturnValue(!maid.isMaidInSittingPose() && !maid.isSleeping() && !maid.isLeashed());
        }
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void followScheduledActivity(CallbackInfo callback) {
        var maid = (EntityMaid) (Object) this;
        ChairUse.arrive(maid);
        graydients$lastSchedule = SeatWork.followSchedule(maid, graydients$lastSchedule);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void resumeSelectedWork(CallbackInfo callback) {
        var selected = graydients$pendingSeatWork;
        graydients$pendingSeatWork = null;
        var maid = (EntityMaid) (Object) this;
        if (selected != null) SeatWork.resume(maid, selected);
    }
}
