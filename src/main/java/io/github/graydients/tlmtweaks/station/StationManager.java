// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.jobs.AnimalWork;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.StationMembership;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Shared server-thread index and a globally bounded maintenance queue. */
@EventBusSubscriber(modid = MaidTweaks.ID)
public final class StationManager {
    private static final Map<ServerLevel, StationManager> LEVELS = new LinkedHashMap<>();
    public final ServerLevel level;
    private final Long2ObjectOpenHashMap<StationBlockEntity> stations = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Pen> membership = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Set<Pen>> watches = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Set<Pen>> chunkWatches = new Long2ObjectOpenHashMap<>();
    private final Set<Pen> pens = new HashSet<>();
    private final ArrayDeque<Pen> work = new ArrayDeque<>();
    private BlockPos milkVisualUpdate;
    private static long nodes, scans, populationQueries, maintenanceNanos, worstNanos, lastPolicyOrder;
    public record Metrics(long nodes, long scans, long populationQueries, long maintenanceNanos, long worstNanos, int queued, int watched, int cells) {}
    public static Metrics metrics() {
        return new Metrics(nodes, scans, populationQueries, maintenanceNanos, worstNanos,
                LEVELS.values().stream().mapToInt(m -> m.work.size()).sum(), LEVELS.values().stream().mapToInt(m -> m.watches.size()).sum(),
                LEVELS.values().stream().mapToInt(m -> m.membership.size()).sum());
    }
    private StationManager(ServerLevel level) { this.level = level; }
    public static StationManager get(ServerLevel level) { return LEVELS.computeIfAbsent(level, StationManager::new); }
    public static final class Pen {
        final Set<StationBlockEntity> stations = new HashSet<>();
        Set<Long> cells = new HashSet<>(), watched = new HashSet<>();
        final LongOpenHashSet watchedChunks = new LongOpenHashSet();
        StationSettings settings;
        private StationSettings allocationSettings;
        private int allocationArea = -1;
        private Map<EntityType<?>, Long> allocationSizes = Map.of(), allocation = Map.of();
        AnimalWork.Population population;
        long populationVersion, surveyedVersion = -1;
        List<StationAnimals.Row> animalRows = List.of();
        Cow milkCow;
        long milkQueryTick = -1;
        AABB bounds;
        PenSelection scan;
        boolean queued, removed;
        long generation, nextPopulation, policyOrder, freshTick = -1;
        int status; // 0 scanning, 1 enclosed, 2 open/too large, 3 unloaded, 4 slaughter, 5 breeding
        StationBlockEntity anchorStation() { return stations.stream().min(Comparator.comparing(StationBlockEntity::getBlockPos)).orElseThrow(); }
        BlockPos anchor() { return anchorStation().getBlockPos(); }
        public StationSettings settings() { return settings; }
        public int area() { return cells.size(); }
        public boolean verified() { return status == 1 || status == 4 || status == 5; }
        boolean excessive(AnimalWork.Population p, Animal animal) {
            if (settings.countMode()) return p.adults().getOrDefault(animal.getType(), 0) > settings.keepCount();
            var share = p.species().get(animal.getType());
            return share != null && share.weight() > share.limit();
        }
        boolean breedAllowed(AnimalWork.Population p, Animal animal) {
            if (!settings.limitBreeding()) return true;
            if (settings.countMode()) return p.adults().getOrDefault(animal.getType(), 0) < settings.keepCount();
            var share = p.species().get(animal.getType());
            return share != null && share.weight() < share.limit();
        }
        private Map<EntityType<?>, Long> capacities(Map<EntityType<?>, Long> sizes) {
            if (!settings.equals(allocationSettings) || cells.size() != allocationArea || !sizes.equals(allocationSizes)) {
                long budget = BigDecimal.valueOf(settings.density()).multiply(BigDecimal.valueOf(cells.size() * 4L)).longValue();
                allocation = SpeciesCapacity.allocate(budget, sizes,
                        Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
                allocationSizes = Map.copyOf(sizes); allocationSettings = settings; allocationArea = cells.size();
            }
            return allocation;
        }
    }
    public void add(StationBlockEntity station) {
        lastPolicyOrder=Math.max(lastPolicyOrder,station.policyOrder);
        var previous=stations.get(station.getBlockPos().asLong());
        if (previous==station) return;
        if (previous!=null && previous.pen!=null) {
            Pen pen=previous.pen;pen.stations.remove(previous);pen.stations.add(station);station.pen=pen;station.settings=pen.settings;station.policyOrder=pen.policyOrder;stations.put(station.getBlockPos().asLong(),station);queue(pen);return;
        }
        stations.put(station.getBlockPos().asLong(), station);
        Pen known = station.selectedSeed == null && (station.detectionMode != 0 || station.getBlockState().getBlock() instanceof StationFenceBlock) ? null
                : membership.get((station.selectedSeed == null ? station.getBlockPos() : station.selectedSeed).asLong());
        if(known!=null && !known.removed) {if(station.selectedSeed==null) {station.selectedSeed=station.getBlockPos();station.selectedSide=1;} known.stations.add(station);station.pen=known;station.settings=known.settings;station.policyOrder=known.policyOrder;queue(known);return;}
        Pen pen = new Pen(); pen.stations.add(station); pen.settings = station.settings; pen.policyOrder=station.policyOrder; station.pen = pen;
        for (long cell : station.savedCells) { pen.cells.add(cell); membership.putIfAbsent(cell, pen); }
        for (long cell : station.savedBoundary) watch(pen, cell);
        pens.add(pen); queue(pen);
    }
    public void unload(StationBlockEntity station) {
        var pen=station.pen;if(pen==null || pen.removed)return;
        watch(pen, station.getBlockPos().asLong());
        suspend(pen);
    }
    private void suspend(Pen pen) {
        pen.generation++;pen.scan=null;pen.status=3;pen.population=null;pen.queued=false;work.remove(pen);
        pen.milkCow = null; publishMilk(pen, false);
    }
    public void remove(StationBlockEntity station) {
        if (!stations.remove(station.getBlockPos().asLong(), station)) return;
        Pen pen = station.pen; station.pen = null;
        if (pen == null) return;
        pen.stations.remove(station);
        if (pen.stations.isEmpty()) retire(pen); else queue(pen);
        if (stations.isEmpty()) LEVELS.remove(level, this);
        AnimalWork.clearCache();
    }
    private void retire(Pen pen) {
        pen.milkCow = null; publishMilk(pen, false);
        pen.removed = true; pens.remove(pen); work.remove(pen);
        for (long cell : pen.cells) membership.remove(cell, pen);
        unwatch(pen);
    }
    private void unwatch(Pen pen) {
        for (long cell : pen.watched) { var set = watches.get(cell); if (set != null) { set.remove(pen); if (set.isEmpty()) watches.remove(cell); } }
        for (long chunk : pen.watchedChunks) {
            var set = chunkWatches.get(chunk);
            if (set != null) { set.remove(pen); if (set.isEmpty()) chunkWatches.remove(chunk); }
        }
        pen.watchedChunks.clear();
    }
    void watch(Pen pen, long pos) {
        if (!pen.watched.add(pos)) return;
        watches.computeIfAbsent(pos, key -> new HashSet<>()).add(pen);
        long chunk = ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
        if (pen.watchedChunks.add(chunk)) chunkWatches.computeIfAbsent(chunk, key -> new HashSet<>()).add(pen);
    }
    private void queue(Pen pen) {
        if (pen.removed) return;
        pen.generation++; pen.scan = null; pen.status = 0; pen.population = null;
        pen.milkCow = null; publishMilk(pen, false);
        if (!pen.queued) { pen.queued = true; work.add(pen); }
    }
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        var manager = LEVELS.get(level); if (manager == null) return;
        // Explicit retries bypass geometry comparison, but milk publication cannot queue itself.
        if (!JobConfig.stationsEnabled() || pos.equals(manager.milkVisualUpdate)) return;
        var affected = manager.watches.get(pos.asLong());
        if (affected != null) for (Pen pen : affected) manager.queue(pen);
    }
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before == after) return;
        var manager = LEVELS.get(level); if (manager == null || !JobConfig.stationsEnabled() || pos.equals(manager.milkVisualUpdate)) return;
        var affected = manager.watches.get(pos.asLong());
        // Compare only watched cells, once per edit rather than once per shared pen.
        if (affected == null || PenScan.sameGeometry(level, pos, before, after)) return;
        for (Pen pen : affected) manager.queue(pen);
    }
    public void configure(StationBlockEntity station, StationSettings settings) {
        if (station.pen == null || !settings.valid()) return;
        Pen pen = station.pen; pen.settings = settings; pen.population = null; pen.nextPopulation = 0;
        pen.policyOrder=lastPolicyOrder=Math.max(System.currentTimeMillis(),lastPolicyOrder+1);
        for (var member : pen.stations) { member.settings = settings; member.policyOrder=pen.policyOrder; member.revision++; if(!member.isRemoved())member.setChanged(); }
    }
    /** Explicit selection changes detach only this station; shared policies remain intact. */
    public void detect(StationBlockEntity station, int mode) {
        if (!JobConfig.stationsEnabled() || mode < 0 || mode > 5 || station.isRemoved()) return;
        if (station.pen == null) add(station);
        Pen pen = station.pen;
        if (pen.status == 0) return;
        if (pen.stations.size() > 1) {
            pen.stations.remove(station);
            Pen next = new Pen(); next.settings = pen.settings; next.policyOrder = pen.policyOrder;
            next.stations.add(station); pens.add(next); station.pen = next;
            pen = next;
        }
        station.detectionMode = mode; station.selectedSide = 0; station.selectedSeed = null;
        station.revision++; station.setChanged(); queue(pen);
    }
    public Pen penAt(Animal animal) {
        BlockPos feet=animal.blockPosition(); Pen exact=penAt(feet); if(exact!=null)return exact;
        // Thin fences leave usable space inside the boundary block. Only consult
        // neighboring cells at watched boundaries, with the animal center on that side.
        boolean watched=false;
        for (int dy = -1; dy <= 1; dy++) if (watches.containsKey(BlockPos.asLong(feet.getX(), feet.getY() + dy, feet.getZ()))) {watched=true;break;}
        if(!watched)return null;
        double x=animal.getX()-feet.getX(),z=animal.getZ()-feet.getZ();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0 || dx<0 && x>=.5 || dx>0 && x<=.5 || dz<0 && z>=.5 || dz>0 && z<=.5)continue;
            Pen near=penAt(feet.getX()+dx,feet.getY(),feet.getZ()+dz);if(near!=null)return near;
        }
        return null;
    }
    public Pen penAt(BlockPos feet) {
        return penAt(feet.getX(), feet.getY(), feet.getZ());
    }
    private Pen penAt(int x, int y, int z) {
        Pen pen = membership.get(BlockPos.asLong(x, y, z));
        if (pen != null && !pen.removed) return pen;
        pen = membership.get(BlockPos.asLong(x, y + 1, z));
        if (pen != null && !pen.removed) return pen;
        pen = membership.get(BlockPos.asLong(x, y - 1, z));
        if (pen != null && !pen.removed) return pen;
        return null;
    }
    public static boolean managed(ServerLevel level, Animal animal) { var manager = LEVELS.get(level); return manager != null && JobConfig.stationsEnabled() && manager.penAt(animal) != null; }
    public static Optional<Animal> findWork(EntityMaid maid, boolean slaughter) { var manager=LEVELS.get((ServerLevel)maid.level());return manager==null?Optional.empty():manager.find(maid,slaughter); }
    public static boolean allowWork(EntityMaid maid, Animal animal, boolean slaughter) { var manager=LEVELS.get((ServerLevel)maid.level());return manager!=null && manager.allow(maid,animal,slaughter); }
    public static boolean inWorkRange(EntityMaid maid, Animal animal) { var manager=LEVELS.get((ServerLevel)maid.level());return manager!=null && manager.inRange(maid,animal); }
    private void commit(Pen pen, PenScan scan) {
        if (pen.removed || pen.generation != scan.generation) return;
        scans++;
        if (scan.failure != 0) { pen.status = scan.failure; return; }
        var peers = new HashSet<Pen>(); peers.add(pen);
        for (var station : stations.values()) {
            if (station.selectedSeed != null && scan.cells.contains(station.selectedSeed.asLong()) && station.pen != null) peers.add(station.pen);
        }
        // Joining a new station must not replace an established policy with its defaults.
        var established = peers.stream().filter(p -> !p.cells.isEmpty()).toList();
        StationBlockEntity canonical = (established.isEmpty() ? peers.stream() : established.stream())
                .flatMap(p -> p.stations.stream()).min(Comparator.comparing(StationBlockEntity::getBlockPos)).orElseThrow();
        var latest=peers.stream().max(Comparator.comparingLong(p->p.policyOrder)).orElseThrow();
        StationSettings settings = latest.policyOrder>0?latest.settings:canonical.settings;
        long policyOrder=latest.policyOrder;
        var members = new HashSet<StationBlockEntity>(); peers.forEach(p -> members.addAll(p.stations));
        var detached = new HashSet<StationBlockEntity>();
        members.removeIf(station -> { if (station.selectedSeed != null && scan.cells.contains(station.selectedSeed.asLong())) return false; detached.add(station); return true; });
        // Retain exclusion for the disconnected side until its own scan is verified.
        var remainingCells = new HashSet<Long>(); var remainingWatches = new HashSet<Long>();
        if (!detached.isEmpty()) for (var peer : peers) { remainingCells.addAll(peer.cells); remainingWatches.addAll(peer.watched); }
        remainingCells.removeAll(scan.cells);
        for (Pen peer : peers) if (peer != pen) retire(peer);
        for (long cell : pen.cells) membership.remove(cell, pen);
        unwatch(pen);
        pen.watched = new HashSet<>(); pen.cells = scan.cells; pen.stations.clear(); pen.stations.addAll(members); pen.settings = settings; pen.policyOrder=policyOrder;
        for (long cell : scan.watched) watch(pen, cell);
        // Outside and fence-mounted stations are members through their selected floor seed.
        for (var member : members) {
            watch(pen, member.getBlockPos().asLong());
            if (member.selectedSeed != null) watch(pen, member.getBlockPos().relative(member.getBlockState().getValue(StationBlock.FACING).getOpposite()).asLong());
        }
        for (long cell : pen.cells) membership.put(cell, pen);
        int minX=Integer.MAX_VALUE, minY=Integer.MAX_VALUE, minZ=Integer.MAX_VALUE, maxX=Integer.MIN_VALUE, maxY=Integer.MIN_VALUE, maxZ=Integer.MIN_VALUE;
        for (long cell : pen.cells) { var p = BlockPos.of(cell); minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ()); maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ()); }
        // Include the inner halves of border blocks, then filter by exact membership.
        pen.bounds = new AABB(minX - .5, minY - 1, minZ - .5, maxX + 1.5, maxY + 2, maxZ + 1.5);
        pen.status = 1; pen.nextPopulation = level.getGameTime() + Math.floorMod(pen.anchor().asLong(), 40);
        long[] cells = pen.cells.stream().mapToLong(Long::longValue).sorted().toArray(), boundary = pen.watched.stream().mapToLong(Long::longValue).sorted().toArray();
        for (var member : members) { member.pen = pen; member.settings = settings; member.policyOrder=policyOrder; member.savedCells = cells; member.savedBoundary = boundary; member.revision++; if(!member.isRemoved())member.setChanged(); }
        if (!detached.isEmpty()) {
            Pen pending = new Pen(); pending.settings = settings; pending.policyOrder=policyOrder; pending.stations.addAll(detached); pending.cells = remainingCells;
            pens.add(pending);
            for (long cell : remainingCells) membership.put(cell, pending);
            for (long watched : remainingWatches) watch(pending, watched);
            long[] retained = remainingCells.stream().mapToLong(Long::longValue).sorted().toArray(), retainedBoundary = remainingWatches.stream().mapToLong(Long::longValue).sorted().toArray();
            for (var member : detached) { member.pen = pending; member.settings = settings; member.policyOrder=policyOrder; member.savedCells = retained; member.savedBoundary = retainedBoundary; member.revision++; if(!member.isRemoved())member.setChanged(); }
            queue(pending);
        }
        AnimalWork.clearCache();
    }
    private boolean oneNode() {
        Pen pen = work.poll(); if (pen == null) return false;
        if (pen.removed) return true;
        if (pen.scan == null) pen.scan = new PenSelection(this, pen);
        PenSelection selection = pen.scan;
        nodes++;
        if (selection.step()) {
            pen.queued = false; pen.scan = null;
            PenScan scan = selection.result();
            if (scan != null) commit(pen, scan);
        } else work.add(pen);
        return true;
    }
    /** Shared menu telemetry derives from the existing staggered snapshot, never a new scan. */
    public List<StationAnimals.Row> animals(StationBlockEntity station) {
        Pen pen = station.pen;
        if (pen == null || !pen.verified() || pen.population == null || !JobConfig.stationsEnabled()) return List.of();
        if (pen.surveyedVersion != pen.populationVersion) {
            pen.animalRows = StationAnimals.collect(pen.population); pen.surveyedVersion = pen.populationVersion;
        }
        return pen.animalRows;
    }
    private AnimalWork.Population query(Pen pen) {
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, pen.bounds, a -> !(a instanceof EntityMaid) && a.isAlive() && penAt(a) == pen);
        pen.population = snapshot(pen, animals, true); pen.populationVersion++;
        Map<EntityType<?>, Integer> adults = pen.population.adults();
        populationQueries++; pen.nextPopulation = level.getGameTime() + 40;
        pen.milkQueryTick = level.getGameTime();
        pen.status = animals.stream().anyMatch(a -> !a.isBaby() && pen.excessive(pen.population, a) && (!pen.settings.protectPair() || adults.getOrDefault(a.getType(),0)>2)) ? 4
                : animals.stream().anyMatch(a -> a.getAge()==0 && a.canFallInLove() && pen.breedAllowed(pen.population,a)) ? 5 : 1;
        publishMilk(pen, pen.milkCow != null);
        return pen.population;
    }
    /** Positive collections check one live cow; missing sources share at most one query per pen/tick. */
    public boolean hasMilk(StationBlockEntity station) {
        Pen pen = station.pen;
        if (pen == null || pen.removed) return false;
        if (!JobConfig.milkEnabled() || !pen.verified()) { publishMilk(pen, false); return false; }
        if (milkCowValid(pen, pen.milkCow)) return true;
        if (pen.milkQueryTick != level.getGameTime()) query(pen);
        else {
            pen.milkCow = null;
            if (pen.population != null) for (Animal animal : pen.population.animals()) {
                if (animal instanceof Cow cow && milkCowValid(pen, cow)) { pen.milkCow = cow; break; }
            }
            publishMilk(pen, pen.milkCow != null);
        }
        return pen.milkCow != null;
    }
    private boolean milkCowValid(Pen pen, Cow cow) {
        return cow != null && cow.isAlive() && !cow.isBaby() && cow.level() == level && penAt(cow) == pen;
    }
    private void publishMilk(Pen pen, boolean available) {
        for (var station : pen.stations) {
            BlockPos pos = station.getBlockPos();
            if (station.isRemoved() || !level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!StationContent.isStation(state) || state.getValue(StationBlock.HAS_MILK) == available) continue;
            milkVisualUpdate = pos;
            try { level.setBlock(pos, state.setValue(StationBlock.HAS_MILK, available), Block.UPDATE_CLIENTS); }
            finally { milkVisualUpdate = null; }
        }
    }
    public static void configurationChanged() { configurationChanged(false); }
    public static void configurationChanged(boolean revalidate) {
        for (var manager : LEVELS.values()) for (var pen : manager.pens) {
            pen.milkCow = null; pen.milkQueryTick = -1; pen.nextPopulation = 0;
            if (revalidate) manager.queue(pen); else manager.publishMilk(pen, false);
        }
    }
    /** Reuse the existing population pass; quotas depend on species/sizes, never current head counts. */
    private AnimalWork.Population snapshot(Pen pen, List<Animal> animals, boolean updateMilk) {
        var adults = new HashMap<EntityType<?>, Integer>();
        boolean weighted = !pen.settings.countMode();
        Map<EntityType<?>, Double> weights = weighted ? new HashMap<>() : Map.of();
        Map<EntityType<?>, Long> sizes = weighted ? new HashMap<>() : Map.of();
        if (updateMilk) pen.milkCow = null;
        boolean milkEnabled = updateMilk && JobConfig.milkEnabled();
        double weight = 0;
        for (Animal animal : animals) {
            double size = pen.settings.weight(animal);
            weight += size;
            if (weighted) {
                weights.merge(animal.getType(), size, Double::sum);
                long adultSize = Math.round((animal.isBaby() ? pen.settings.adultWeight(animal) : size) * 4);
                sizes.merge(animal.getType(), adultSize, Math::min);
            }
            if (!animal.isBaby()) {
                adults.merge(animal.getType(), 1, Integer::sum);
                if (milkEnabled && pen.milkCow == null && animal instanceof Cow cow) pen.milkCow = cow;
            }
        }
        Map<EntityType<?>, AnimalWork.SpeciesShare> shares = Map.of();
        if (weighted) {
            var limits = pen.capacities(sizes);
            var values = new HashMap<EntityType<?>, AnimalWork.SpeciesShare>();
            weights.forEach((type, used) -> values.put(type, new AnimalWork.SpeciesShare(used, limits.get(type) / 4.0)));
            shares = Map.copyOf(values);
        }
        return new AnimalWork.Population(animals, adults, weight, pen.bounds, shares);
    }
    private AnimalWork.Population population(Pen pen, boolean fresh) {
        if (!pen.verified()) return null;
        if (pen.population == null || fresh && pen.freshTick != level.getGameTime()) {
            query(pen);
            if (fresh) pen.freshTick = level.getGameTime();
            return pen.population; // The query already counted these live references on this server thread.
        }
        // Recount live references after another maid's hit; queries are shared within the tick.
        if (fresh) {
            var live = pen.population.animals().stream().filter(a -> a.isAlive() && penAt(a)==pen).toList();
            return snapshot(pen, live, false);
        }
        return pen.population;
    }
    private static boolean ready(EntityMaid maid, Animal animal, boolean slaughter) {
        return animal.isAlive() && animal.getAge()==0 && animal.canFallInLove() && maid.isWithinRestriction(animal.blockPosition())
                && (slaughter && JobConfig.butcherWithoutFood() || ItemsUtil.isStackIn(maid.getAvailableInv(false), animal::isFood));
    }
    public Optional<Animal> find(EntityMaid maid, boolean slaughter) {
        if (!JobConfig.stationsEnabled() || pens.isEmpty()) return Optional.empty();
        record Nearby(Pen pen, double distance) {}
        var nearby = new ArrayList<Nearby>();
        for (Pen pen : pens) {
            if (!pen.verified() || pen.population == null || pen.population.animals().isEmpty()) continue;
            boolean inRange = false;
            for (var station : pen.stations) {
                BlockPos pos = station.getBlockPos();
                if (maid.isWithinRestriction(pos) && maid.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)<=4096) {
                    inRange = true;
                    break;
                }
            }
            if (inRange) {
                BlockPos anchor = pen.anchor();
                nearby.add(new Nearby(pen, maid.distanceToSqr(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5)));
            }
        }
        if (nearby.isEmpty()) return Optional.empty();
        // Preserve the previous stable tie order; retain only the nearest three animals.
        nearby.sort(Comparator.comparingDouble(Nearby::distance));
        Animal[] nearest = new Animal[3];
        double[] distances = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        for (var candidate : nearby) {
            Pen pen = candidate.pen();
            for (Animal animal : pen.population.animals()) {
                if (slaughter ? !pen.excessive(pen.population, animal)
                        || pen.settings.protectPair() && pen.population.adults().getOrDefault(animal.getType(),0)<=2
                        : !pen.breedAllowed(pen.population, animal)) continue;
                double distance = maid.distanceToSqr(animal);
                if (distance >= distances[2] || penAt(animal) != pen || !ready(maid, animal, slaughter)) continue;
                int slot = 2;
                while (slot > 0 && distance < distances[slot-1]) {
                    nearest[slot] = nearest[slot-1]; distances[slot] = distances[slot-1]; slot--;
                }
                nearest[slot] = animal; distances[slot] = distance;
            }
        }
        for (Animal animal : nearest) if (animal != null && maid.canPathReach(animal)) return Optional.of(animal);
        return Optional.empty();
    }
    public boolean allow(EntityMaid maid, Animal animal, boolean slaughter) {
        if (!JobConfig.stationsEnabled()) return false;
        Pen pen = penAt(animal);
        if (pen == null || !pen.verified() || penAt(maid) != pen || !ready(maid,animal,slaughter)) return false;
        if (!slaughter && !pen.settings.limitBreeding()) return true;
        var population = population(pen, slaughter || pen.settings.limitBreeding());
        if (population == null) return false;
        return slaughter ? pen.excessive(population,animal) && (!pen.settings.protectPair() || population.adults().getOrDefault(animal.getType(),0)>2) : pen.breedAllowed(population,animal);
    }
    public boolean inRange(EntityMaid maid, Animal animal) {
        if (!JobConfig.stationsEnabled()) return false;
        Pen pen = penAt(animal);
        return pen != null && pen.verified() && maid.isWithinRestriction(animal.blockPosition());
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) { StationMembership.install(StationManager::managed); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }
    @SubscribeEvent public static void chunk(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var manager = LEVELS.get(level);
        if (manager == null) return;
        var affected = manager.chunkWatches.get(event.getChunk().getPos().toLong());
        if (affected != null) for (Pen pen : affected) if (pen.status == 3) manager.queue(pen);
    }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var manager = LEVELS.get(level);
        if (manager == null) return;
        var affected = manager.chunkWatches.get(event.getChunk().getPos().toLong());
        // A boundary can unload while every station stays loaded. Keep exclusion, but stop actions.
        if (affected != null) for (Pen pen : affected) if (pen.status != 3) manager.suspend(pen);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (LEVELS.isEmpty() || !JobConfig.stationsEnabled()) return;
        long start = System.nanoTime(), deadline = start + JobConfig.maintenanceMicros() * 1000L;
        int remaining = JobConfig.scanNodes();
        var managers = List.copyOf(LEVELS.values());
        boolean progress = true;
        while (remaining > 0 && progress && System.nanoTime() < deadline) {
            progress = false;
            for (var manager : managers) {
                if (remaining <= 0 || System.nanoTime() >= deadline) break;
                if (manager.oneNode()) { remaining--; progress = true; }
            }
        }
        // A due pen is one indivisible entity query; no scan/query occurs for every maid.
        outer: for (var manager : managers) for (var pen : manager.pens) {
            if (System.nanoTime() >= deadline) break outer;
            if (pen.verified() && manager.level.getGameTime() >= pen.nextPopulation) manager.query(pen);
        }
        long elapsed=System.nanoTime()-start; maintenanceNanos+=elapsed; worstNanos=Math.max(worstNanos,elapsed);
    }
}
