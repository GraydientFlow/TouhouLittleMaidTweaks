// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared one-night counting permission, independent of ownership and loaded entities. */
public final class SleepNight extends SavedData {
    private static final String ID = MaidTweaks.ID + "_sleep_night";
    private static final Factory<SleepNight> FACTORY = new Factory<>(SleepNight::new, SleepNight::load, null);
    private MaidSleep.Order order;

    public static SleepNight load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SleepNight();
        if (tag.contains("until_day_time")) data.order = new MaidSleep.Order(tag.getLong("until_day_time"),
                tag.getLong("issued_game_time"), tag.getBoolean("cancelled"), tag.getBoolean("automatic"));
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (order != null) {
            tag.putLong("until_day_time", order.untilDayTime());
            tag.putLong("issued_game_time", order.issuedGameTime());
            tag.putBoolean("cancelled", order.cancelled());
            tag.putBoolean("automatic", order.automatic());
        }
        return tag;
    }

    public MaidSleep.Order current(long dayTime) {
        long remaining = order == null ? 0 : order.untilDayTime() - dayTime;
        if (order != null && (remaining <= 0 || remaining > 24000)) { order = null; setDirty(); }
        return order;
    }

    public static MaidSleep.Order current(ServerLevel level) {
        var data = level.getDataStorage().get(FACTORY, ID);
        return data == null ? null : data.current(level.getDayTime());
    }

    public static void set(ServerLevel level, MaidSleep.Order order) {
        var data = level.getDataStorage().computeIfAbsent(FACTORY, ID);
        data.order = order; data.setDirty();
        MaidSleep.invalidate(level);
    }
}
