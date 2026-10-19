// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Do not offer the top of a closed Ghost gate as a path node. */
@Mixin(value = MaidNodeEvaluator.class, remap = false)
public abstract class GhostGatePathMixin extends WalkNodeEvaluator {
    @Inject(method = "getPathType", at = @At("RETURN"), cancellable = true, require = 1)
    private void ghostGateHeadroom(PathfindingContext context, int x, int y, int z,
                                   CallbackInfoReturnable<PathType> callback) {
        if (callback.getReturnValue() != PathType.WALKABLE
                || !(mob instanceof EntityMaid maid) || GhostGates.mode(maid) != 2) return;
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos gatePos = pos.below();
        var gate = context.getBlockState(gatePos);
        if (gate.getBlock() instanceof FenceGateBlock && !gate.getValue(FenceGateBlock.OPEN)
                && context.getBlockState(pos).getCollisionShape(context.level(), pos).isEmpty()) {
            // TLM's OPEN-above-gate fallback otherwise turns this air space into WALKABLE.
            // OPEN makes the pathfinder descend to the door node at the actual floor height.
            callback.setReturnValue(PathType.OPEN);
        }
    }
}
