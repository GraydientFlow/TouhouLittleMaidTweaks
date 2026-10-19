// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** World-wide work switches and defaults; explicit maid/pen policies remain separate. */
public record WorkSettings(boolean animalJobs, boolean seatRecovery, boolean seatLock, boolean stations,
                           boolean milk, boolean replaceTools, boolean butcherWithoutFood, AnimalSettings animalDefaults) {
    public static final WorkSettings DEFAULTS = new WorkSettings(true, true, true, true, true, true, false,
            new AnimalSettings(16, 1.5, false, true));
    public static final StreamCodec<ByteBuf, WorkSettings> CODEC = StreamCodec.of((buf, value) -> {
        buf.writeBoolean(value.animalJobs); buf.writeBoolean(value.seatRecovery); buf.writeBoolean(value.seatLock);
        buf.writeBoolean(value.stations); buf.writeBoolean(value.milk); buf.writeBoolean(value.replaceTools); buf.writeBoolean(value.butcherWithoutFood);
        var defaults = value.animalDefaults;
        buf.writeInt(defaults.searchWidth()); buf.writeDouble(defaults.density());
        buf.writeBoolean(defaults.limitBreeding()); buf.writeBoolean(defaults.protectPair()); buf.writeInt(defaults.method()); buf.writeInt(defaults.keepCount());
    }, buf -> new WorkSettings(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), new AnimalSettings(buf.readInt(), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt())));

    public boolean valid() { return animalDefaults != null && animalDefaults.valid(); }
}
