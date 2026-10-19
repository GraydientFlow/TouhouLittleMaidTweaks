// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.storage;

import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.ArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Native wireless I/O filter data, with reference-only slots and a one-time item refund. */
public final class StorageFilters {
    private static final String REFERENCES = MaidTweaks.ID + ":reference_filters";

    private StorageFilters() {}

    public static void save(HolderLookup.Provider registries, ItemStack wirelessIO, ItemStackHandler filters) {
        var data = filters.serializeNBT(registries);
        data.putBoolean(REFERENCES, true);
        wirelessIO.set(InitDataComponent.FILTER_LIST_TAG, data);
    }

    /** Run only on the server, before the native menu sends its initial item/slot snapshot. */
    public static void initialize(Player player, ItemStack wirelessIO, ItemStackHandler filters) {
        var data = wirelessIO.get(InitDataComponent.FILTER_LIST_TAG);
        if (data != null && data.getBoolean(REFERENCES)) return;
        var refunds = new ArrayList<ItemStack>();
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            var original = filters.getStackInSlot(slot);
            if (original.isEmpty()) continue;
            refunds.add(original.copy());
            filters.setStackInSlot(slot, original.copyWithCount(1));
        }
        // Record conversion before handing back the real items; later opens must not refund copies.
        save(player.registryAccess(), wirelessIO, filters);
        for (var refund : refunds) {
            player.getInventory().add(refund);
            if (!refund.isEmpty()) player.drop(refund, false);
        }
    }

    /** Native inventory operations must never take references or merge real items into them. */
    public static final class FilterSlot extends SlotItemHandler {
        public FilterSlot(ItemStackHandler filters, int slot, int x, int y) { super(filters, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
        @Override public int getMaxStackSize() { return 1; }
    }
}
