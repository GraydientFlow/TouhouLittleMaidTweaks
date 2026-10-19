// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import io.github.graydients.tlmtweaks.jobs.JobConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Incremental surface flood. Only stations do this work; no roof or animal-height scan. */
final class PenScan {
    final StationManager manager;
    final StationManager.Pen pen;
    final BlockPos origin;
    final long generation;
    final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    final Set<Long> cells = new HashSet<>(), watched = new HashSet<>();
    int failure;
    boolean fenced;
    PenScan(StationManager manager, StationManager.Pen pen, BlockPos origin) {
        this.manager = manager; this.pen = pen; this.origin = origin; generation = pen.generation;
        if (floor(origin)) { queue.add(origin); cells.add(origin.asLong()); }
        else if (failure == 0) failure = 2;
    }
    private BlockState read(BlockPos pos) {
        watched.add(pos.asLong()); manager.watch(pen, pos.asLong());
        if (!manager.level.hasChunkAt(pos)) { failure = 3; return null; }
        return manager.level.getBlockState(pos);
    }
    private boolean floor(BlockPos feet) {
        if (feet.getY() <= manager.level.getMinBuildHeight() || feet.getY() >= manager.level.getMaxBuildHeight()) return false;
        BlockState state = read(feet), support = read(feet.below());
        if (state == null || support == null) return false;
        if (support.getBlock() instanceof FenceBlock || support.getBlock() instanceof WallBlock || support.getBlock() instanceof FenceGateBlock) return false;
        boolean empty = state.getBlock() instanceof StationBlock || state.getCollisionShape(manager.level, feet).isEmpty();
        // Open gates are passages; closed gates/fences are barriers, including their top surface.
        return empty && !support.getCollisionShape(manager.level, feet.below()).isEmpty();
    }
    boolean step() {
        if (failure != 0 || pen.generation != generation || pen.removed) return true;
        BlockPos pos = queue.poll();
        if (pos == null) return true;
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0) continue;
            BlockPos next = pos.offset(dx,0,dz);
            BlockState at = read(next);
            if (at == null) continue;
            if(dx!=0 && dz!=0) {
                BlockState left=read(pos.offset(dx,0,0)),right=read(pos.offset(0,0,dz));
                if(left==null || right==null) continue;
                boolean leftSolid=!left.getCollisionShape(manager.level,pos.offset(dx,0,0)).isEmpty(),rightSolid=!right.getCollisionShape(manager.level,pos.offset(0,0,dz)).isEmpty();
                if(leftSolid && rightSolid && !thin(left) && !thin(right)) continue;
            }
            if (boundary(at)) { fenced = true; continue; }
            BlockPos found = null;
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos candidate = next.offset(0, dy, 0);
                if (floor(candidate)) { found = candidate; break; }
            }
            if (found == null) {
                if (at.getCollisionShape(manager.level, next).isEmpty()) { failure = 2; return true; }
                continue;
            }
            if (Math.abs(found.getX() - origin.getX()) > JobConfig.scanRadius() || Math.abs(found.getZ() - origin.getZ()) > JobConfig.scanRadius()) {
                failure = 2; return true;
            }
            if (cells.add(found.asLong())) {
                if (cells.size() > JobConfig.scanCells()) { failure = 2; return true; }
                queue.add(found);
            }
        }
        return failure != 0 || queue.isEmpty();
    }
    private static boolean thin(BlockState state) {return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;}
    static boolean boundary(BlockState state) {
        return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }
    /** These are exactly the state properties used by selection and the floor flood. */
    static boolean sameGeometry(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        return thin(before) == thin(after) && boundary(before) == boundary(after)
                && (before.getBlock() instanceof StationBlock) == (after.getBlock() instanceof StationBlock)
                && before.getCollisionShape(level, pos).isEmpty() == after.getCollisionShape(level, pos).isEmpty();
    }
}
