// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.pickup.PickupDelay;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidPickupDelayMixin {
    // This precedes ItemResultPre: Curios containers can perform pickup during that event.
    @Inject(method = "pickupItem", at = @At("HEAD"), cancellable = true)
    private void delayItem(ItemEntity item, boolean simulate, CallbackInfoReturnable<Boolean> callback) {
        if (!PickupDelay.allowed((EntityMaid) (Object) this, item)) callback.setReturnValue(false);
    }

    @Redirect(method = "pushEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> immediateItemRange(Level level, Entity except, AABB originalBox, Predicate<? super Entity> predicate) {
        return PickupDelay.nearby(level, (EntityMaid) (Object) this, originalBox, predicate);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void cancelDelayedItemWalk(CallbackInfo callback) { PickupDelay.cancelDelayedWalk((EntityMaid) (Object) this); }
}
