// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Placement/explicit discovery shares the same incremental node budget as revalidation. */
final class PenSelection {
    private final StationManager manager;
    private final StationManager.Pen pen;
    private final StationBlockEntity station;
    private final int[] sides;
    private final Set<Long> explored = new HashSet<>();
    private PenScan current, best;
    private int index, currentSide, bestSide, bestRank = Integer.MAX_VALUE;
    private boolean done;

    PenSelection(StationManager manager, StationManager.Pen pen) {
        this.manager = manager; this.pen = pen; station = pen.anchorStation();
        sides = station.detectionMode == 0 ? new int[]{1, 2, 3, 4, 5} : new int[]{station.detectionMode};
        if (station.selectedSeed != null) {
            currentSide = station.selectedSide;
            current = new PenScan(manager, pen, station.selectedSeed);
            index = sides.length; // Geometry changes never switch to a different enclosure.
        }
    }

    private BlockState read(BlockPos pos) {
        manager.watch(pen, pos.asLong());
        return manager.level.hasChunkAt(pos) ? manager.level.getBlockState(pos) : null;
    }

    private BlockPos seed(int side) {
        BlockPos pos = station.getBlockPos();
        boolean mounted = station.getBlockState().getBlock() instanceof StationFenceBlock;
        if (side == 1) return mounted ? null : pos;
        Direction front = station.getBlockState().getValue(StationBlock.FACING);
        // Left/right are relative to looking out from the front of the station.
        Direction direction = switch (side) {
            case 2 -> front.getOpposite(); case 3 -> front.getCounterClockWise();
            case 4 -> front.getClockWise(); default -> front;
        };
        BlockPos near = pos.relative(direction);
        BlockState at = read(near);
        if (at == null) return near; // PenScan reports unloaded; never force a chunk load.
        if (mounted) return PenScan.boundary(at) ? null : near;
        if (!PenScan.boundary(at)) return side == 2 ? null : near;
        BlockPos far = near.relative(direction);
        BlockState beyond = read(far);
        // Exactly one barrier may be crossed. A T junction/double fence is ambiguous.
        return beyond != null && PenScan.boundary(beyond) ? null : far;
    }

    boolean step() {
        if (done || pen.removed) return true;
        if (current == null) {
            if (index == sides.length) { done = true; return true; }
            currentSide = sides[index++];
            BlockPos seed = seed(currentSide);
            if (seed == null || explored.contains(seed.asLong())) return false;
            current = new PenScan(manager, pen, seed);
        }
        if (!current.step()) return false;
        explored.addAll(current.cells);
        int rank = current.failure != 0 ? 3 : current.fenced && currentSide == 1 ? 0
                : current.fenced && currentSide == 2 ? 1 : 2;
        if (rank < bestRank) { best = current; bestSide = currentSide; bestRank = rank; }
        if (current.failure == 3 && bestRank == 3) { best = current; bestSide = currentSide; }
        done = index == sides.length || bestRank <= 1 || bestRank == 2 && index >= 2;
        current = null;
        return done;
    }

    PenScan result() {
        if (best == null) {
            best = new PenScan(manager, pen, station.getBlockPos()); best.failure = 2;
            bestSide = station.detectionMode;
            station.selectedSeed = null;
        } else station.selectedSeed = best.origin;
        station.selectedSide = bestSide;
        station.setChanged();
        return best;
    }
}
