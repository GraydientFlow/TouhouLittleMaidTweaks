// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import io.github.graydients.tlmtweaks.schedule.ScheduleConfig;
import io.github.graydients.tlmtweaks.schedule.ScheduleProtocol;
import io.github.graydients.tlmtweaks.schedule.ScheduleSettings;
import java.util.ArrayList;
import java.util.Optional;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** One flat set of six server-owned schedule transitions. */
public final class ClothScheduleConfig {
    private static final String KEY=MaidTweaks.ID+".schedule_config.";
    private ClothScheduleConfig() {}
    private static Component text(String key) { return Component.translatable(KEY+key); }
    private static Component scope(String key) { return Component.translatable(MaidTweaks.ID+".work_config."+key); }
    public static void receive(ScheduleProtocol.State packet) {
        if (Minecraft.getInstance().getSingleplayerServer()==null) ScheduleConfig.acceptRemote(packet.settings());
    }
    public static void add(AddClothConfigEvent event,SubCategoryBuilder category) {
        var minecraft=Minecraft.getInstance(); var entries=event.getEntryBuilder();
        if (minecraft.level==null || minecraft.player==null || !ScheduleConfig.SPEC.isLoaded()) {
            category.add(entries.startTextDescription(scope("no_world")).build()); return;
        }
        var connection=minecraft.getConnection(); var integrated=minecraft.getSingleplayerServer();
        boolean editable=minecraft.player.hasPermissions(2) || integrated!=null && integrated.isSingleplayerOwner(minecraft.player.getGameProfile());
        category.add(entries.startTextDescription(scope(integrated==null?"server_scope":"world_scope")).build());
        category.add(entries.startTextDescription(text("instruction")).build());
        if (!editable) category.add(entries.startTextDescription(scope("read_only")).build());
        var initial=MaidSchedules.settings(); var defaults=ScheduleSettings.DEFAULTS;
        int[] current={initial.dayWork(),initial.dayIdle(),initial.daySleep(),initial.nightWork(),initial.nightIdle(),initial.nightSleep()};
        int[] standard={defaults.dayWork(),defaults.dayIdle(),defaults.daySleep(),defaults.nightWork(),defaults.nightIdle(),defaults.nightSleep()};
        String[] names={"day_work","day_idle","day_sleep","night_work","night_idle","night_sleep"};
        var fields=new ArrayList<StringListEntry>();
        for (int i=0;i<names.length;i++) {
            final int index=i;
            var entry=entries.startStrField(text(names[i]),ScheduleSettings.clock(current[i]))
                    .setDefaultValue(ScheduleSettings.clock(standard[i])).setTooltip(text("time.tip"))
                    .setErrorSupplier(value -> {
                        if (!ScheduleSettings.validClock(value)) return Optional.of(text("invalid_time"));
                        for (int other=index/3*3;other<index/3*3+3 && other<fields.size();other++)
                            if (other!=index && value.equals(fields.get(other).getValue())) return Optional.of(text("duplicate_time"));
                        return Optional.empty();
                    }).build();
            entry.setEditable(editable); fields.add(entry); category.add(entry);
        }
        Runnable previousSave=event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave!=null) previousSave.run();
            if (!editable || minecraft.getConnection()!=connection) return;
            var selected=new ScheduleSettings(ScheduleSettings.minute(fields.get(0).getValue()),ScheduleSettings.minute(fields.get(1).getValue()),
                    ScheduleSettings.minute(fields.get(2).getValue()),ScheduleSettings.minute(fields.get(3).getValue()),
                    ScheduleSettings.minute(fields.get(4).getValue()),ScheduleSettings.minute(fields.get(5).getValue()));
            if (selected.valid() && !selected.equals(initial)) PacketDistributor.sendToServer(new ScheduleProtocol.Set(selected));
        });
    }
}
