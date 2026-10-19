// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameEntityBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindSitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBoardGames;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFishing;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Vacant seats are shared leisure space; an actual work request has priority over leisure. */
public final class ChairUse {
    private ChairUse() {}

    public interface Context {
        Approach graydients$seatApproach();
        void graydients$seatApproach(Approach approach);
    }

    public record Approach(Entity seat, WalkTarget walk, ResourceLocation job, boolean casual, long expires, long retryAt) {}

    /** Reuse the work-area sensor; navigation may reach work seats around a blocked sight line. */
    public static boolean seek(EntityMaid maid, boolean casual, float speed) {
        var previous = ((Context) maid).graydients$seatApproach();
        if (previous != null && maid.level().getGameTime() < previous.retryAt()) return true;
        Entity selected = null;
        if (casual) {
            var visible = maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            if (visible.isPresent()) selected = visible.get().findClosest(seat -> select(maid, seat, true)).orElse(null);
        } else {
            var nearby = maid.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
            if (nearby.isPresent()) for (var seat : nearby.get()) {
                if (select(maid, seat, false)) { selected = seat; break; }
            }
        }
        if (selected == null) return false;
        BehaviorUtils.setWalkAndLookTargetMemories(maid, selected, speed, 1);
        var walk = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        ((Context) maid).graydients$seatApproach(new Approach(selected, walk, maid.getTask().getUid(), casual,
                maid.level().getGameTime() + 600, 0));
        arrive(maid);
        return true;
    }

    /** Boarding is checked during the approach, independently of the next search opportunity. */
    public static void arrive(EntityMaid maid) {
        var context = (Context) maid;
        var approach = context.graydients$seatApproach();
        if (approach == null) return;
        var seat = approach.seat();
        var brain = maid.getBrain();
        var walk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        boolean nearby = seat.closerThan(maid, 2);
        boolean valid = JobConfig.seatRecovery() && maid.isAlive() && maid.isRideable() && seat.isAlive()
                && !maid.isMaidInSittingPose() && !maid.isSleeping() && !maid.isLeashed()
                && (maid.getScheduleDetail() == Activity.WORK || approach.casual() && maid.getScheduleDetail() == Activity.IDLE)
                && maid.getTask().getUid().equals(approach.job()) && maid.level().getGameTime() <= approach.expires()
                && (walk == approach.walk() || walk == null && nearby)
                && maid.isWithinRestriction(seat.blockPosition());
        if (!valid || maid.getVehicle() == seat) {
            context.graydients$seatApproach(null);
            if (walk == approach.walk()) { brain.eraseMemory(MemoryModuleType.WALK_TARGET); maid.getNavigation().stop(); }
            return;
        }
        if (!nearby || maid.isPassenger() || maid.level().getGameTime() < approach.retryAt()) return;
        if (!select(maid, seat, approach.casual())) {
            context.graydients$seatApproach(null);
            if (walk == approach.walk()) { brain.eraseMemory(MemoryModuleType.WALK_TARGET); maid.getNavigation().stop(); }
            return;
        }
        if (!seat.getPassengers().isEmpty() && !yieldTo(maid, seat)) {
            context.graydients$seatApproach(new Approach(seat, approach.walk(), approach.job(), approach.casual(),
                    approach.expires(), maid.level().getGameTime() + 20));
            return;
        }
        if (maid.startRiding(seat, true)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET); brain.eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop(); context.graydients$seatApproach(null);
        }
    }

    public static boolean locked(EntityMaid maid) { return JobConfig.seatLock() && !maid.isRideable(); }

    private static boolean working(EntityMaid maid) {
        return maid.isAlive() && !maid.isMaidInSittingPose() && !maid.isSleeping()
                && !maid.isLeashed() && maid.getScheduleDetail() == Activity.WORK;
    }

    public static boolean servesWork(EntityMaid maid, Entity seat) {
        if (seat == null || !working(maid) || maid.getTask() instanceof TaskIdle) return false;
        if (maid.getTask() instanceof TaskFishing) return FishingSeats.waterAt(maid, seat) != null;
        if (maid.getTask() instanceof TaskBoardGames) {
            return seat instanceof EntitySit sit && maid.getTask().canSitInJoy(maid, sit.getJoyType())
                    && maid.level().hasChunkAt(sit.getAssociatedBlockPos())
                    && maid.level().getBlockEntity(sit.getAssociatedBlockPos()) instanceof IBoardGameEntityBlock board
                    && sit.getUUID().equals(board.getSitId());
        }
        return ((SeatWork.Context) maid).graydients$nativeRidingWork();
    }

    /** Selection never ejects an occupant; repeat the priority check on arrival. */
    public static boolean canTake(EntityMaid worker, Entity seat) {
        if (!JobConfig.seatRecovery() || !working(worker) || !worker.isRideable() || !seat.isAlive()) return false;
        for (var passenger : seat.getPassengers()) {
            if (!(passenger instanceof EntityMaid occupant) || occupant == worker
                    || locked(occupant) || servesWork(occupant, seat)) return false;
        }
        return true;
    }

    public static boolean select(EntityMaid maid, Entity seat, boolean casual) {
        if (!maid.isRideable() || !seat.isAlive() || seat == maid.getVehicle()
                || !(seat instanceof EntityChair || seat instanceof Boat) || !maid.isWithinRestriction(seat.blockPosition())) return false;
        if (!casual && maid.getTask() instanceof TaskFishing && FishingSeats.waterAt(maid, seat) == null) return false;
        return seat.getPassengers().isEmpty() || !casual && canTake(maid, seat);
    }

    /** Only called by the selected job at native boarding distance. No player is evicted. */
    public static boolean yieldTo(EntityMaid worker, Entity seat) {
        if (!canTake(worker, seat)) return false;
        var passengers = List.copyOf(seat.getPassengers());
        var destinations = new ArrayList<Vec3>(passengers.size());
        for (var passenger : passengers) {
            var occupant = (EntityMaid) passenger;
            var destination = clearance(occupant, worker, seat, destinations);
            // Keep the current occupant seated when no safe adjacent ground is available.
            if (destination == null) return false;
            destinations.add(destination);
        }
        for (int i = 0; i < passengers.size(); i++) {
            var occupant = (EntityMaid) passengers.get(i);
            SeatWork.leave(occupant);
            if (occupant.isPassenger()) return false;
            occupant.getNavigation().stop();
            occupant.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            occupant.getBrain().eraseMemory(MemoryModuleType.PATH);
            var destination = destinations.get(i);
            occupant.teleportTo(destination.x, destination.y, destination.z);
        }
        return seat.getPassengers().isEmpty();
    }

    /** Constant-size, handover-only search using vanilla safe-dismount geometry. Never loads chunks. */
    private static Vec3 clearance(EntityMaid maid, EntityMaid worker, Entity seat, List<Vec3> reserved) {
        var base = seat.blockPosition();
        for (int radius = 1; radius <= 2; radius++) for (int dy = -1; dy <= 1; dy++)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var pos = base.offset(dx, dy, dz);
                if (!maid.isWithinRestriction(pos) || !maid.level().hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))
                        || !maid.level().getFluidState(pos).isEmpty() || !maid.level().getFluidState(pos.below()).isEmpty()) continue;
                var point = DismountHelper.findSafeDismountLocation(maid.getType(), maid.level(), pos, true);
                if (point == null) continue;
                var box = maid.getBoundingBox().move(point.subtract(maid.position()));
                if (box.intersects(seat.getBoundingBox()) || box.intersects(worker.getBoundingBox())
                        || !maid.level().getWorldBorder().isWithinBounds(box) || !maid.level().noCollision(maid, box)) continue;
                boolean overlaps = false;
                for (var previous : reserved) if (previous.distanceToSqr(point) < 1) { overlaps = true; break; }
                if (!overlaps) return point;
            }
        return null;
    }

    public static Entity boardSeat(ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof IBoardGameEntityBlock board)
            return level.getEntity(board.getSitId());
        return null;
    }

    public static void useBoard(IBoardGameBlock block, EntityMaid maid, BlockState state, Level level, BlockPos pos) {
        if (JobConfig.seatRecovery() && level instanceof ServerLevel server) {
            var seat = boardSeat(server, pos);
            if (seat != null) {
                if (!yieldTo(maid, seat)) return;
                // Native board seats are temporary entities; the block creates its replacement.
                seat.discard();
            }
        }
        block.startMaidSit(maid, state, level, pos);
    }

    public static List<Pair<Integer, BehaviorControl<? super EntityMaid>>> casualTasks(
            List<Pair<Integer, BehaviorControl<? super EntityMaid>>> original) {
        if (!JobConfig.seatRecovery()) return original;
        var result = new ArrayList<>(original);
        result.add(Pair.of(18, new CasualSeat()));
        return result;
    }

    /** Reuses the native nearest-living-entity sensor and native walk/boarding behavior. */
    public static final class CasualSeat extends MaidFindSitTask {
        private CasualSeat() { super(0.6f); setMaxCheckRate(100); }
        @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
            return JobConfig.seatRecovery() && maid.isAlive() && maid.isRideable()
                    && !maid.isMaidInSittingPose() && !maid.isSleeping() && !maid.isLeashed()
                    && (maid.getScheduleDetail() == Activity.WORK || maid.getScheduleDetail() == Activity.IDLE)
                    && !maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())
                    && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && super.checkExtraStartConditions(level, maid);
        }
    }
}
