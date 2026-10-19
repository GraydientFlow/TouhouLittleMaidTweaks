// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;

public final class RegularBreeding extends MaidCheckRateTask {
    public RegularBreeding() {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        setMaxCheckRate(20);
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long time) {
        if (!AnimalWork.active(maid)) return;
        AnimalWork.findBreeding(maid).ifPresent(animal -> {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, animal, 0.6f, 0);
            if (!animal.closerThan(maid, 2) || !AnimalWork.allowFeedNow(maid, animal)) return;
            ItemStack food = ItemsUtil.getStack(maid.getAvailableInv(false), animal::isFood);
            if (food.isEmpty()) return;
            food.shrink(1);
            maid.swing(InteractionHand.MAIN_HAND);
            animal.setInLove(null);
            if (maid.getOwner() instanceof ServerPlayer owner)
                InitTrigger.MAID_EVENT.get().trigger(owner, TriggerType.MAID_FEED_ANIMAL);
        });
    }
}
