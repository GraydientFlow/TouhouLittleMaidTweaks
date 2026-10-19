// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.schedule.MaidSchedules;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One-night permission for scheduled sleep to count. Never changes a maid's timetable or brain. */
@EventBusSubscriber(modid = MaidTweaks.ID)
public final class MaidSleep {
    public record Order(long untilDayTime, long issuedGameTime, boolean cancelled, boolean automatic) {
        public Order(long untilDayTime, long issuedGameTime) { this(untilDayTime, issuedGameTime, false, false); }
        private static final Codec<Order> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("until_day_time").forGetter(Order::untilDayTime),
                Codec.LONG.fieldOf("issued_game_time").forGetter(Order::issuedGameTime),
                Codec.BOOL.optionalFieldOf("cancelled", false).forGetter(Order::cancelled),
                Codec.BOOL.optionalFieldOf("automatic", false).forGetter(Order::automatic)
        ).apply(instance, Order::new));
    }

    public enum OrderResult { QUEUED, ALREADY_QUEUED, CANCELLED, INELIGIBLE }
    public static final TaskDataKey<Order> ORDER = new TaskDataRegister().register(
            ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "sleep_order"), Order.CODEC);
    private static final Order EXPIRED = new Order(0, 0);
    private static final Map<ServerLevel, Population> LEVELS = new HashMap<>();

    private static final class Sleeper {
        final EntityMaid maid;
        BlockPos bed;
        long sleepSince = Long.MIN_VALUE;
        long seenOrder = Long.MIN_VALUE;
        Sleeper(EntityMaid maid) { this.maid = maid; }
    }

    private static final class Population {
        final Map<UUID, Sleeper> maids = new HashMap<>();
        final List<EntityMaid> living = new ArrayList<>();
        final SleepForecast forecast = new SleepForecast();
        boolean ready;
        boolean dirty = true;
    }

    private MaidSleep() {}
    public static void initialize() {}

    public static boolean compatible(ServerLevel level) {
        return level.dimensionType().natural() && level.dimensionType().bedWorks();
    }

    public static boolean night(ServerLevel level) {
        return Math.floorMod(level.getDayTime(), 24000L) >= 12000L && compatible(level);
    }

    public static long nightEnd(long time) { return time - Math.floorMod(time, 24000L) + 24000L; }

    public static Order currentOrder(EntityMaid maid) {
        // TLM registers the brain before EntityMaid initializes task data. New maids are untamed here.
        if (!maid.isTame()) return null;
        if (maid.level() instanceof ServerLevel level) {
            Order shared = SleepNight.current(level);
            if (shared != null) return shared;
        }
        return legacyOrder(maid);
    }

    private static Order legacyOrder(EntityMaid maid) {
        Order order = maid.getData(ORDER);
        long remaining = order == null ? 0 : order.untilDayTime() - maid.level().getDayTime();
        return remaining > 0 && remaining <= 24000 ? order : null;
    }

    public static boolean normalNightRest(EntityMaid maid) {
        return MaidSchedules.hasNightRest(maid.getSchedule());
    }

    public static boolean permitted(EntityMaid maid) {
        return maid.isTame() && permitted(currentOrder(maid), SleepConfig.settings());
    }

    static boolean permitted(Order order, SleepSettings settings) {
        return settings.enabled() && (order == null || !order.cancelled())
                && (settings.contributeToSleep() || order != null && (!order.automatic() || settings.autoSleep()));
    }

    static boolean manual(Order order) { return order != null && !order.cancelled() && !order.automatic(); }

    public static boolean queued(EntityMaid maid) { return permitted(maid) && normalNightRest(maid); }

    public static boolean instructed(EntityMaid maid) {
        int time = (int) Math.floorMod(maid.level().getDayTime(), 24000L);
        return queued(maid) && maid.level().dimensionType().natural() && maid.level().dimensionType().bedWorks()
                && time >= 12000 && MaidSchedules.activity(maid.getSchedule(),time) == Activity.REST;
    }

    public static Collection<EntityMaid> loaded(ServerLevel level) {
        Population population = LEVELS.get(level);
        return population == null ? List.of() : population.maids.values().stream().map(sleeper -> sleeper.maid).toList();
    }

    public static OrderResult orderTonight(ServerLevel level, boolean cancel) {
        Order old = SleepNight.current(level);
        if (!cancel && old != null && !old.cancelled() && !old.automatic()) return OrderResult.ALREADY_QUEUED;
        long issued = old != null && !old.cancelled() ? old.issuedGameTime() : level.getGameTime();
        SleepNight.set(level, new Order(nightEnd(level.getDayTime()), issued, cancel, false));
        return cancel ? OrderResult.CANCELLED : OrderResult.QUEUED;
    }

    /** Retain existing per-maid data for legacy saves and their validation. Commands use shared permission. */
    public static OrderResult order(EntityMaid maid, boolean cancel) {
        if (!cancel && !normalNightRest(maid)) return OrderResult.INELIGIBLE;
        ServerLevel level = (ServerLevel) maid.level();
        Order old = currentOrder(maid);
        if (!cancel && old != null && !old.cancelled() && !old.automatic()) return OrderResult.ALREADY_QUEUED;
        long issued = old != null && !old.cancelled() ? old.issuedGameTime() : level.getGameTime();
        maid.setAndSyncData(ORDER, new Order(nightEnd(level.getDayTime()), issued, cancel, false));
        invalidate(level);
        return cancel ? OrderResult.CANCELLED : OrderResult.QUEUED;
    }

    public static void invalidate(ServerLevel level) {
        Population population = LEVELS.get(level);
        if (population != null) { population.ready = false; population.dirty = true; population.forecast.invalidate(); }
    }

    public static boolean ready(ServerLevel level) {
        Population population = LEVELS.get(level);
        return SleepConfig.settings().enabled() && population != null && population.ready;
    }

    /** Replace a vanilla player-only notice when real sleeping maids are also contributing. */
    public static boolean announcePlayerSleep(ServerLevel level) {
        Population population = LEVELS.get(level);
        SleepSettings settings = SleepConfig.settings();
        if (population == null || !settings.enabled() || !settings.contributeToSleep() || !night(level)) return false;
        SleepProgress progress = population.forecast.progress();
        progress.refreshPlayers(level, settings);
        if (progress.maids() == 0 || !progress.attempting()
                || progress.normalSleepDisabled() && !progress.maidOnlyAttempt()) return false;
        var message = progress.message(null, progress.ready(level) || progress.playerWillSkip(level) || progress.maidSleepingThrough(level));
        for (var player : level.players()) player.displayClientMessage(message, true);
        return true;
    }

    /** An explicit auto-on command resumes cancelled nights; routine maintenance never does. */
    public static void resumeAutomatic(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!compatible(level)) continue;
            Order shared = SleepNight.current(level);
            if (shared != null && shared.cancelled())
                SleepNight.set(level, new Order(nightEnd(level.getDayTime()), level.getGameTime(), false, true));
            Population population = LEVELS.get(level);
            if (population == null) continue;
            boolean changed = false;
            for (Sleeper sleeper : population.maids.values()) {
                EntityMaid maid = sleeper.maid;
                Order old = legacyOrder(maid);
                if (maid.isAlive() && maid.isTame() && old != null && old.cancelled()) {
                    maid.setAndSyncData(ORDER, new Order(nightEnd(level.getDayTime()), level.getGameTime(), false, true));
                    changed = true;
                }
            }
            if (changed) invalidate(level);
        }
    }

    public static void reconfigure(MinecraftServer server) {
        for (var entry : LEVELS.entrySet()) {
            if (entry.getKey().getServer() != server) continue;
            entry.getValue().ready = false;
            entry.getValue().dirty = true;
            entry.getValue().forecast.invalidate();
            Order shared = SleepNight.current(entry.getKey());
            if (shared != null && !shared.cancelled() && (!SleepConfig.settings().enabled()
                    || shared.automatic() && !SleepConfig.settings().autoSleep())) SleepNight.set(entry.getKey(), null);
            for (Sleeper sleeper : entry.getValue().maids.values()) {
                EntityMaid maid = sleeper.maid;
                if (!maid.isAlive() || !maid.isTame()) continue;
                Order order = legacyOrder(maid);
                if (order != null && !order.cancelled() && (!SleepConfig.settings().enabled()
                        || order.automatic() && !SleepConfig.settings().autoSleep())) maid.setAndSyncData(ORDER, EXPIRED);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof EntityMaid maid) {
            LEVELS.computeIfAbsent(level, ignored -> new Population()).maids.put(maid.getUUID(), new Sleeper(maid));
            invalidate(level);
        }
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof EntityMaid maid)) return;
        Population population = LEVELS.get(level);
        if (population == null) return;
        population.maids.remove(maid.getUUID());
        population.living.remove(maid);
        invalidate(level);
    }

    private static void maintainOrders(ServerLevel level, Population population, SleepSettings settings) {
        Order shared = SleepNight.current(level);
        for (Sleeper sleeper : population.maids.values()) {
            EntityMaid maid = sleeper.maid;
            if (!maid.isAlive() || !maid.isTame()) continue;
            Order saved = maid.getData(ORDER);
            Order order = legacyOrder(maid);
            if (saved != null && saved.untilDayTime() != 0 && (order == null || !order.cancelled()
                    && (!settings.enabled() || !compatible(level)
                    || order.automatic() && !settings.autoSleep()))) {
                maid.setAndSyncData(ORDER, EXPIRED);
                order = null;
                population.forecast.invalidate();
            }
            if (shared == null && settings.enabled() && settings.autoSleep() && compatible(level) && order == null && normalNightRest(maid)) {
                maid.setAndSyncData(ORDER, new Order(nightEnd(level.getDayTime()), level.getGameTime(), false, true));
                population.forecast.invalidate();
            }
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SleepSettings settings = SleepConfig.settings();
        Population population = LEVELS.get(level);
        if (population == null && settings.enabled() && !level.players().isEmpty()) {
            population = new Population(); LEVELS.put(level, population);
        }
        if (population == null) return;
        population.ready = false;
        long now = level.getGameTime();
        if (population.dirty || now % 20 == 0) {
            maintainOrders(level, population, settings);
            population.dirty = false;
        }
        if (!settings.enabled()) {
            population.living.clear();
            if (now % 20 == 0) population.forecast.tick(level, List.of(), settings);
            if (population.maids.isEmpty() && level.players().isEmpty()) LEVELS.remove(level);
            return;
        }
        int qualified = 0, sleeping = 0, manualSleeping = 0, manualQualified = 0;
        int checkedSchedules = 0, restingSchedules = 0;
        int time = (int) Math.floorMod(level.getDayTime(), 24000L);
        boolean bedtime = compatible(level) && time >= 12000;
        population.living.clear();
        for (Sleeper sleeper : population.maids.values()) {
            EntityMaid maid = sleeper.maid;
            if (!maid.isAlive() || maid.level() != level) continue;
            population.living.add(maid);
            if (!bedtime || !maid.isSleeping()) { sleeper.bed = null; sleeper.sleepSince = Long.MIN_VALUE; continue; }
            Order order = currentOrder(maid);
            BlockPos bed = maid.getSleepingPos().orElse(null);
            int schedule = 1 << maid.getSchedule().ordinal();
            if ((checkedSchedules & schedule) == 0) {
                checkedSchedules |= schedule;
                if (normalNightRest(maid) && MaidSchedules.activity(maid.getSchedule(),time) == Activity.REST) restingSchedules |= schedule;
            }
            boolean inBed = maid.isTame() && permitted(order, settings) && (restingSchedules & schedule) != 0
                    && bed != null && level.hasChunkAt(bed)
                    && level.getBlockState(bed).isBed(level, bed, maid);
            if (!inBed) { sleeper.bed = null; sleeper.sleepSince = Long.MIN_VALUE; continue; }
            sleeping++;
            if (manual(order)) manualSleeping++;
            // Implicit contribution and automatic permission share one stable interval.
            // An explicit reissue after cancellation still starts a fresh interval.
            long issued = order == null || settings.contributeToSleep() && order.automatic() ? Long.MIN_VALUE : order.issuedGameTime();
            if (!bed.equals(sleeper.bed) || sleeper.seenOrder != issued || sleeper.sleepSince == Long.MIN_VALUE) {
                sleeper.bed = bed.immutable(); sleeper.sleepSince = now; sleeper.seenOrder = issued;
            }
            if (now - sleeper.sleepSince >= settings.minimumSleepTicks()) {
                qualified++;
                if (manual(order)) manualQualified++;
            }
        }
        population.forecast.tick(level, population.living, settings, sleeping, qualified, manualSleeping, manualQualified);
        population.ready = population.forecast.progress().ready(level);
        if (population.maids.isEmpty() && level.players().isEmpty()) LEVELS.remove(level);
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { LEVELS.remove(event.getLevel()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }
}
