// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.control.MaidMoveControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Make the obstacle-jump check use the same Ghost passage lease as movement. */
@Mixin(value = MaidMoveControl.class, remap = false)
public abstract class GhostGateMoveMixin {
    @Shadow @Final
    private EntityMaid maid;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"),
            require = 1)
    private VoxelShape ghostObstacleShape(BlockState state, BlockGetter blocks, BlockPos pos) {
        if (blocks instanceof Level && state.getBlock() instanceof FenceGateBlock && GhostGates.mode(maid) == 2) {
            // An authorized maid sees an empty shape; expired and other maids' leases stay solid.
            return state.getCollisionShape(blocks, pos, CollisionContext.of(maid));
        }
        return state.getCollisionShape(blocks, pos);
    }
}
