// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.ToggleTabPackage;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ToggleTabPackage.class, remap = false)
public abstract class MaidPermissionTabMixin {
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true)
    private static void graydients$switchSharedTab(IPayloadContext context, ToggleTabPackage packet, CallbackInfo callback) {
        var player = context.player();
        if (!(player.level().getEntity(packet.entityId()) instanceof EntityMaid maid) || maid.isOwnedBy(player)) return;
        if (PlayerPermissions.canOpenTab(player, maid, packet.tabId())) maid.openMaidGui(player, packet.tabId());
        callback.cancel();
    }
}
