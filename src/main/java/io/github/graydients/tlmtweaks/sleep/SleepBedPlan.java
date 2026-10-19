// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sleep;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidUnderWaterNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Attempt-time bed diagnosis. Never changes POI tickets, bed occupancy, navigation or brain memories. */
public final class SleepBedPlan {
    private record Check(EntityMaid maid, BlockPos bed) {}
    private final ServerLevel level;
    private final Map<UUID, EntityMaid> maids = new LinkedHashMap<>();
    private final Map<UUID, List<BlockPos>> reachable = new LinkedHashMap<>();
    private final List<Check> checks = new ArrayList<>();
    private final Set<BlockPos> beds = new HashSet<>();
    private final Map<UUID, BlockPos> assignments = new HashMap<>();
    private final Map<BlockPos, UUID> owners = new HashMap<>();
    private int cursor, blocked;
    private boolean unloaded, finished;

    public SleepBedPlan(ServerLevel level, Collection<EntityMaid> candidates) {
        this.level = level;
        var chunkBeds = new HashMap<Long, List<BlockPos>>();
        for (EntityMaid maid : candidates.stream().sorted(Comparator.comparing(EntityMaid::getUUID)).toList()) {
            maids.put(maid.getUUID(), maid);
            reachable.put(maid.getUUID(), new ArrayList<>());
            if (maid.isSleeping()) {
                maid.getSleepingPos().filter(pos -> available(maid, pos)).ifPresent(pos -> {
                    beds.add(pos); reachable.get(maid.getUUID()).add(pos);
                });
            } else if (maid.isNoAi() || !maid.canBrainMoving()) blocked++;
            else for (BlockPos pos : candidates(maid, chunkBeds)) { beds.add(pos); checks.add(new Check(maid, pos)); }
        }
        for (UUID maid : reachable.keySet()) reserve(maid, reachable, owners, assignments);
    }

    private List<BlockPos> candidates(EntityMaid maid, Map<Long, List<BlockPos>> chunkBeds) {
        BlockPos center = maid.isHomeModeEnable() ? maid.getSchedulePos().getSleepPos() : maid.getBrainSearchPos();
        int radius = maid.isHomeModeEnable() ? MaidConfig.MAID_SLEEP_RANGE.get() : (int) maid.getRestrictRadius();
        var found = new HashSet<BlockPos>();
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) { unloaded = true; continue; }
                var chunk = new ChunkPos(x, z);
                // Shared only during construction; live bed/occupancy checks remain maid-specific.
                var positions = chunkBeds.computeIfAbsent(chunk.toLong(), key -> level.getPoiManager()
                        .getInChunk(holder -> holder.value().equals(InitPoi.MAID_BED.get()), chunk, PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos().immutable()).toList());
                for (var pos : positions)
                    if (pos.distSqr(center) < (double) radius * radius && available(maid, pos)) found.add(pos);
            }
        return found.stream().sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(maid.position()))).toList();
    }

    private boolean available(EntityMaid maid, BlockPos head) {
        if (!level.hasChunkAt(head)) return false;
        var state = level.getBlockState(head);
        if (!state.is(InitBlocks.MAID_BED.get()) || state.getValue(BlockMaidBed.PART) != BedPart.HEAD) return false;
        BlockPos foot = head.relative(state.getValue(BlockMaidBed.FACING).getOpposite());
        if (!level.hasChunkAt(foot)) return false;
        var other = level.getBlockState(foot);
        if (!other.is(InitBlocks.MAID_BED.get()) || other.getValue(BlockMaidBed.PART) != BedPart.FOOT
                || other.getValue(BlockMaidBed.FACING) != state.getValue(BlockMaidBed.FACING)) return false;
        return !state.getValue(BlockMaidBed.OCCUPIED) && !other.getValue(BlockMaidBed.OCCUPIED)
                || maid.getSleepingPos().filter(head::equals).isPresent();
    }

    /** At most two real path searches per level tick, using private evaluators instead of the live navigator. */
    public void step(int needed) {
        // No route can compensate for a known shortage of distinct beds.
        if (complete()) return;
        if (beds.size() < needed) { finished = true; return; }
        for (int budget = 2; cursor < checks.size() && budget > 0 && assignments.size() < needed; budget--) {
            Check next = checks.get(cursor++);
            if (next.maid().isAlive() && available(next.maid(), next.bed()) && pathReachable(next.maid(), next.bed())) {
                reachable.get(next.maid().getUUID()).add(next.bed());
                extend(reachable, owners, assignments);
            }
        }
        if (assignments.size() >= needed) finished = true;
    }

    private boolean pathReachable(EntityMaid maid, BlockPos bed) {
        if (bed.distToCenterSqr(maid.position()) < 4) return true; // Same close-enough distance as MaidBedTask.
        float range = (float) maid.getAttributeValue(Attributes.FOLLOW_RANGE);
        int extent = (int) (range + 8);
        BlockPos center = maid.blockPosition();
        int wantedMinX = (center.getX() - extent) >> 4, wantedMaxX = (center.getX() + extent) >> 4;
        int wantedMinZ = (center.getZ() - extent) >> 4, wantedMaxZ = (center.getZ() + extent) >> 4;
        int minX = Math.min(center.getX() >> 4, bed.getX() >> 4), maxX = Math.max(center.getX() >> 4, bed.getX() >> 4);
        int minZ = Math.min(center.getZ() >> 4, bed.getZ() >> 4), maxZ = Math.max(center.getZ() >> 4, bed.getZ() >> 4);
        if (!loaded(minX, maxX, minZ, maxZ)) { unloaded = true; return false; }
        // Grow a loaded rectangle around both endpoints instead of requiring every faraway range chunk.
        boolean grew;
        do {
            grew = false;
            if (minX > wantedMinX && loaded(minX - 1, minX - 1, minZ, maxZ)) { minX--; grew = true; }
            if (maxX < wantedMaxX && loaded(maxX + 1, maxX + 1, minZ, maxZ)) { maxX++; grew = true; }
            if (minZ > wantedMinZ && loaded(minX, maxX, minZ - 1, minZ - 1)) { minZ--; grew = true; }
            if (maxZ < wantedMaxZ && loaded(minX, maxX, maxZ + 1, maxZ + 1)) { maxZ++; grew = true; }
        } while (grew);
        boolean clipped = minX > wantedMinX || maxX < wantedMaxX || minZ > wantedMinZ || maxZ < wantedMaxZ;
        BlockPos min = new BlockPos(Math.max(center.getX() - extent, minX << 4), center.getY() - extent,
                Math.max(center.getZ() - extent, minZ << 4));
        BlockPos max = new BlockPos(Math.min(center.getX() + extent, (maxX << 4) + 15), center.getY() + extent,
                Math.min(center.getZ() + extent, (maxZ << 4) + 15));
        NodeEvaluator live = maid.getNavigation().getNodeEvaluator();
        NodeEvaluator evaluator = live instanceof MaidUnderWaterNodeEvaluator
                ? new MaidUnderWaterNodeEvaluator() : new MaidNodeEvaluator();
        evaluator.setCanFloat(live.canFloat()); evaluator.setCanOpenDoors(live.canOpenDoors());
        evaluator.setCanPassDoors(live.canPassDoors()); evaluator.setCanWalkOverFences(live.canWalkOverFences());
        var finder = new PathFinder(evaluator, (int) (range * 16));
        var region = new PathNavigationRegion(level, min, max);
        var path = finder.findPath(region, maid, Set.of(bed), range, 1, 1);
        if (path != null && path.canReach()) return true;
        if (clipped) unloaded = true; // A missing detour cannot be ruled out beyond loaded chunks.
        return false;
    }

    private boolean loaded(int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }

    public boolean complete() { return finished || cursor >= checks.size(); }
    public int checkedPaths() { return cursor; }
    public int availableBeds() { return beds.size(); }
    public int blockedMaids() { return blocked; }
    public boolean hasUnloadedArea() { return unloaded; }
    public int ready() { return assignments.size(); }
    public Map<UUID, BlockPos> assignments() { return Map.copyOf(assignments); }
    public boolean hasCandidates(List<EntityMaid> candidates) {
        if (candidates.size() != maids.size()) return false;
        for (EntityMaid maid : candidates) if (maids.get(maid.getUUID()) != maid) return false;
        return true;
    }
    public boolean valid() {
        for (var entry : assignments.entrySet()) {
            EntityMaid maid = maids.get(entry.getKey());
            if (!maid.isAlive() || !available(maid, entry.getValue())) return false;
        }
        return true;
    }

    /** Maximum matching avoids counting a bed twice or stranding a maid with only one reachable bed. */
    static Map<UUID, BlockPos> match(Map<UUID, List<BlockPos>> graph) {
        var owners = new HashMap<BlockPos, UUID>();
        var assigned = new HashMap<UUID, BlockPos>();
        for (UUID maid : graph.keySet()) reserve(maid, graph, owners, assigned);
        return assigned;
    }

    /** One new edge can add at most one assignment; a newly reachable alternative may free another maid's bed. */
    private static void extend(Map<UUID, List<BlockPos>> graph, Map<BlockPos, UUID> owners, Map<UUID, BlockPos> assigned) {
        for (UUID maid : graph.keySet())
            if (!assigned.containsKey(maid) && reserve(maid, graph, owners, assigned)) return;
    }

    /** Iterative shortest augmenting path: no recursive chain or rebuilding already valid assignments. */
    private static boolean reserve(UUID root, Map<UUID, List<BlockPos>> graph, Map<BlockPos, UUID> owners, Map<UUID, BlockPos> assigned) {
        // Most new maids have a free reachable bed. Avoid allocating a search frontier for them.
        for (BlockPos bed : graph.getOrDefault(root, List.of())) if (!owners.containsKey(bed)) {
            owners.put(bed, root); assigned.put(root, bed); return true;
        }
        var parents = new HashMap<UUID, UUID>();
        var queue = new ArrayDeque<UUID>();
        parents.put(root, null); queue.add(root);
        while (!queue.isEmpty()) {
            UUID maid = queue.remove();
            for (BlockPos bed : graph.getOrDefault(maid, List.of())) {
                UUID owner = owners.get(bed);
                if (owner == null) {
                    while (maid != null) {
                        BlockPos previous = assigned.put(maid, bed);
                        owners.put(bed, maid);
                        bed = previous; maid = parents.get(maid);
                    }
                    return true;
                }
                if (!parents.containsKey(owner)) { parents.put(owner, maid); queue.add(owner); }
            }
        }
        return false;
    }
}
