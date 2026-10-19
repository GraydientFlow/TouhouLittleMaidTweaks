// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedAnimal;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import io.github.graydients.tlmtweaks.station.StationManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;

/** Local policy only. Cached selection is advisory; destructive actions re-query. */
public final class AnimalWork {
    public static final int REFRESH_TICKS = 40;
    private static final ResourceLocation FARM_AND_COOK = ResourceLocation.fromNamespaceAndPath("maidsoulkitchen", "feed_animal_t");
    private static final Map<EntityMaid, Cached> CACHE = new WeakHashMap<>();
    private static long queries, queryNanos;
    private AnimalWork() {}
    private static final class Cached {
        Population population;
        long expires, nextSlaughter;
        AnimalSettings settings;
    }
    public record SpeciesShare(double weight, double limit) {}
    public record Population(List<Animal> animals, Map<EntityType<?>, Integer> adults, double weight, AABB bounds,
                             Map<EntityType<?>, SpeciesShare> species) {
        public boolean excess(AnimalSettings settings) {
            if (!settings.countMode()) return weight > settings.density() * settings.area();
            for (int count : adults.values()) if (count > settings.keepCount()) return true;
            return false;
        }
        public boolean excess(AnimalSettings settings, EntityType<?> type) {
            return settings.countMode() ? adults.getOrDefault(type, 0) > settings.keepCount() : excess(settings);
        }
        public boolean room(AnimalSettings settings, EntityType<?> type) {
            return settings.countMode() ? adults.getOrDefault(type, 0) < settings.keepCount() : weight < settings.density() * settings.area();
        }
    }
    public record Metrics(long queries, long queryNanos) {}
    public static Metrics metrics() { return new Metrics(queries, queryNanos); }
    public static void clearCache() { CACHE.clear(); }
    public static void invalidate(EntityMaid maid) { CACHE.remove(maid); }
    public static boolean supports(IMaidTask task) { return task.getUid().equals(TaskFeedAnimal.UID) || task.getUid().equals(FARM_AND_COOK); }
    public static boolean active(EntityMaid maid) { return JobConfig.enabled() && supports(maid.getTask()); }
    public static int sizeWeight(Animal animal) {
        AABB box = animal.getBoundingBox();
        return (int) Math.max(1, 2 * Math.round(box.getXsize() * box.getYsize() * box.getZsize() * 1.5));
    }
    /** Footprint weight in the same four-points-per-cow units as legacy volume density. */
    public static int footprintWeight(Animal animal) {
        var box = animal.getBoundingBox();
        return (int) Math.max(1, 2 * Math.round(box.getXsize() * box.getZsize() * 1.5)) * 2;
    }
    private static AABB bounds(EntityMaid maid, AnimalSettings settings) {
        int x = maid.getBlockX() - settings.searchWidth() / 2;
        int z = maid.getBlockZ() - settings.searchWidth() / 2;
        double vertical = Math.max(2, maid.getRestrictRadius());
        return new AABB(x, maid.getY() - vertical, z, x + settings.searchWidth(), maid.getY() + vertical, z + settings.searchWidth());
    }
    private static boolean included(EntityMaid maid, Animal animal, AABB bounds) {
        return !(animal instanceof EntityMaid) && animal.isAlive() && animal.level() == maid.level() && bounds.contains(animal.position())
                && maid.isWithinRestriction(animal.blockPosition())
                && !StationMembership.managed((ServerLevel) maid.level(), animal);
    }
    public static Population current(EntityMaid maid) {
        long started = System.nanoTime();
        AnimalSettings settings = AnimalSettings.get(maid);
        AABB bounds = bounds(maid, settings);
        List<Animal> animals = ((ServerLevel) maid.level()).getEntitiesOfClass(Animal.class, bounds, animal -> included(maid, animal, bounds));
        Map<EntityType<?>, Integer> adults = new HashMap<>();
        double weight = 0;
        for (Animal animal : animals) {
            weight += settings.weight(animal);
            if (!animal.isBaby()) adults.merge(animal.getType(), 1, Integer::sum);
        }
        queries++;
        queryNanos += System.nanoTime() - started;
        return new Population(animals, adults, weight, bounds, Map.of());
    }
    private static Population cached(EntityMaid maid) {
        Cached state = CACHE.computeIfAbsent(maid, key -> new Cached());
        long now = maid.level().getGameTime();
        AnimalSettings settings = AnimalSettings.get(maid);
        if (state.population == null || now >= state.expires || !settings.equals(state.settings)) {
            state.population = current(maid);
            state.settings = settings;
            state.expires = now + REFRESH_TICKS;
        }
        return state.population;
    }
    private static NearestVisibleLivingEntities visible(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
    private static boolean breedReady(EntityMaid maid, Animal animal, AABB bounds) {
        return included(maid, animal, bounds) && animal.getAge() == 0 && animal.canFallInLove()
                && ItemsUtil.isStackIn(maid.getAvailableInv(false), animal::isFood);
    }
    private static boolean slaughterReady(EntityMaid maid, Animal animal, Population population) {
        AnimalSettings settings = AnimalSettings.get(maid);
        return included(maid, animal, population.bounds()) && animal.getAge() == 0 && animal.canFallInLove() && population.excess(settings, animal.getType())
                && (!settings.protectPair() || population.adults().getOrDefault(animal.getType(), 0) > 2)
                && (JobConfig.butcherWithoutFood() || ItemsUtil.isStackIn(maid.getAvailableInv(false), animal::isFood));
    }
    public static Optional<? extends LivingEntity> findSlaughter(EntityMaid maid) {
        Cached state = CACHE.computeIfAbsent(maid, key -> new Cached());
        long now = maid.level().getGameTime();
        if (now < state.nextSlaughter) return Optional.empty();
        state.nextSlaughter = now + 20 + Math.floorMod(maid.getId(), 20);
        var station = StationManager.findWork(maid, true);
        if (station.isPresent()) return station;
        Population population = cached(maid);
        if (!population.excess(AnimalSettings.get(maid))) return Optional.empty();
        return visible(maid).find(entity -> entity instanceof Animal animal && slaughterReady(maid, animal, population))
                .filter(maid::canPathReach).findFirst();
    }
    public static Optional<Animal> findBreeding(EntityMaid maid) {
        var station = StationManager.findWork(maid, false);
        if (station.isPresent()) return station;
        AnimalSettings settings = AnimalSettings.get(maid);
        AABB bounds = bounds(maid, settings);
        Population population = settings.limitBreeding() ? cached(maid) : null;
        if (population != null && !settings.countMode() && population.weight() >= settings.density() * settings.area()) return Optional.empty();
        return visible(maid).find(entity -> entity instanceof Animal animal && breedReady(maid, animal, bounds)
                && (population == null || population.room(settings, animal.getType())))
                .filter(maid::canPathReach).map(entity -> (Animal) entity).findFirst();
    }
    public static boolean allowFeedNow(EntityMaid maid, Animal animal) {
        if (StationMembership.managed((ServerLevel) maid.level(), animal)) return StationManager.allowWork(maid, animal, false);
        AnimalSettings settings = AnimalSettings.get(maid);
        return breedReady(maid, animal, bounds(maid, settings)) && (!settings.limitBreeding()
                || current(maid).room(settings, animal.getType()));
    }
    public static boolean targetInRange(EntityMaid maid, LivingEntity target) {
        return target instanceof Animal animal && (StationMembership.managed((ServerLevel) maid.level(), animal)
                ? StationManager.inWorkRange(maid, animal) : included(maid, animal, bounds(maid, AnimalSettings.get(maid))));
    }
    public static boolean allowHitNow(EntityMaid maid, Animal animal) {
        return StationMembership.managed((ServerLevel) maid.level(), animal) ? StationManager.allowWork(maid, animal, true) : slaughterReady(maid, animal, current(maid));
    }
}
