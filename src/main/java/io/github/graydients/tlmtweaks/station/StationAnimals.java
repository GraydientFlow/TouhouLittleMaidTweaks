// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import io.github.graydients.tlmtweaks.jobs.AnimalWork;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Menu-only statistics from an existing population snapshot; never queries the world. */
public final class StationAnimals {
    private StationAnimals() {}
    public record Row(ResourceLocation type, int adults, int babies, int ready,
                      long volume, long footprint, long adultVolume, long adultFootprint, long babyVolume, long babyFootprint) {
        public int total() { return adults + babies; }
        public long used(int method) { return method == StationSettings.FOOTPRINT ? footprint : volume; }
        public long babyUsed(int method) { return method == StationSettings.FOOTPRINT ? babyFootprint : babyVolume; }
        public long unit(int method) { return method == StationSettings.FOOTPRINT ? adultFootprint : adultVolume; }
    }
    private static final StationSettings VOLUME = new StationSettings(StationSettings.VOLUME, 0, 0, false, false);
    private static final StationSettings FOOTPRINT = new StationSettings(StationSettings.FOOTPRINT, 0, 0, false, false);
    public static List<Row> collect(AnimalWork.Population population) {
        if (population == null) return List.of();
        var values = new HashMap<ResourceLocation, long[]>();
        for (var animal : population.animals()) {
            if (!animal.isAlive()) continue;
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
            var v = values.computeIfAbsent(id, ignored -> new long[]{0, 0, 0, 0, 0, Long.MAX_VALUE, Long.MAX_VALUE, 0, 0});
            if (animal.isBaby()) v[1]++; else v[0]++;
            if (animal.getAge() == 0 && animal.canFallInLove()) v[2]++;
            long volume = Math.round(VOLUME.weight(animal) * 4), footprint = Math.round(FOOTPRINT.weight(animal) * 4);
            v[3] += volume; v[4] += footprint;
            if (animal.isBaby()) { v[7] += volume; v[8] += footprint; }
            v[5] = Math.min(v[5], Math.round(VOLUME.adultWeight(animal) * 4));
            v[6] = Math.min(v[6], Math.round(FOOTPRINT.adultWeight(animal) * 4));
        }
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            long[] v = e.getValue();
            return new Row(e.getKey(), (int)v[0], (int)v[1], (int)v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
        }).toList();
    }
}
