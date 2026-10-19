// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractMaidContainer.class, remap = false)
public abstract class MaidPermissionMenuMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void graydients$sharedView(Player player, CallbackInfoReturnable<Boolean> callback) {
        var menu = (AbstractMaidContainer)(Object)this;
        if (menu.getMaid() != null && !menu.getMaid().isOwnedBy(player))
            callback.setReturnValue(PlayerPermissions.canUseMenu(player, menu));
    }
}
