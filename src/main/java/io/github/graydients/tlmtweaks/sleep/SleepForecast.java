// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import io.github.graydients.tlmtweaks.schedule.ScheduleSettings;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

/** Schedule-only advance notices and bounded attempt-time diagnosis. Paths never authorize a skip. */
public final class SleepForecast {
    private SleepBedPlan plan;
    private final SleepProgress progress = new SleepProgress();
    private final List<EntityMaid> queued = new ArrayList<>();
    private final List<EntityMaid> manualQueued = new ArrayList<>();
    private SleepSettings plannedSettings;
    private SleepSettings noticeSettings;
    private ScheduleSettings noticeSchedule;
    private int noticeTick;
    private long plannedNight, planStarted;
    private int plannedRequired;
    private final Map<UUID, SleepStatus> sent = new HashMap<>();
    private long announcedNight = Long.MIN_VALUE, announcedAdvanceNight = Long.MIN_VALUE, noticeUntil;
    private int announcedReason = -1;
    private int announcedReady = -1, announcedRequired = -1;
    private int announcedPlayers = -1, announcedMaids = -1;
    private boolean announcedAttempt, announcedCanSkip;

    public void invalidate() { plan = null; queued.clear(); manualQueued.clear(); sent.clear(); progress.invalidate(); }

    public SleepProgress progress() { return progress; }

    public void tick(ServerLevel level, List<EntityMaid> loaded, SleepSettings settings) {
        tick(level, loaded, settings, 0, 0);
    }

    public void tick(ServerLevel level, List<EntityMaid> loaded, SleepSettings settings, int sleeping, int rested) {
        tick(level, loaded, settings, sleeping, rested, 0, 0);
    }

    public void tick(ServerLevel level, List<EntityMaid> loaded, SleepSettings settings, int sleeping, int rested,
            int manualSleeping, int manualRested) {
        if (!settings.equals(noticeSettings) || !MaidSchedules.settings().equals(noticeSchedule)) {
            noticeSchedule = MaidSchedules.settings();
            noticeSettings = settings; announcedNight = Long.MIN_VALUE; announcedReason = -1;
            noticeTick = settings.noticeTick();
            announcedReady = announcedRequired = -1;
        }
        long now = level.getGameTime(), nightEnd = MaidSleep.nightEnd(level.getDayTime());
        int time = (int) Math.floorMod(level.getDayTime(), 24000L);
        // Reuse storage, but read live permissions and timetables on every tick.
        queued.clear(); manualQueued.clear();
        int nightRest = 0, cancelled = 0;
        int checkedSchedules = 0, restingSchedules = 0;
        for (EntityMaid maid : loaded) {
            if (!maid.isTame()) continue;
            var order = MaidSleep.currentOrder(maid);
            boolean permitted = MaidSleep.permitted(order, settings);
            if (order != null && order.cancelled()) cancelled++;
            // Timetables are shared; evaluate each once per tick without a cross-tick cache.
            int schedule = 1 << maid.getSchedule().ordinal();
            if ((checkedSchedules & schedule) == 0) {
                checkedSchedules |= schedule;
                if (MaidSchedules.canRestTonight(maid.getSchedule(), time)) restingSchedules |= schedule;
            }
            if ((restingSchedules & schedule) != 0) {
                nightRest++;
                if (permitted) {
                    queued.add(maid);
                    if (MaidSleep.manual(order)) manualQueued.add(maid);
                }
            }
        }
        progress.update(level, settings, sleeping, rested, manualQueued.size(), manualSleeping, manualRested);
        int online = progress.online();
        boolean attempt = progress.attempting();
        int required = online == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE,
                attempt ? progress.required() : progress.aloneRequired());
        List<EntityMaid> candidates = progress.manualOnly() ? manualQueued : queued;
        sleeping = progress.maids();
        int reason;
        int ready = 0;
        if (!settings.enabled()) { reason = SleepStatus.OFF; plan = null; }
        else if (!settings.autoSleep() && !progress.hasManualOrders() && !settings.contributeToSleep()) { reason = SleepStatus.INACTIVE; plan = null; }
        else if (!MaidSleep.compatible(level)) reason = SleepStatus.DIMENSION;
        else if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) reason = SleepStatus.DAYLIGHT;
        else if (!settings.autoSleep() && !progress.hasManualOrders() && progress.normalSleepDisabled()) { reason = SleepStatus.PERCENTAGE; plan = null; }
        else if (!settings.autoSleep() && !progress.hasManualOrders() && progress.players() == 0) { reason = SleepStatus.PLAYER; ready = sleeping; plan = null; }
        else if (attempt && (progress.ready(level) || progress.playerWillSkip(level))) { reason = SleepStatus.READY; ready = sleeping; plan = null; }
        else if (attempt && sleeping > 0 && sleeping >= required) { reason = SleepStatus.WAITING; ready = sleeping; plan = null; }
        else if (candidates.size() < required || required == 0) {
            reason = nightRest < required ? SleepStatus.SCHEDULES : SleepStatus.ORDERS;
            ready = reason == SleepStatus.SCHEDULES ? nightRest : candidates.size();
            plan = null;
        } else if (!attempt) {
            // Advance notices inspect only schedules and permission, including seated maids.
            reason = SleepStatus.READY; ready = candidates.size(); plan = null;
        }
        else {
            if (plan == null || plannedNight != nightEnd || plannedRequired != required || !plan.hasCandidates(candidates)
                    || !settings.equals(plannedSettings) || !plan.valid() || plan.complete() && now - planStarted >= 200) {
                plan = new SleepBedPlan(level, candidates);
                plannedNight = nightEnd; plannedSettings = settings;
                plannedRequired = required; planStarted = now;
            }
            plan.step(required);
            ready = plan.ready();
            if (ready >= required) reason = SleepStatus.READY;
            else if (!plan.complete()) reason = SleepStatus.CHECKING;
            else if (candidates.size() - plan.blockedMaids() < required) reason = SleepStatus.BUSY;
            else if (plan.hasUnloadedArea()) reason = SleepStatus.UNLOADED;
            else if (plan.availableBeds() < required) reason = SleepStatus.BEDS;
            else reason = SleepStatus.PATHS;
        }
        final int outcome = reason, checked = ready, needed = required;
        if (now % 20 == 0) {
            sent.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
            SleepStatus status = new SleepStatus(candidates.size(), cancelled, checked, needed, outcome, nightEnd);
            for (ServerPlayer player : level.players()) {
                if (!status.equals(sent.put(player.getUUID(), status)))
                    PacketDistributor.sendToPlayer(player, new SleepProtocol.Status(status));
            }
        }
        if (!attempt) {
            // One broadcast at the one-hour boundary. Schedule refreshes never repeat the advance notice.
            if (settings.enabled() && online > 0 && (settings.autoSleep() || progress.hasManualOrders())
                    && time == noticeTick && announcedAdvanceNight != nightEnd) {
                announcedAdvanceNight = nightEnd;
                var diagnosis = new SleepStatus(0, 0, ready, required, reason, nightEnd);
                var message = diagnosis.noticeMessage(settings, level.getDayTime()).copy()
                        .withStyle(reason == SleepStatus.READY ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
                for (ServerPlayer player : level.players()) player.displayClientMessage(message, true);
            }
            return;
        }
        if (!settings.enabled() || online == 0 || progress.playerOnlyEnough()) return;
        boolean canSkip = progress.ready(level) || progress.playerWillSkip(level) || progress.maidSleepingThrough(level);
        // A background route refresh must not repeatedly replace an unchanged failure with Checking.
        if (announcedNight == nightEnd && announcedAttempt == attempt && reason == SleepStatus.CHECKING
                && announcedReason != SleepStatus.CHECKING && announcedPlayers == progress.players()
                && announcedMaids == sleeping && announcedCanSkip == canSkip) return;
        // Vanilla-style actionbar notice for ten seconds, refreshed if tonight's forecast changes.
        boolean noticeChanged = announcedNight != nightEnd || announcedReason != reason || announcedReady != ready || announcedRequired != required
                || announcedAttempt != attempt || announcedPlayers != progress.players() || announcedMaids != sleeping
                || announcedCanSkip != canSkip;
        if (noticeChanged) {
            announcedNight = nightEnd; announcedReason = reason;
            announcedReady = ready; announcedRequired = required; noticeUntil = now + 200;
            announcedAttempt = attempt; announcedPlayers = progress.players(); announcedMaids = sleeping; announcedCanSkip = canSkip;
        }
        if (now <= noticeUntil && (noticeChanged || now % 40 == 0)) {
            var diagnosis = new SleepStatus(0, 0, ready, required, reason, nightEnd);
            var message = progress.message(diagnosis, canSkip);
            for (ServerPlayer player : level.players()) player.displayClientMessage(message, true);
        }
    }
}
