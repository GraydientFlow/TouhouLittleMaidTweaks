// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.schedule;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.network.codec.StreamCodec;

/** Clock minutes; every activity runs until the next configured transition in that shift. */
public record ScheduleSettings(int dayWork, int dayIdle, int daySleep, int nightWork, int nightIdle, int nightSleep) {
    private static final Pattern CLOCK = Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");
    public static final ScheduleSettings DEFAULTS = new ScheduleSettings(360,1080,1320,1080,840,360);
    public static final StreamCodec<ByteBuf,ScheduleSettings> CODEC = StreamCodec.of((buf,value) -> {
        buf.writeInt(value.dayWork); buf.writeInt(value.dayIdle); buf.writeInt(value.daySleep);
        buf.writeInt(value.nightWork); buf.writeInt(value.nightIdle); buf.writeInt(value.nightSleep);
    }, buf -> new ScheduleSettings(buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt()));
    public boolean valid() { return validShift(dayWork,dayIdle,daySleep) && validShift(nightWork,nightIdle,nightSleep); }
    private static boolean validShift(int work, int idle, int sleep) {
        return work>=0 && work<1440 && idle>=0 && idle<1440 && sleep>=0 && sleep<1440
                && work!=idle && work!=sleep && idle!=sleep;
    }
    public static boolean validClock(String clock) { return clock!=null && CLOCK.matcher(clock).matches(); }
    public static int minute(String clock) {
        return validClock(clock) ? Integer.parseInt(clock.substring(0,2))*60+Integer.parseInt(clock.substring(3)) : -1;
    }
    public static String clock(int minute) { return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60); }
    public static int tick(int minute) {
        return (Math.floorMod(minute-360,1440)*1000+59)/60;
    }
}
