// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class ToolReplacement {
    private static final Map<EntityMaid, EnumMap<EquipmentSlot, Item>> PENDING = new WeakHashMap<>();
    private ToolReplacement() {}
    public static EquipmentSlot equippedSlot(EntityMaid maid, ItemStack stack) {
        for (EquipmentSlot slot : EquipmentSlot.values()) if (maid.getItemBySlot(slot) == stack) return slot;
        return null;
    }
    public static void broken(EntityMaid maid, EquipmentSlot slot, Item item) {
        if (slot != null && !maid.level().isClientSide() && JobConfig.replaceTools())
            PENDING.computeIfAbsent(maid, key -> new EnumMap<>(EquipmentSlot.class)).put(slot, item);
    }
    public static void clear() { PENDING.clear(); }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) return;
        Map<EquipmentSlot, Item> replacements = PENDING.remove(maid);
        if (replacements == null || !JobConfig.replaceTools() || !maid.isAlive()) return;
        var backpack = maid.getAvailableBackpackInv();
        for (var entry : replacements.entrySet()) {
            if (!maid.getItemBySlot(entry.getKey()).isEmpty()) continue;
            for (int slot = 0; slot < backpack.getSlots(); slot++) {
                ItemStack spare = backpack.getStackInSlot(slot);
                if (spare.isEmpty() || spare.getItem() != entry.getValue()) continue;
                maid.setItemSlot(entry.getKey(), backpack.extractItem(slot, 1, false));
                break;
            }
        }
    }
}
