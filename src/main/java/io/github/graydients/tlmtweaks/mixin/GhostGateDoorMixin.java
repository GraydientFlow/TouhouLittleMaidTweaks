// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidInteractWithDoor;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidInteractWithDoor.class,remap=false)
public abstract class GhostGateDoorMixin {
    @Inject(method="setFenceGate",at=@At("HEAD"),cancellable=true)
    private static void ghost(Entity entity,Level level,BlockState state,BlockPos pos,boolean open,CallbackInfo callback) {
        if(GhostGates.intercept(entity,level,state,pos,open)) callback.cancel();
    }
}
