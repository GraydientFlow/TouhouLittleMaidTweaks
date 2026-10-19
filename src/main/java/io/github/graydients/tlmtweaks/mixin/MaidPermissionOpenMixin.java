// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiPackage;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenMaidGuiPackage.class, remap = false)
public abstract class MaidPermissionOpenMixin {
    @Inject(method = "handle(Lcom/github/tartaricacid/touhoulittlemaid/network/message/OpenMaidGuiPackage;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void graydients$openSharedTab(OpenMaidGuiPackage packet, ServerPlayer player, CallbackInfo callback) {
        if (player == null || !(player.level().getEntity(packet.entityId()) instanceof EntityMaid maid)
                || maid.isOwnedBy(player)) return;
        if (PlayerPermissions.canOpenTab(player, maid, packet.tabId())) maid.openMaidGui(player, packet.tabId());
        callback.cancel();
    }
}
