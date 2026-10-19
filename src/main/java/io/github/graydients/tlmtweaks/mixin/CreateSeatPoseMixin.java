// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-19.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.SeatWork;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Create's mount pose setter also issues TLM's explicit standby order. Riding already supplies the visual pose. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.seat.SeatBlock", remap = false)
public abstract class CreateSeatPoseMixin {
    @Shadow public static boolean canBePickedUp(Entity entity) { throw new AssertionError(); }

    @Redirect(method = "updateEntityAfterFallOn",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/seat/SeatBlock;canBePickedUp(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean respectMaidDismountCooldown(Entity entity) {
        // Create's automatic pickup forces mounting, bypassing vanilla's post-dismount cooldown.
        // Leave explicit mounting and all other passengers on their native paths.
        return !(entity instanceof EntityMaid maid && JobConfig.seatRecovery()
                && ((SeatWork.Context) maid).graydients$boardingCoolingDown()) && canBePickedUp(entity);
    }

    @Redirect(method = "sitDown(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/TamableAnimal;setInSittingPose(Z)V"))
    private static void keepMaidWorkActive(TamableAnimal passenger, boolean sitting) {
        if (!(passenger instanceof EntityMaid) || !JobConfig.seatRecovery()) passenger.setInSittingPose(sitting);
    }
}
