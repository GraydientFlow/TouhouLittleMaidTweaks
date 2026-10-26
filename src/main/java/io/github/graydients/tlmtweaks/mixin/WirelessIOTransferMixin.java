// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.WirelessIOBauble;
import io.github.graydients.tlmtweaks.storage.WirelessTransfers;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WirelessIOBauble.class, remap = false)
public abstract class WirelessIOTransferMixin {
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void groupedTransfers(EntityMaid maid, ItemStack charm, CallbackInfo callback) {
        WirelessTransfers.tick(maid);
        callback.cancel();
    }
}
