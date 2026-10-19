// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** All owners' eligible sleep and the current dimension's forecast; no identifiers or addresses. */
public record SleepStatus(int queued, int cancelled, int ready, int required, int reason, long nightEnd) {
    public static final int OFF = 0, WAITING = 1, CHECKING = 2, READY = 3, SCHEDULES = 4,
            ORDERS = 5, BEDS = 6, PATHS = 7, BUSY = 8, UNLOADED = 9, DAYLIGHT = 10, DIMENSION = 11, PLAYER = 12, INACTIVE = 13, PERCENTAGE = 14;
    public static final StreamCodec<ByteBuf, SleepStatus> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SleepStatus::queued, ByteBufCodecs.VAR_INT, SleepStatus::cancelled,
            ByteBufCodecs.VAR_INT, SleepStatus::ready, ByteBufCodecs.VAR_INT, SleepStatus::required,
            ByteBufCodecs.VAR_INT, SleepStatus::reason, ByteBufCodecs.VAR_LONG, SleepStatus::nightEnd, SleepStatus::new);
    private static final String KEY = "message." + MaidTweaks.ID + ".sleep.";

    public Component queueMessage() {
        if (queued > 0) return Component.translatable(KEY + "queue", queued);
        if (cancelled > 0) return Component.translatable(KEY + "queue_cancelled", cancelled);
        return Component.translatable(KEY + "queue_none");
    }

    public Component forecastMessage(SleepSettings settings) {
        return switch (reason) {
            case READY -> settings.autoSleep() ? Component.translatable(KEY + "forecast.ready", settings.earliestSkipTime())
                    : Component.translatable(KEY + "forecast.assisted");
            case SCHEDULES -> Component.translatable(KEY + "forecast.schedules", ready, required);
            case ORDERS -> Component.translatable(KEY + "forecast.orders", ready, required);
            case BEDS -> Component.translatable(KEY + "forecast.beds", ready, required);
            case PATHS -> Component.translatable(KEY + "forecast.paths", ready, required);
            case BUSY -> Component.translatable(KEY + "forecast.busy");
            case UNLOADED -> Component.translatable(KEY + "forecast.unloaded");
            case DAYLIGHT -> Component.translatable(KEY + "forecast.daylight");
            case DIMENSION -> Component.translatable(KEY + "forecast.dimension");
            case OFF -> Component.translatable(KEY + "disabled");
            case WAITING -> Component.translatable(KEY + "forecast.waiting");
            case PLAYER -> Component.translatable(KEY + "forecast.player");
            case INACTIVE -> Component.translatable(KEY + "forecast.inactive");
            case PERCENTAGE -> Component.translatable(KEY + "forecast.percentage");
            default -> Component.translatable(KEY + "forecast.checking");
        };
    }

    /** Advance notice based on configured night-rest schedules and permission only. */
    public Component noticeMessage(SleepSettings settings, long dayTime) {
        if (reason != READY) return forecastMessage(settings);
        long remaining = settings.earliestSkipTick() - Math.floorMod(dayTime, 24000L);
        if (remaining <= 0) return Component.translatable(KEY + "notice.ready_now");
        long minutes = (remaining * 60 + 999) / 1000;
        return minutes >= 60
                ? Component.translatable(KEY + "notice.in_hour")
                : Component.translatable(KEY + "notice.in_minutes", minutes);
    }
}
