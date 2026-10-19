// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.AnimalWork;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class AnimalSweepMixin {
    @Inject(method = "doSweepHurt", at = @At("HEAD"), cancellable = true)
    private void preventCollateral(Entity target, CallbackInfo callback) {
        if (AnimalWork.active((EntityMaid) (Object) this)) callback.cancel();
    }
}
