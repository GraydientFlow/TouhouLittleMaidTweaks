// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidSubConfigPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidTaskPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.MaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.SetMaidSoundIdPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.RefreshMaidBrainPackage;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only these edit packets gain the explicit non-owner setting permission. */
@Mixin(value = {MaidConfigPackage.class, MaidSubConfigPackage.class, MaidTaskPackage.class,
        MaidModelPackage.class, SetMaidSoundIdPackage.class, YsmMaidModelPackage.class, RefreshMaidBrainPackage.class}, remap = false)
public abstract class MaidPermissionEditMixin {
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isOwnedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private static boolean graydients$mayEdit(EntityMaid maid, LivingEntity actor) {
        return actor instanceof Player player && PlayerPermissions.canChangeSettings(player, maid);
    }
}
