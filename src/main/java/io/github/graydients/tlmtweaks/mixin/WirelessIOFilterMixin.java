// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import io.github.graydients.tlmtweaks.storage.StorageFilters;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WirelessIOContainer.class, remap = false)
public abstract class WirelessIOFilterMixin extends AbstractContainerMenu {
    @Shadow @Final private ItemStack wirelessIO;
    @Shadow @Final private ItemStackHandler filterListInv;

    protected WirelessIOFilterMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "addWirelessIOSlots", at = @At("HEAD"), cancellable = true)
    private void referenceSlots(CallbackInfo callback) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
            addSlot(new StorageFilters.FilterSlot(filterListInv, column + row * 3, 62 + column * 18, 17 + row * 18));
        callback.cancel();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void returnOriginalItems(int id, Inventory inventory, ItemStack item, CallbackInfo callback) {
        if (!inventory.player.level().isClientSide) StorageFilters.initialize(inventory.player, wirelessIO, filterListInv);
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void editReference(int slotId, int button, ClickType type, Player player, CallbackInfo callback) {
        if (slotId < 0 || slotId >= slots.size() || !(slots.get(slotId) instanceof StorageFilters.FilterSlot slot)) return;
        if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
            var carried = getCarried();
            if (carried.isEmpty() || EntityMaid.canInsertItem(carried)) {
                filterListInv.setStackInSlot(slot.getSlotIndex(), carried.copyWithCount(1));
                StorageFilters.save(player.registryAccess(), wirelessIO, filterListInv);
            }
        } else if (type == ClickType.QUICK_MOVE || type == ClickType.THROW) {
            filterListInv.setStackInSlot(slot.getSlotIndex(), ItemStack.EMPTY);
            StorageFilters.save(player.registryAccess(), wirelessIO, filterListInv);
        }
        // Also block creative cloning, hotbar swaps, double-click collection and drag insertion.
        callback.cancel();
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void preventReferenceTransfer(Player player, int slotId, CallbackInfoReturnable<ItemStack> callback) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof StorageFilters.FilterSlot)
            callback.setReturnValue(ItemStack.EMPTY);
    }

    @Redirect(method = "clicked", at = @At(value = "INVOKE", target =
            "Lcom/github/tartaricacid/touhoulittlemaid/item/ItemWirelessIO;setFilterList(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/items/ItemStackHandler;)V"))
    private void preserveReferenceMarker(HolderLookup.Provider registries, ItemStack item, ItemStackHandler filters) {
        StorageFilters.save(registries, item, filters);
    }
}
