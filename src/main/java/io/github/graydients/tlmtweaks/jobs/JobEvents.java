// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtTarget;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class JobEvents {
    @SubscribeEvent public static void keepLockedSeat(EntityMountEvent event) {
        if (JobConfig.seatLock() && event.isDismounting() && !event.getLevel().isClientSide()
                && event.getEntityMounting() instanceof EntityMaid maid
                && !maid.isRideable() && maid.isAlive() && event.getEntityBeingMounted().isAlive())
            event.setCanceled(true);
    }
    @SubscribeEvent public static void beforeHit(MaidHurtTarget.Pre event) {
        var maid = event.getMaid();
        if (maid.level().isClientSide() || !AnimalWork.active(maid)) return;
        if (!(event.getTarget() instanceof Animal animal) || !AnimalWork.allowHitNow(maid, animal)) {
            event.setCanceled(true);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        StationMembership.clear();
        ToolReplacement.clear();
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)
            AnimalWork.invalidate(maid);
    }
}
