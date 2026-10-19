// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.schedule;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sleep.MaidSleep;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server-owned clocks, saved independently of per-maid Day/Night/All-day selection. */
public final class ScheduleConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> DAY_WORK, DAY_IDLE, DAY_SLEEP, NIGHT_WORK, NIGHT_IDLE, NIGHT_SLEEP;
    static {
        var builder=new ModConfigSpec.Builder();
        builder.comment("24-hour HH:MM starts; each activity lasts until the next start. Three distinct times per shift.").push("day");
        DAY_WORK=clock(builder,"work","06:00"); DAY_IDLE=clock(builder,"idle","18:00"); DAY_SLEEP=clock(builder,"sleep","22:00");
        builder.pop().push("night");
        NIGHT_WORK=clock(builder,"work","18:00"); NIGHT_IDLE=clock(builder,"idle","14:00"); NIGHT_SLEEP=clock(builder,"sleep","06:00");
        builder.pop(); SPEC=builder.build();
    }
    private ScheduleConfig() {}
    private static ModConfigSpec.ConfigValue<String> clock(ModConfigSpec.Builder builder,String key,String value) {
        return builder.define(key,value,raw -> raw instanceof String text && ScheduleSettings.validClock(text));
    }
    public static ScheduleSettings settings() {
        var value=readValues(); return value.valid() ? value : MaidSchedules.settings();
    }
    private static ScheduleSettings readValues() {
        if (!SPEC.isLoaded()) return ScheduleSettings.DEFAULTS;
        return new ScheduleSettings(ScheduleSettings.minute(DAY_WORK.get()),ScheduleSettings.minute(DAY_IDLE.get()),ScheduleSettings.minute(DAY_SLEEP.get()),
                ScheduleSettings.minute(NIGHT_WORK.get()),ScheduleSettings.minute(NIGHT_IDLE.get()),ScheduleSettings.minute(NIGHT_SLEEP.get()));
    }
    private static void setValues(ScheduleSettings value) {
        DAY_WORK.set(ScheduleSettings.clock(value.dayWork())); DAY_IDLE.set(ScheduleSettings.clock(value.dayIdle())); DAY_SLEEP.set(ScheduleSettings.clock(value.daySleep()));
        NIGHT_WORK.set(ScheduleSettings.clock(value.nightWork())); NIGHT_IDLE.set(ScheduleSettings.clock(value.nightIdle())); NIGHT_SLEEP.set(ScheduleSettings.clock(value.nightSleep()));
    }
    public static void acceptRemote(ScheduleSettings value) {
        if (!value.valid()) return;
        if (SPEC.isLoaded()) setValues(value);
        MaidSchedules.configure(value);
    }
    public static boolean update(MinecraftServer server,ScheduleSettings value) {
        if (!SPEC.isLoaded() || !value.valid()) return false;
        var previous=MaidSchedules.settings();
        try { setValues(value); SPEC.save(); }
        catch (RuntimeException failure) {
            setValues(previous); MaidTweaks.LOG.error("Could not save maid schedules",failure); return false;
        }
        apply(server,value); return true;
    }
    public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec()!=SPEC) return;
        if (event instanceof ModConfigEvent.Unloading) { MaidSchedules.reset(); return; }
        var server=ServerLifecycleHooks.getCurrentServer();
        Runnable update=() -> {
            var value=readValues();
            if (!value.valid()) {
                MaidTweaks.LOG.warn("Maid schedule starts must be distinct; retaining the previous schedule");
                setValues(MaidSchedules.settings()); return;
            }
            if (server==null) MaidSchedules.configure(value); else apply(server,value);
        };
        if (server==null) update.run(); else server.execute(update);
    }
    private static void apply(MinecraftServer server,ScheduleSettings value) {
        if (!MaidSchedules.configure(value)) return;
        for (var level:server.getAllLevels()) for (var entity:level.getAllEntities())
            if (entity instanceof EntityMaid maid) maid.refreshBrain(level);
        MaidSleep.reconfigure(server);
        ScheduleProtocol.broadcast(server);
    }
}
