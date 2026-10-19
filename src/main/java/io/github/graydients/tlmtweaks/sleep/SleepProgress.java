// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.math.BigDecimal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/** Live counts for independent player-assisted sleep and automatic night skipping. */
public final class SleepProgress {
    private static final String KEY = "message." + MaidTweaks.ID + ".sleep.";
    private SleepSettings settings;
    private int online, activePlayers, players, restedPlayers, maids, restedMaids, nativeNeeded, percentage;
    private int countedOnline = -1, countedPlayers = -1, countedRestedPlayers = -1, countedNative = -1;
    private SleepSettings countedSettings;
    private long autoRequired, autoRestedRequired, playerRequired, playerRestedRequired, aloneRequired;
    private boolean maidOnlyAttempt;
    private int manualOrders, manualSleeping, manualRested;
    private long maidDelaySince = Long.MIN_VALUE, maidDelayNight = Long.MIN_VALUE;

    public void update(ServerLevel level, SleepSettings rules, int sleepingMaids, int qualifiedMaids) {
        update(level, rules, sleepingMaids, qualifiedMaids, 0, 0, 0);
    }

    public void update(ServerLevel level, SleepSettings rules, int sleepingMaids, int qualifiedMaids,
            int explicitOrders, int explicitSleeping, int explicitRested) {
        maids = sleepingMaids; restedMaids = qualifiedMaids;
        manualOrders = explicitOrders; manualSleeping = explicitSleeping; manualRested = explicitRested;
        refreshPlayers(level, rules);
    }

    public void refreshPlayers(ServerLevel level, SleepSettings rules) {
        settings = rules;
        online = level.getServer().getPlayerList().getPlayerCount();
        players = restedPlayers = 0;
        int active = 0;
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level || player.isSpectator()) continue;
            active++;
            if (player.isSleeping()) players++;
            if (player.isSleepingLongEnough()) restedPlayers++;
        }
        activePlayers = active;
        percentage = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        nativeNeeded = Math.max(1, (int) Math.ceil(active * (float) percentage / 100.0F));
        maidOnlyAttempt = (rules.autoSleep() || hasManualOrders()) && rules.clockAllowsSkip(level.getDayTime());
        if (!rules.equals(countedSettings) || online != countedOnline || players != countedPlayers
                || restedPlayers != countedRestedPlayers || nativeNeeded != countedNative) {
            aloneRequired = needed(rules, online);
            autoRequired = needed(rules, online - (rules.contributeToSleep() ? players : 0));
            autoRestedRequired = needed(rules, online - (rules.contributeToSleep() ? restedPlayers : 0));
            playerRequired = needed(rules, nativeNeeded - players);
            playerRestedRequired = needed(rules, nativeNeeded - restedPlayers);
            countedSettings = rules; countedOnline = online; countedPlayers = players;
            countedRestedPlayers = restedPlayers; countedNative = nativeNeeded;
        }
        updateMaidDelay(level);
    }

    private void updateMaidDelay(ServerLevel level) {
        if (!maidSleepingThrough(level)) { maidDelaySince = Long.MIN_VALUE; return; }
        long night = MaidSleep.nightEnd(level.getDayTime()), now = level.getGameTime();
        if (maidDelaySince == Long.MIN_VALUE || maidDelayNight != night || now < maidDelaySince) {
            maidDelaySince = now; maidDelayNight = night;
        }
    }

    /** The vanilla-style skipping message starts while the cancellable maid-only delay runs. */
    public boolean maidSleepingThrough(ServerLevel level) {
        if (!settings.enabled() || online == 0 || !maidOnlyAttempt || !MaidSleep.night(level)
                || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return false;
        return settings.autoSleep() && maids > 0 && maids >= autoRequired
                || hasManualOrders() && manualSleeping > 0 && manualSleeping >= autoRequired;
    }

    private static long needed(SleepSettings settings, int remaining) { return remaining <= 0 ? 0 : settings.maidsNeeded(remaining); }
    public void invalidate() {
        maids = restedMaids = manualOrders = manualSleeping = manualRested = 0;
        maidDelaySince = maidDelayNight = Long.MIN_VALUE;
    }
    public boolean hasManualOrders() { return manualOrders > 0; }
    /** Shared manual permission includes every eligible maid; passive player credit remains independent. */
    public boolean manualOnly() {
        return hasManualOrders() && !settings.autoSleep()
                && (!settings.contributeToSleep() || players == 0 || normalSleepDisabled());
    }
    public int maids() { return manualOnly() ? manualSleeping : maids; }
    public int players() { return players; }
    public int online() { return online; }
    public long required() {
        return settings.contributeToSleep() && players > 0 && (!maidOnlyAttempt || playerRequired < autoRequired)
                ? playerRequired : autoRequired;
    }
    public long aloneRequired() { return aloneRequired; }
    public boolean attempting() { return maidOnlyAttempt || settings.contributeToSleep() && players > 0; }
    public boolean maidOnlyAttempt() { return maidOnlyAttempt; }
    public boolean normalSleepDisabled() { return percentage > 100; }
    public boolean playerOnlyEnough() { return maids == 0 && players >= nativeNeeded; }
    public boolean playerReady() { return restedPlayers >= nativeNeeded; }

    /** Like vanilla, announce skipping when counts meet the target, before deep sleep completes. */
    public boolean playerWillSkip(ServerLevel level) {
        boolean enough = players >= nativeNeeded || settings.enabled() && settings.contributeToSleep()
                && players > 0 && maids > 0 && maids >= playerRequired;
        return enough && !normalSleepDisabled() && MaidSleep.night(level)
                && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
    }

    public boolean ready(ServerLevel level) {
        if (!settings.enabled() || online == 0 || restedMaids == 0 || !MaidSleep.night(level)
                || !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return false;
        boolean automatic = settings.autoSleep() && maidOnlyAttempt && restedMaids >= autoRestedRequired;
        boolean manual = hasManualOrders() && maidOnlyAttempt && manualRested > 0 && manualRested >= autoRestedRequired;
        boolean assisted = settings.contributeToSleep() && percentage <= 100 && restedPlayers > 0
                && restedMaids >= playerRestedRequired;
        boolean delayComplete = maidDelaySince != Long.MIN_VALUE
                && level.getGameTime() - maidDelaySince >= settings.minimumSleepTicks();
        return (automatic || manual) && delayComplete || assisted;
    }

    public Component message(SleepStatus diagnosis, boolean ready) {
        if (ready) return Component.translatable(players >= nativeNeeded && !normalSleepDisabled()
                || settings.contributeToSleep() && players > 0
                ? "sleep.skipping_night" : KEY + "progress.skipping").withStyle(ChatFormatting.WHITE);
        if (settings.contributeToSleep() && players > 0) {
            int target = normalSleepDisabled() ? online : nativeNeeded;
            String credit = BigDecimal.valueOf(settings.maidWeight()).multiply(BigDecimal.valueOf(maids))
                    .add(BigDecimal.valueOf(players)).min(BigDecimal.valueOf(target))
                    .stripTrailingZeros().toPlainString();
            return Component.translatable(KEY + "progress.mixed", maids, players, activePlayers, credit, target).withStyle(ChatFormatting.WHITE);
        }
        long required = required();
        int sleeping = maids();
        Component count = Component.translatable(KEY + "progress.maids", sleeping, required).withStyle(ChatFormatting.WHITE);
        int reason = diagnosis == null ? SleepStatus.READY : diagnosis.reason();
        if (reason != SleepStatus.DAYLIGHT && reason != SleepStatus.DIMENSION && reason != SleepStatus.PERCENTAGE
                && (players >= nativeNeeded && !normalSleepDisabled() || sleeping > 0 && sleeping >= required))
            return count;
        Component failure = switch (reason) {
            case SleepStatus.SCHEDULES -> Component.translatable(KEY + "failure.schedules", diagnosis.ready(), diagnosis.required());
            case SleepStatus.ORDERS -> Component.translatable(KEY + "failure.orders", diagnosis.ready(), diagnosis.required());
            case SleepStatus.BEDS -> Component.translatable(KEY + "failure.beds");
            case SleepStatus.PATHS -> Component.translatable(KEY + "failure.paths");
            case SleepStatus.BUSY -> Component.translatable(KEY + "failure.busy");
            case SleepStatus.UNLOADED -> Component.translatable(KEY + "failure.unloaded");
            case SleepStatus.DAYLIGHT -> Component.translatable(KEY + "failure.daylight");
            case SleepStatus.DIMENSION -> Component.translatable(KEY + "failure.dimension");
            case SleepStatus.PERCENTAGE -> Component.translatable(KEY + "forecast.percentage");
            case SleepStatus.CHECKING -> Component.translatable(KEY + "failure.checking");
            default -> Component.translatable(KEY + "failure.sleeping");
        };
        return Component.translatable(KEY + "progress.failed", failure).withStyle(ChatFormatting.YELLOW);
    }
}
