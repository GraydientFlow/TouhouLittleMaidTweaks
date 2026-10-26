// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.storage;

import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Real item references remain count one; separate menu data holds the requested stock. */
public final class StorageFilters {
    public static final int SIZE = 9;
    public static final int MAXIMUM = 128;
    public static final int BUTTON_START = 100;
    private static final String REFERENCES = MaidTweaks.ID + ":reference_filters";
    private static final String LIMITS = MaidTweaks.ID + ":stock_limits";

    private StorageFilters() {}
    public static int clamp(int count) { return Math.clamp(count, 1, MAXIMUM); }
    public static int scrollButton(int slot, boolean increase) { return BUTTON_START + slot * 2 + (increase ? 1 : 0); }

    public static int[] limits(ItemStack io) {
        int[] result = new int[SIZE];
        Arrays.fill(result, 1);
        var data = io.get(InitDataComponent.FILTER_LIST_TAG);
        if (data != null && !ItemWirelessIO.isMaidToChest(io)) {
            int[] saved = data.getIntArray(LIMITS);
            for (int i = 0; i < Math.min(SIZE, saved.length); i++) result[i] = clamp(saved[i]);
        }
        return result;
    }

    public static void save(HolderLookup.Provider registries, ItemStack io, ItemStackHandler filters, int[] limits) {
        var data = filters.serializeNBT(registries);
        data.putBoolean(REFERENCES, true);
        int[] saved = new int[SIZE];
        for (int i = 0; i < SIZE; i++) saved[i] = ItemWirelessIO.isMaidToChest(io) ? 1 : clamp(limits[i]);
        data.putIntArray(LIMITS, saved);
        io.set(InitDataComponent.FILTER_LIST_TAG, data);
    }

    /** Convert physical filters once, before the native menu sends its initial snapshot. */
    public static void initialize(Player player, ItemStack io, ItemStackHandler filters, int[] limits) {
        var data = io.get(InitDataComponent.FILTER_LIST_TAG);
        if (data != null && data.getBoolean(REFERENCES)) return;
        var refunds = new ArrayList<ItemStack>();
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            var original = filters.getStackInSlot(slot);
            if (original.isEmpty()) continue;
            refunds.add(original.copy());
            limits[slot] = ItemWirelessIO.isMaidToChest(io) ? 1 : clamp(original.getCount());
            filters.setStackInSlot(slot, original.copyWithCount(1));
        }
        save(player.registryAccess(), io, filters, limits);
        for (var refund : refunds) {
            player.getInventory().add(refund);
            if (!refund.isEmpty()) player.drop(refund, false);
        }
    }

    /** Guards prevent native inventory actions from turning references into real items. */
    public static final class FilterSlot extends SlotItemHandler {
        private final int[] limits;
        public FilterSlot(ItemStackHandler filters, int slot, int x, int y, int[] limits) {
            super(filters, slot, x, y); this.limits = limits;
        }
        public int quantity() { return clamp(limits[getSlotIndex()]); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
        @Override public int getMaxStackSize() { return 1; }
    }
}
