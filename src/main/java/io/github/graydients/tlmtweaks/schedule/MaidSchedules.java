// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.schedule;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

/** Immutable shared tables replaced on configuration changes; no per-maid or per-tick rebuild. */
public final class MaidSchedules {
    public record Period(int startMinute, int endMinute, Activity activity) {}
    private record Shift(Schedule chart, List<Period> periods, int[] ticks, Activity[] activities, int sleepTick, int firstNightRest) {
        Activity activity(int time) {
            for (int i=ticks.length-1;i>=0;i--) if (time>=ticks[i]) return activities[i];
            return activities[activities.length-1];
        }
    }
    private record Tables(ScheduleSettings settings, Shift day, Shift night, Schedule all) {}
    private static volatile Tables cached;
    private MaidSchedules() {}

    public static boolean configure(ScheduleSettings settings) {
        if (!settings.valid()) throw new IllegalArgumentException("Invalid maid schedule");
        if (cached!=null && cached.settings.equals(settings)) return false;
        boolean defaults=settings.equals(ScheduleSettings.DEFAULTS);
        cached = new Tables(settings,build(settings.dayWork(),settings.dayIdle(),settings.daySleep(),
                    defaults ? InitEntities.MAID_DAY_SHIFT_SCHEDULES.get() : null),
                build(settings.nightWork(),settings.nightIdle(),settings.nightSleep(),
                    defaults ? InitEntities.MAID_NIGHT_SHIFT_SCHEDULES.get() : null),
                InitEntities.MAID_ALL_DAY_SCHEDULES.get());
        return true;
    }
    public static void reset() { cached=null; }
    private static Tables tables() {
        if (cached==null) configure(ScheduleConfig.settings());
        return cached;
    }
    public static ScheduleSettings settings() { var value=cached; return value==null ? ScheduleSettings.DEFAULTS : value.settings; }
    private static Shift shift(Tables tables, MaidSchedule mode) { return mode==MaidSchedule.NIGHT ? tables.night : tables.day; }
    public static Schedule chart(MaidSchedule mode) {
        var tables=tables(); return mode==MaidSchedule.ALL ? tables.all : shift(tables,mode).chart;
    }
    /** Same three transitions as the native chart, without its stream allocation on each AI tick. */
    public static Activity activity(MaidSchedule mode, int time) {
        return mode==MaidSchedule.ALL ? Activity.WORK : shift(tables(),mode).activity(Math.floorMod(time,24000));
    }
    public static List<Period> periods(MaidSchedule mode) { return mode==MaidSchedule.ALL ? List.of() : shift(tables(),mode).periods; }
    public static boolean hasNightRest(MaidSchedule mode) { return mode!=MaidSchedule.ALL && shift(tables(),mode).firstNightRest<24000; }
    public static boolean canRestTonight(MaidSchedule mode, int time) {
        if (mode==MaidSchedule.ALL) return false;
        var shift=shift(tables(),mode);
        return shift.firstNightRest<24000 && (time<12000 || shift.activity(time)==Activity.REST || shift.sleepTick>time);
    }
    public static int firstNightRest() {
        var tables=tables(); int first=Math.min(tables.day.firstNightRest,tables.night.firstNightRest);
        return first==24000 ? 16000 : first;
    }
    private static Shift build(int work, int idle, int sleep, Schedule nativeChart) {
        var starts=new ArrayList<>(List.of(new Period(work,0,Activity.WORK),new Period(idle,0,Activity.IDLE),new Period(sleep,0,Activity.REST)));
        starts.sort(Comparator.comparingInt(period -> ScheduleSettings.tick(period.startMinute)));
        var builder=new ScheduleBuilder(new Schedule());
        builder.changeActivityAt(0,ScheduleSettings.tick(starts.getFirst().startMinute)==0 ? starts.getFirst().activity : starts.getLast().activity);
        var periods=new ArrayList<Period>();
        int[] ticks=new int[starts.size()]; Activity[] activities=new Activity[starts.size()];
        for (int i=0;i<starts.size();i++) {
            var start=starts.get(i);
            int tick=ScheduleSettings.tick(start.startMinute);
            ticks[i]=tick; activities[i]=start.activity;
            if (tick!=0) builder.changeActivityAt(tick,start.activity);
            periods.add(new Period(start.startMinute,starts.get((i+1)%starts.size()).startMinute,start.activity));
        }
        var chart=nativeChart==null ? builder.build() : nativeChart; int sleepTick=ScheduleSettings.tick(sleep);
        int first=chart.getActivityAt(12000)==Activity.REST ? 12000 : sleepTick>=12000 ? sleepTick : 24000;
        return new Shift(chart,List.copyOf(periods),ticks,activities,sleepTick,first);
    }
}
