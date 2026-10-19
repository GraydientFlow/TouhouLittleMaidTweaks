// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MaidPermissionSlotMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void graydients$readOnlySlots(Slot slot, int index, int button, ClickType type, CallbackInfo callback) {
        var screen = (AbstractContainerScreen<?>)(Object)this;
        if (screen.getMenu() instanceof AbstractMaidContainer menu
                && !PlayerPermissions.canMoveItems(Minecraft.getInstance().player, menu.getMaid())) callback.cancel();
    }
}
