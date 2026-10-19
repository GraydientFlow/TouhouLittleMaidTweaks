// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFeedAnimalTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedAnimal;
import com.mojang.datafixers.util.Pair;
import io.github.graydients.tlmtweaks.jobs.AnimalConfigMenu;
import io.github.graydients.tlmtweaks.jobs.AnimalWork;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.RegularBreeding;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = TaskFeedAnimal.class, targets = "com.github.wallev.maidsoulkitchen.task.other.TaskFeedAnimalT", remap = false)
public abstract class AnimalTaskMixin {
    @Inject(method = "createBrainTasks", at = @At("RETURN"), cancellable = true)
    private void replaceBreeding(EntityMaid maid, CallbackInfoReturnable<List<Pair<Integer, BehaviorControl<? super EntityMaid>>>> callback) {
        if (!JobConfig.enabled()) return;
        var tasks = new ArrayList<Pair<Integer, BehaviorControl<? super EntityMaid>>>();
        for (var task : callback.getReturnValue())
            tasks.add(task.getSecond() instanceof MaidFeedAnimalTask
                    || task.getSecond().getClass().getName().equals("com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidFeedAnimalTaskT")
                    ? Pair.of(task.getFirst(), new RegularBreeding()) : task);
        callback.setReturnValue(tasks);
    }
    @Inject(method = "findFirstValidAttackTarget", at = @At("HEAD"), cancellable = true)
    private void chooseSlaughter(EntityMaid maid, CallbackInfoReturnable<Optional<? extends LivingEntity>> callback) {
        if (JobConfig.enabled()) callback.setReturnValue(AnimalWork.findSlaughter(maid));
    }
    @Inject(method = "farAway", at = @At("HEAD"), cancellable = true)
    private void searchLeash(LivingEntity target, EntityMaid maid, CallbackInfoReturnable<Boolean> callback) {
        if (JobConfig.enabled()) callback.setReturnValue(!AnimalWork.targetInRange(maid, target));
    }
    @Inject(method = "getTaskConfigGuiProvider", at = @At("HEAD"), cancellable = true)
    private void jobPage(EntityMaid maid, CallbackInfoReturnable<MenuProvider> callback) {
        if (JobConfig.enabled()) callback.setReturnValue(AnimalConfigMenu.provider(maid));
    }
}
