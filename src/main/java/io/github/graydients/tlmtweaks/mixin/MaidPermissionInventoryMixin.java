// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guard before native menu code and client predictions, after dispatch to the server thread. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MaidPermissionInventoryMixin {
    @Shadow public ServerPlayer player;

    @Unique private boolean graydients$deny(int containerId, int button) {
        if (!(player.containerMenu instanceof AbstractMaidContainer menu) || menu.containerId != containerId
                || menu.getMaid() == null || menu.getMaid().isOwnedBy(player)) return false;
        boolean allowed = PlayerPermissions.canUseMenu(player, menu)
                && (PlayerPermissions.inventoryPageButton(menu, button)
                    || (button >= 0 && !(menu instanceof MaidMainContainer)
                        ? PlayerPermissions.canChangeSettings(player, menu.getMaid())
                        : PlayerPermissions.canMoveItems(player, menu.getMaid())));
        if (allowed) return false;
        menu.sendAllDataToRemote();
        return true;
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void graydients$guardClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (graydients$deny(packet.getContainerId(), -1)) callback.cancel();
    }

    @Inject(method = "handlePlaceRecipe", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void graydients$guardRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo callback) {
        if (graydients$deny(packet.getContainerId(), -1)) callback.cancel();
    }

    @Inject(method = "handleContainerButtonClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void graydients$guardButton(ServerboundContainerButtonClickPacket packet, CallbackInfo callback) {
        if (graydients$deny(packet.containerId(), packet.buttonId())) callback.cancel();
    }
}
