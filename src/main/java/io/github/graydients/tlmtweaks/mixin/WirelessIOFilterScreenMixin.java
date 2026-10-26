// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.gui.item.WirelessIOContainerGui;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.other.WirelessIOContainer;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.storage.StorageFilters;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = WirelessIOContainerGui.class, remap = false)
public abstract class WirelessIOFilterScreenMixin extends AbstractContainerScreen<WirelessIOContainer> {
    @Shadow private boolean isMaidToChest;
    protected WirelessIOFilterScreenMixin(WirelessIOContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && !isMaidToChest) {
            for (var slot : menu.slots) {
                if (slot instanceof StorageFilters.FilterSlot filter && slot.hasItem()
                        && x >= leftPos + slot.x && x < leftPos + slot.x + 16
                        && y >= topPos + slot.y && y < topPos + slot.y + 16) {
                    if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            StorageFilters.scrollButton(filter.getSlotIndex(), vertical > 0));
                    return true;
                }
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, String count) {
        if (slot instanceof StorageFilters.FilterSlot filter) {
            int quantity = isMaidToChest ? 1 : filter.quantity();
            count = quantity > 1 ? Integer.toString(quantity) : null;
        }
        super.renderSlotContents(graphics, stack, slot, count);
    }
    @Override protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (!isMaidToChest && hoveredSlot instanceof StorageFilters.FilterSlot filter && hoveredSlot.hasItem()) {
            var lines = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
            lines.add(Component.translatable("gui." + MaidTweaks.ID + ".storage.limit", filter.quantity()));
            graphics.renderTooltip(font, lines, Optional.empty(), hoveredSlot.getItem(), x, y);
        } else super.renderTooltip(graphics, x, y);
    }
}
