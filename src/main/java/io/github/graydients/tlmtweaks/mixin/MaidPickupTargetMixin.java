// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import io.github.graydients.tlmtweaks.pickup.PickupDelay;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MaidPickupEntitiesTask.class, remap = false)
public abstract class MaidPickupTargetMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at = @At("RETURN"), cancellable = true)
    private void avoidEmptyPickupPath(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) return;
        var targets = maid.getBrain().getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get()).orElse(List.of());
        if (!PickupDelay.hasAllowedTarget(maid, targets)) callback.setReturnValue(false);
    }

    @Inject(method = "getItems", at = @At("RETURN"), cancellable = true)
    private void filterDelayedItems(EntityMaid maid, CallbackInfoReturnable<List<Entity>> callback) {
        var targets = callback.getReturnValue();
        var filtered = PickupDelay.filterTargets(maid, targets);
        if (filtered != targets) callback.setReturnValue(filtered);
    }
}
