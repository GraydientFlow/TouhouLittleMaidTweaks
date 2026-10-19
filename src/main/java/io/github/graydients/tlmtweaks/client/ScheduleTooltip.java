// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import io.github.graydients.tlmtweaks.schedule.ScheduleSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.schedule.Activity;

/** Shared data-to-text conversion; no screen or renderer state. */
public final class ScheduleTooltip {
    private static final String KEY=MaidTweaks.ID+".schedule_tooltip.";
    private ScheduleTooltip() {}
    public static List<Component> lines(MaidSchedule mode,long dayTime) {
        int tick=(int)Math.floorMod(dayTime,24000L);
        var result=new ArrayList<Component>();
        result.add(Component.translatable(KEY+"header",Component.translatable(KEY+mode.name().toLowerCase(Locale.ROOT)),
                ScheduleSettings.clock((tick*60/1000+360)%1440)).withStyle(ChatFormatting.UNDERLINE));
        if (mode==MaidSchedule.ALL) result.add(Component.translatable(KEY+"all_day").withStyle(ChatFormatting.GREEN));
        else {
            var current=MaidSchedules.activity(mode,tick);
            for (var period:MaidSchedules.periods(mode)) {
                String activity=period.activity()==Activity.WORK?"work":period.activity()==Activity.REST?"sleep":"idle";
                result.add(Component.translatable(KEY+"period",ScheduleSettings.clock(period.startMinute()),ScheduleSettings.clock(period.endMinute()),
                        Component.translatable(KEY+activity)).withStyle(current==period.activity()?ChatFormatting.GREEN:ChatFormatting.DARK_GRAY));
            }
        }
        result.add(Component.translatable("tooltips.touhou_little_maid.schedule.desc"));
        return result;
    }
}
