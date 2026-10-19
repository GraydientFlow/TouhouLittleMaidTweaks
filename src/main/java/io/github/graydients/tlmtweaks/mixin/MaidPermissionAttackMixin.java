// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetAttackListPackage;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SetAttackListPackage.class, remap = false)
public abstract class MaidPermissionAttackMixin {
    @Redirect(method = "writeList", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isOwnedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private static boolean graydients$mayEditTargets(EntityMaid maid, LivingEntity actor) {
        return actor instanceof Player player && PlayerPermissions.canChangeSettings(player, maid);
    }
}
