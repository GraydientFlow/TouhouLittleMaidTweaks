// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Carry On hook; actual ownership and all other pickup restrictions stay native. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public abstract class MaidPermissionCarryMixin {
    @Inject(method = "tryPickupEntity", at = @At("HEAD"), cancellable = true)
    private static void graydients$guardCarry(ServerPlayer player, Entity target, Function<Entity, Boolean> condition,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof EntityMaid maid && maid.isTame() && !PlayerPermissions.canCarry(player, maid))
            callback.setReturnValue(false);
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/TamableAnimal;getOwnerUUID()Ljava/util/UUID;"))
    private static UUID graydients$carryPermission(TamableAnimal target, ServerPlayer player, Entity entity,
                                                   Function<Entity, Boolean> condition) {
        if (target instanceof EntityMaid maid && !maid.isOwnedBy(player) && PlayerPermissions.canCarry(player, maid))
            return player.getUUID();
        return target.getOwnerUUID();
    }
}
