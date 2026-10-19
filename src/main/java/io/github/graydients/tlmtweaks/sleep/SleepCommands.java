// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class SleepCommands {
    private static final String KEY = "message." + MaidTweaks.ID + ".sleep.";
    private SleepCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maidsleep")
                .executes(context -> tonight(context.getSource(), false))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> tonight(context.getSource(), !BoolArgumentType.getBool(context, "enabled"))))
                .then(Commands.literal("cancel").executes(context -> tonight(context.getSource(), true)))
                .then(Commands.literal("status").executes(context -> status(context.getSource()))));
        event.getDispatcher().register(Commands.literal("automaidsleep")
                .requires(source -> !SleepConfig.settings().autoSleepOperatorOnly() || source.hasPermission(2)
                        || source.getEntity() instanceof ServerPlayer player && SleepProtocol.canEdit(player))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> automatic(context.getSource(), BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int automatic(CommandSourceStack source, boolean enabled) {
        SleepSettings settings = SleepConfig.settings();
        if (settings.autoSleep() != enabled && !SleepConfig.update(source.getServer(),
                new SleepSettings(settings.enabled(), settings.maidWeight(), settings.earliestSkipTime(), enabled, settings.autoSleepOperatorOnly(), settings.contributeToSleep()))) {
            source.sendFailure(Component.translatable(KEY + "save_failed"));
            return 0;
        }
        if (enabled) MaidSleep.resumeAutomatic(source.getServer());
        announce(source, Component.translatable(KEY + "automatic", state(enabled), state(settings.enabled())));
        return 1;
    }

    private static int status(CommandSourceStack source) {
        SleepSettings settings = SleepConfig.settings();
        int permitted = 0, cancelled = 0;
        int time = (int) Math.floorMod(source.getLevel().getDayTime(), 24000L);
        for (EntityMaid maid : MaidSleep.loaded(source.getLevel())) {
            if (!maid.isAlive() || !maid.isTame()) continue;
            MaidSleep.Order order = MaidSleep.currentOrder(maid);
            if (order != null && order.cancelled()) cancelled++;
            if (MaidSleep.permitted(order, settings) && MaidSchedules.canRestTonight(maid.getSchedule(), time)) permitted++;
        }
        int allowed = permitted, excluded = cancelled;
        int online = source.getServer().getPlayerList().getPlayerCount();
        long required = online == 0 ? 0 : settings.maidsNeeded(online);
        source.sendSuccess(() -> Component.translatable(KEY + "status", state(settings.enabled()), state(settings.autoSleep()),
                state(settings.contributeToSleep()), allowed, excluded, required, settings.earliestSkipTime()), false);
        return 1;
    }

    private static void announce(CommandSourceStack source, Component result) {
        Component notice = Component.translatable(KEY + "announcement", source.getDisplayName(), result);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) player.sendSystemMessage(notice);
        if (!(source.getEntity() instanceof ServerPlayer)) source.sendSuccess(() -> notice, false);
    }

    private static Component state(boolean enabled) { return Component.translatable(enabled ? "options.on" : "options.off"); }

    private static int tonight(CommandSourceStack source, boolean cancel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.getPlayerOrException();
        SleepSettings settings = SleepConfig.settings();
        if (!cancel && !settings.enabled()) {
            source.sendFailure(Component.translatable(KEY + "disabled"));
            return 0;
        }
        if (!cancel && !MaidSleep.compatible(source.getLevel())) {
            source.sendFailure(Component.translatable(KEY + "forecast.dimension"));
            return 0;
        }
        var result = MaidSleep.orderTonight(source.getLevel(), cancel);
        int scheduled = 0, affected = 0;
        int time = (int) Math.floorMod(source.getLevel().getDayTime(), 24000L);
        for (EntityMaid maid : MaidSleep.loaded(source.getLevel())) {
            if (!maid.isAlive() || !maid.isTame()) continue;
            affected++;
            if (MaidSchedules.canRestTonight(maid.getSchedule(), time)) scheduled++;
        }
        if (cancel) announce(source, Component.translatable(KEY + "cancelled"));
        else {
            var notice = Component.translatable(KEY + (result == MaidSleep.OrderResult.ALREADY_QUEUED ? "already_queued" : "ordered"), scheduled);
            var progress = new SleepProgress();
            progress.update(source.getLevel(), settings, 0, 0, scheduled, 0, 0);
            if (scheduled < progress.required()) notice.append(Component.translatable(KEY + "shortfall"));
            announce(source, notice);
        }
        return Math.max(1, cancel ? affected : scheduled);
    }
}
