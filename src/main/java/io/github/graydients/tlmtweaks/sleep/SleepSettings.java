// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import io.netty.buffer.ByteBuf;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SleepSettings(boolean enabled, double maidWeight, String earliestSkipTime, boolean autoSleep, boolean autoSleepOperatorOnly, boolean contributeToSleep) {
    private static final Pattern NIGHT_CLOCK = Pattern.compile("(?:1[89]|2[0-3]|0[0-5]):[0-5][0-9]");
    public static final int BEDTIME_TICK = 16500; // Default earliest maid-only skip: 22:30, allowing time after bedtime to reach beds.
    public static final String BEDTIME = "22:30";
    public static final SleepSettings DEFAULTS = new SleepSettings(false, 0.5, BEDTIME, false, true, true);
    public SleepSettings(boolean enabled, double maidWeight, String earliestSkipTime) {
        this(enabled, maidWeight, earliestSkipTime, false, true, true);
    }
    public SleepSettings(boolean enabled, double maidWeight, String earliestSkipTime, boolean autoSleep) {
        this(enabled, maidWeight, earliestSkipTime, autoSleep, true, true);
    }
    public SleepSettings(boolean enabled, double maidWeight, String earliestSkipTime, boolean autoSleep, boolean autoSleepOperatorOnly) {
        this(enabled, maidWeight, earliestSkipTime, autoSleep, autoSleepOperatorOnly, true);
    }
    public static final StreamCodec<ByteBuf, SleepSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SleepSettings::enabled,
            ByteBufCodecs.DOUBLE, SleepSettings::maidWeight,
            ByteBufCodecs.stringUtf8(5), SleepSettings::earliestSkipTime,
            ByteBufCodecs.BOOL, SleepSettings::autoSleep,
            ByteBufCodecs.BOOL, SleepSettings::autoSleepOperatorOnly,
            ByteBufCodecs.BOOL, SleepSettings::contributeToSleep, SleepSettings::new);

    public boolean valid() {
        return Double.isFinite(maidWeight) && maidWeight >= 0.01 && maidWeight <= 64
                && validClock(earliestSkipTime);
    }

    public long maidsNeeded(int onlinePlayers) {
        if (onlinePlayers <= 0 || !valid()) return Long.MAX_VALUE;
        // Decimal configuration values must not require an extra maid through rounding error.
        return BigDecimal.valueOf(onlinePlayers).divide(BigDecimal.valueOf(maidWeight), 0, RoundingMode.CEILING).longValueExact();
    }

    public static boolean validClock(String value) { return value != null && NIGHT_CLOCK.matcher(value).matches(); }

    public int earliestSkipTick() {
        int hour = (earliestSkipTime.charAt(0) - '0') * 10 + earliestSkipTime.charAt(1) - '0';
        int minute = (earliestSkipTime.charAt(3) - '0') * 10 + earliestSkipTime.charAt(4) - '0';
        // Minecraft day tick zero is 06:00. Round up so a skip never precedes the selected minute.
        return Math.floorMod(hour - 6, 24) * 1000 + (minute * 1000 + 59) / 60;
    }

    public boolean clockAllowsSkip(long dayTime) { return Math.floorMod(dayTime, 24000L) >= earliestSkipTick(); }
    public int noticeTick() { return earliestSkipTick() - 1000; }
    public String noticeTime() {
        int hour = Integer.parseInt(earliestSkipTime.substring(0, 2));
        return String.format(java.util.Locale.ROOT, "%02d%s", Math.floorMod(hour - 1, 24), earliestSkipTime.substring(2));
    }
    public long minimumSleepTicks() { return 100L; }
}
