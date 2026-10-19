// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.pickup;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PickupSettings(int delaySeconds, double immediateRange) {
    public static final PickupSettings DEFAULTS = new PickupSettings(15, -1);
    public static final StreamCodec<ByteBuf, PickupSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PickupSettings::delaySeconds,
            ByteBufCodecs.DOUBLE, PickupSettings::immediateRange, PickupSettings::new);

    public boolean valid() {
        return delaySeconds >= 0 && delaySeconds <= 3600 && Double.isFinite(immediateRange)
                && immediateRange >= -1 && immediateRange <= 16;
    }

    public long delayTicks() { return delaySeconds * 20L; }
}
