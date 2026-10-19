// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.permissions;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Server policy for interacting with another player's maid. */
public record PermissionSettings(boolean carry, boolean view, boolean moveItems, boolean changeSettings) {
    public static final PermissionSettings DEFAULTS = new PermissionSettings(false, true, false, false);
    public static final StreamCodec<ByteBuf, PermissionSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PermissionSettings::carry,
            ByteBufCodecs.BOOL, PermissionSettings::view,
            ByteBufCodecs.BOOL, PermissionSettings::moveItems,
            ByteBufCodecs.BOOL, PermissionSettings::changeSettings, PermissionSettings::new);
}
