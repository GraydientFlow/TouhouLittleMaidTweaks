// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-19.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBoardGames;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskCrossBowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskDanmakuAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFishing;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskTridentAttack;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Boat;

/** Runs the job's own work selection while seated; no independent work search. */
public final class SeatWork implements BehaviorControl<EntityMaid> {
    /** Optional seat integrations that permit the same schedule travel as native maid seats. */
    public interface ScheduleSeat {}

    public interface Context {
        boolean graydients$boardingCoolingDown();
        void graydients$seatWork(boolean active);
        boolean graydients$seatWork();
        void graydients$seatWorkTarget(WalkTarget target);
        void graydients$nativeRidingWork(boolean active);
        boolean graydients$nativeRidingWork();
    }

    private final BehaviorControl<? super EntityMaid> task;
    private SeatWork(BehaviorControl<? super EntityMaid> task) { this.task = task; }

    public static List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks(
            IMaidTask job, EntityMaid maid, List<Pair<Integer, BehaviorControl<? super EntityMaid>>> riding) {
        ((Context) maid).graydients$nativeRidingWork(!riding.isEmpty() && !(job instanceof TaskIdle));
        if (!JobConfig.seatRecovery() || job instanceof TaskIdle) return riding;
        if (combatPursuit(job)) {
            // Riding combat retains its native attacks, but omits the normal pursuit selector.
            // Restore only movement so attacks are not registered or executed twice.
            BehaviorControl<? super EntityMaid> pursuit = job instanceof TaskAttack
                    ? SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.6f)
                    : MaidRangedWalkToTarget.create(0.6f);
            var result = new ArrayList<>(riding);
            result.add(Pair.of(5, new SeatWork(pursuit)));
            return result;
        }
        // These jobs can seek actual work from an unrelated seat. Their own valid seats stay native.
        boolean seekSeat = job instanceof TaskFishing || job instanceof TaskBoardGames;
        if (!seekSeat && !riding.isEmpty()) return riding;
        var result = new ArrayList<>(riding);
        for (var entry : job.createBrainTasks(maid))
            result.add(Pair.of(entry.getFirst(), new SeatWork(entry.getSecond())));
        return result;
    }

    private static boolean combatPursuit(IMaidTask job) {
        return job instanceof TaskAttack || job instanceof TaskBowAttack || job instanceof TaskCrossBowAttack
                || job instanceof TaskTridentAttack || job instanceof TaskDanmakuAttack;
    }

    public static boolean eligible(EntityMaid maid) {
        return JobConfig.seatRecovery() && maid.isPassenger() && maid.isRideable() && maid.isAlive()
                && !maid.isMaidInSittingPose() && !maid.isSleeping() && !maid.isLeashed()
                && maid.getScheduleDetail() == Activity.WORK
                && (combatPursuit(maid.getTask()) || !ChairUse.servesWork(maid, maid.getVehicle()));
    }

    @Override public Behavior.Status getStatus() { return task.getStatus(); }

    @Override public boolean tryStart(ServerLevel level, EntityMaid maid, long time) {
        if (!eligible(maid)) return false;
        var before = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        var context = (Context) maid;
        context.graydients$seatWork(true);
        try { return task.tryStart(level, maid, time); }
        finally { finishCall(maid, context, before); }
    }

    @Override public void tickOrStop(ServerLevel level, EntityMaid maid, long time) {
        if (!eligible(maid)) { task.doStop(level, maid, time); return; }
        var before = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        var context = (Context) maid;
        context.graydients$seatWork(true);
        try { task.tickOrStop(level, maid, time); }
        finally { finishCall(maid, context, before); }
    }

    private static void finishCall(EntityMaid maid, Context context, WalkTarget before) {
        context.graydients$seatWork(false);
        var after = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (after != null && after != before) context.graydients$seatWorkTarget(after);
    }

    @Override public void doStop(ServerLevel level, EntityMaid maid, long time) { task.doStop(level, maid, time); }
    @Override public String debugString() { return "Seat work: " + task.debugString(); }

    /** Work completed within reach can clear its walk before this end-of-AI-step check. */
    public static void resume(EntityMaid maid, WalkTarget selected) {
        if (!eligible(maid) || maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != selected) return;
        var destination = selected.getTarget().currentBlockPosition();
        if (!maid.level().hasChunkAt(destination) || !maid.isWithinRestriction(destination)
                || maid.blockPosition().distManhattan(destination) <= selected.getCloseEnoughDist()) return;
        leave(maid);
    }

    /** Schedule travel is independent of job availability; sleeping cannot run in RIDE_REST. */
    public static Activity followSchedule(EntityMaid maid, Activity previous) {
        var current = maid.getScheduleDetail();
        var vehicle = maid.getVehicle();
        if (!JobConfig.seatRecovery() || !maid.isAlive()
                || !(vehicle instanceof EntityChair || vehicle instanceof EntitySit || vehicle instanceof Boat
                    || vehicle instanceof ScheduleSeat)) return current;
        // Keep an unhandled transition pending until the user unlocks or releases standby.
        if (ChairUse.locked(maid) || maid.isMaidInSittingPose() || maid.isSleeping() || maid.isLeashed())
            return previous == null ? current : previous;
        if (current != Activity.REST && (previous == null || previous == current)) return current;
        leave(maid);
        if (maid.isPassenger()) return previous;
        var brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (maid.isHomeModeEnable()) {
            maid.getSchedulePos().restrictTo(maid);
            BehaviorUtils.setWalkAndLookTargetMemories(maid, maid.getRestrictCenter(), 0.7f, 3);
        }
        return current;
    }

    public static void leave(EntityMaid maid) {
        if (ChairUse.locked(maid)) return;
        maid.stopRiding();
        if (!maid.isPassenger()) maid.getBrain().setActiveActivityIfPossible(maid.getScheduleDetail());
    }
}
