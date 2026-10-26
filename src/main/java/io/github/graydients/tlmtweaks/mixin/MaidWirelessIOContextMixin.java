// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.


package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.storage.WirelessTransfers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidWirelessIOContextMixin implements WirelessTransfers.Context {
    @Unique private int graydients$wirelessTick = Integer.MIN_VALUE;
    @Override public boolean graydients$beginWirelessTick(int tick) {
        if (graydients$wirelessTick == tick) return false;
        graydients$wirelessTick = tick;
        return true;
    }
}
