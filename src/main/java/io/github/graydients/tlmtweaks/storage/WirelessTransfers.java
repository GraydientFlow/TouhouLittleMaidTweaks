// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.storage;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidWirelessIOEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.WirelessIOBauble;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** One batch per maid and native 100-tick interval; only the per-maid tick guard survives the batch. */
public final class WirelessTransfers {
    public interface Context { boolean graydients$beginWirelessTick(int tick); }
    private record IO(ItemStack charm, IItemHandler filters, boolean export, boolean blacklist,
                      Map<Item, Integer> limits, List<Boolean> blocked) {}
    private record Target(IItemHandler inventory) {}
    private WirelessTransfers() {}

    public static void tick(EntityMaid maid) {
        if (maid.level().isClientSide || maid.tickCount % 100 != 0 || maid.guiOpening
                || !((Context)maid).graydients$beginWirelessTick(maid.tickCount)) return;
        var inventory = maid.getAvailableInv(false);
        var imports = new ArrayList<IO>();
        var exports = new ArrayList<IO>();
        var reserves = new IdentityHashMap<Item, Integer>();
        var equipped = maid.getMaidBauble();
        for (int slot = 0; slot < equipped.getSlots(); slot++) {
            var charm = equipped.getStackInSlot(slot);
            if (!charm.is(InitItems.WIRELESS_IO.get())) continue;
            var filters = ItemWirelessIO.getFilterList(maid.registryAccess(), charm);
            var limits = new IdentityHashMap<Item, Integer>();
            int[] quantities = StorageFilters.limits(charm);
            for (int i = 0; i < filters.getSlots(); i++) {
                var reference = filters.getStackInSlot(i);
                if (!reference.isEmpty()) limits.putIfAbsent(reference.getItem(), quantities[i]);
            }
            boolean export = ItemWirelessIO.isMaidToChest(charm);
            boolean blacklist = ItemWirelessIO.isBlacklist(charm);
            var blocked = new ArrayList<>(Collections.nCopies(Math.max(38, inventory.getSlots()), false));
            var saved = ItemWirelessIO.getSlotConfig(charm);
            if (saved != null) {
                for (int i = 0; i < Math.min(blocked.size(), saved.size()); i++) blocked.set(i, saved.get(i));
                blocked.set(inventory.getSlots() - 2, saved.get(36));
                blocked.set(inventory.getSlots() - 1, saved.get(37));
            }
            var io = new IO(charm, filters, export, blacklist, limits, blocked);
            (export ? exports : imports).add(io);
            if (!export && !blacklist) limits.forEach((item, count) -> reserves.merge(item, count, Math::max));
        }
        var counts = new IdentityHashMap<Item, Integer>();
        var targets = new HashMap<BlockPos, Optional<Target>>();
        for (var io : imports) transfer(maid, inventory, io, counts, reserves, targets);
        for (var io : exports) transfer(maid, inventory, io, counts, reserves, targets);
    }

    private static void transfer(EntityMaid maid, IItemHandler inventory, IO io, Map<Item, Integer> counts,
                                 Map<Item, Integer> reserves, Map<BlockPos, Optional<Target>> targets) {
        var pos = ItemWirelessIO.getBindingPos(io.charm);
        float range = maid.getRestrictRadius();
        if (pos == null || maid.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > range * range) return;
        var target = targets.computeIfAbsent(pos, p -> resolve(maid, p));
        if (target.isEmpty()) return;
        var chest = target.get().inventory;
        if (chest != null) {
            MaidWirelessIOEvent event = io.export
                    ? new MaidWirelessIOEvent.MaidToChest(maid, inventory, chest, io.filters, io.blacklist, io.blocked)
                    : new MaidWirelessIOEvent.ChestToMaid(maid, inventory, chest, io.filters, io.blacklist, io.blocked);
            if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
                // Native event handlers can move items themselves, including during a canceled prior IO.
                // Reuse the map, but count live stock after callbacks and before this transfer.
                counts.clear();
                for (int i = 0; i < inventory.getSlots(); i++) {
                    var stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
                if (io.export) export(inventory, chest, io, counts, reserves);
                else refill(chest, inventory, io, counts);
            }
        }
        if (maid.getOwner() instanceof ServerPlayer owner)
            InitTrigger.MAID_EVENT.get().trigger(owner, TriggerType.USE_WIRELESS_IO);
    }

    private static Optional<Target> resolve(EntityMaid maid, BlockPos pos) {
        var block = maid.level().getBlockEntity(pos);
        if (block == null) return Optional.empty();
        for (var type : ChestManager.getAllChestTypes()) {
            if (!type.isChest(block)) continue;
            if (type.getOpenCount(maid.level(), pos, block) > 0) return Optional.empty();
            return Optional.of(new Target(maid.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null)));
        }
        return Optional.empty();
    }

    private static void refill(IItemHandler chest, IItemHandler maid, IO io, Map<Item, Integer> counts) {
        for (int slot = 0; slot < chest.getSlots(); slot++) {
            var source = chest.getStackInSlot(slot);
            if (source.isEmpty() || io.limits.containsKey(source.getItem()) == io.blacklist) continue;
            var item = source.getItem();
            int amount = source.getCount();
            if (!io.blacklist) amount = Math.min(amount, io.limits.get(source.getItem()) - counts.getOrDefault(source.getItem(), 0));
            if (amount <= 0) continue;
            var offered = chest.extractItem(slot, amount, true);
            int accepted = offered.getCount() - WirelessIOBauble.insertItemStacked(maid, offered.copy(), true, io.blocked).getCount();
            if (accepted <= 0) continue;
            var extracted = chest.extractItem(slot, accepted, false);
            var remainder = WirelessIOBauble.insertItemStacked(maid, extracted, false, io.blocked);
            int moved = accepted - remainder.getCount();
            if (!remainder.isEmpty()) chest.insertItem(slot, remainder, false);
            if (moved > 0) counts.merge(item, moved, Integer::sum);
        }
    }

    private static void export(IItemHandler maid, IItemHandler chest, IO io, Map<Item, Integer> counts, Map<Item, Integer> reserves) {
        for (int slot = 0; slot < maid.getSlots(); slot++) {
            if (slot < io.blocked.size() && io.blocked.get(slot)) continue;
            var source = maid.getStackInSlot(slot);
            if (source.isEmpty() || io.limits.containsKey(source.getItem()) == io.blacklist) continue;
            var item = source.getItem();
            int excess = counts.getOrDefault(item, 0) - reserves.getOrDefault(item, 0);
            if (excess <= 0) continue;
            var offered = maid.extractItem(slot, Math.min(source.getCount(), excess), true);
            int accepted = offered.getCount() - ItemHandlerHelper.insertItemStacked(chest, offered.copy(), true).getCount();
            if (accepted <= 0) continue;
            var extracted = maid.extractItem(slot, accepted, false);
            var remainder = ItemHandlerHelper.insertItemStacked(chest, extracted, false);
            int moved = accepted - remainder.getCount();
            if (!remainder.isEmpty()) maid.insertItem(slot, remainder, false);
            if (moved > 0) counts.merge(item, -moved, Integer::sum);
        }
    }
}
