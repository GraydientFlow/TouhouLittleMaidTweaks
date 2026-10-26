// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import io.github.graydients.tlmtweaks.storage.StorageFilters;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WirelessIOContainer.class, remap = false)
public abstract class WirelessIOFilterMixin extends AbstractContainerMenu {
    @Shadow @Final private ItemStack wirelessIO;
    @Shadow @Final private ItemStackHandler filterListInv;
    @Unique private int[] graydients$limits;
    @Unique private Player graydients$viewer;
    protected WirelessIOFilterMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "addWirelessIOSlots", at = @At("HEAD"), cancellable = true)
    private void referenceSlots(CallbackInfo callback) {
        graydients$limits = StorageFilters.limits(wirelessIO);
        for (int i = 0; i < StorageFilters.SIZE; i++) {
            addSlot(new StorageFilters.FilterSlot(filterListInv, i, 62 + i % 3 * 18, 17 + i / 3 * 18,
                    graydients$limits));
            addDataSlot(DataSlot.shared(graydients$limits, i));
        }
        callback.cancel();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void returnOriginalItems(int id, Inventory inventory, ItemStack item, CallbackInfo callback) {
        graydients$viewer = inventory.player;
        if (!inventory.player.level().isClientSide)
            StorageFilters.initialize(inventory.player, wirelessIO, filterListInv, graydients$limits);
    }

    @Unique private void graydients$reference(int index, ItemStack stack, int quantity, Player player) {
        filterListInv.setStackInSlot(index, stack.copyWithCount(1));
        graydients$limits[index] = ItemWirelessIO.isMaidToChest(wirelessIO) || stack.isEmpty()
                ? 1 : StorageFilters.clamp(quantity);
        StorageFilters.save(player.registryAccess(), wirelessIO, filterListInv, graydients$limits);
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void editReference(int slotId, int button, ClickType type, Player player, CallbackInfo callback) {
        if (slotId < 0 || slotId >= slots.size() || !(slots.get(slotId) instanceof StorageFilters.FilterSlot slot)) return;
        if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
            var carried = getCarried();
            if (carried.isEmpty() || EntityMaid.canInsertItem(carried)) {
                int quantity = carried.getCount();
                if (button == 1 && !carried.isEmpty())
                    quantity = ItemStack.isSameItem(slot.getItem(), carried) ? slot.quantity() + 1 : 1;
                graydients$reference(slot.getSlotIndex(), carried, quantity, player);
            }
        } else if (type == ClickType.QUICK_MOVE || type == ClickType.THROW) {
            graydients$reference(slot.getSlotIndex(), ItemStack.EMPTY, 1, player);
        }
        callback.cancel();
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void copyQuickReference(Player player, int slotId, CallbackInfoReturnable<ItemStack> callback) {
        // Return EMPTY even after copying: native shift-transfer otherwise repeats while the real source is unchanged.
        callback.setReturnValue(ItemStack.EMPTY);
        if (slotId < 0 || slotId >= 36 || slotId == 27 + player.getInventory().selected) return;
        var stack = slots.get(slotId).getItem();
        if (stack.isEmpty() || !EntityMaid.canInsertItem(stack)) return;
        int empty = -1;
        for (int i = 0; i < StorageFilters.SIZE; i++) {
            var reference = filterListInv.getStackInSlot(i);
            if (ItemStack.isSameItem(reference, stack)) { graydients$reference(i, stack, stack.getCount(), player); return; }
            if (reference.isEmpty() && empty < 0) empty = i;
        }
        if (empty >= 0) graydients$reference(empty, stack, stack.getCount(), player);
    }

    @Override public void broadcastChanges() {
        if (graydients$limits != null && ItemWirelessIO.isMaidToChest(wirelessIO)) {
            boolean changed = false;
            for (int i = 0; i < graydients$limits.length; i++) {
                if (graydients$limits[i] != 1) { graydients$limits[i] = 1; changed = true; }
            }
            if (changed && graydients$viewer != null && !graydients$viewer.level().isClientSide)
                StorageFilters.save(graydients$viewer.registryAccess(), wirelessIO, filterListInv, graydients$limits);
        }
        super.broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        int action = id - StorageFilters.BUTTON_START;
        if (action < 0 || action >= StorageFilters.SIZE * 2 || player.level().isClientSide
                || player.containerMenu != this || !stillValid(player) || player.getMainHandItem() != wirelessIO
                || ItemWirelessIO.isMaidToChest(wirelessIO)) return false;
        int slot = action / 2;
        if (filterListInv.getStackInSlot(slot).isEmpty()) return false;
        graydients$limits[slot] = StorageFilters.clamp(graydients$limits[slot] + (action % 2 == 1 ? 1 : -1));
        StorageFilters.save(player.registryAccess(), wirelessIO, filterListInv, graydients$limits);
        broadcastChanges();
        return true;
    }

    @Redirect(method = "clicked", at = @At(value = "INVOKE", target =
            "Lcom/github/tartaricacid/touhoulittlemaid/item/ItemWirelessIO;setFilterList(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/items/ItemStackHandler;)V"))
    private void preserveReferenceMarker(HolderLookup.Provider registries, ItemStack item, ItemStackHandler filters) {
        StorageFilters.save(registries, item, filters, graydients$limits);
    }
}
