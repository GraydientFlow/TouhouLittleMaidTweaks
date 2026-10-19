// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.ToolReplacement;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemBreakMixin {
    @Unique private EntityMaid graydients$breakingMaid;
    @Unique private EquipmentSlot graydients$breakingSlot;
    @Unique private Item graydients$breakingItem;
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void capture(int damage, ServerLevel level, LivingEntity entity, Consumer<Item> callback, CallbackInfo info) {
        ItemStack stack = (ItemStack) (Object) this;
        graydients$breakingMaid = null;
        if (!JobConfig.replaceTools() || !(entity instanceof EntityMaid maid) || !stack.isDamageableItem()) return;
        EquipmentSlot slot = ToolReplacement.equippedSlot(maid, stack);
        if (slot == null) return;
        graydients$breakingMaid = maid;
        graydients$breakingSlot = slot;
        graydients$breakingItem = stack.getItem();
    }
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void record(int damage, ServerLevel level, LivingEntity entity, Consumer<Item> callback, CallbackInfo info) {
        if (graydients$breakingMaid != null && ((ItemStack) (Object) this).isEmpty())
            ToolReplacement.broken(graydients$breakingMaid, graydients$breakingSlot, graydients$breakingItem);
        graydients$breakingMaid = null;
    }
}
