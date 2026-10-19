// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import io.github.graydients.tlmtweaks.jobs.AnimalWork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Animal;

/** Shared pen policy. Density is cow-equivalent allowance per verified floor cell. */
public record StationSettings(int method, double density, int keepCount, boolean limitBreeding, boolean protectPair) {
    public static final int VOLUME = 0, COUNT = 1, FOOTPRINT = 2;
    public static final int DENSITY_BLOCKS = 4;
    public static final double DEFAULT_DENSITY = 1.5;

    public StationSettings(boolean countMode, double density, int keepCount, boolean limitBreeding, boolean protectPair) {
        this(countMode ? COUNT : VOLUME, density, keepCount, limitBreeding, protectPair);
    }

    public static StationSettings defaults() { return new StationSettings(VOLUME, DEFAULT_DENSITY / DENSITY_BLOCKS, 10, false, true); }
    public boolean countMode() { return method == COUNT; }
    public boolean valid() {
        return method >= VOLUME && method <= FOOTPRINT && Double.isFinite(density) && density >= 0 && density <= 64
                && keepCount >= 0 && keepCount <= 10000;
    }

    /** Both population refreshes and final hit checks use the selected weight. */
    public double weight(Animal animal) {
        if (method != FOOTPRINT) return AnimalWork.sizeWeight(animal) / 4.0;
        return AnimalWork.footprintWeight(animal) / 4.0;
    }

    /** Reserve a whole adult's space even while this species is represented only by babies. */
    public double adultWeight(Animal animal) {
        if (!animal.isBaby()) return weight(animal);
        var box = animal.getBoundingBox();
        double ageScale = animal.getAgeScale();
        double width = box.getXsize() / ageScale, depth = box.getZsize() / ageScale, height = box.getYsize() / ageScale;
        return method == FOOTPRINT ? Math.max(1, 2 * Math.round(width * depth * 1.5)) / 2.0
                : Math.max(1, 2 * Math.round(width * depth * height * 1.5)) / 4.0;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        // Preserve existing save keys: old policies remain volume or per-type count.
        tag.putBoolean("count_mode", countMode()); tag.putBoolean("footprint_weight", method == FOOTPRINT);
        tag.putBoolean("cow_baseline", true);
        tag.putDouble("density", density); tag.putInt("keep_count", keepCount);
        tag.putBoolean("limit_breeding", limitBreeding); tag.putBoolean("protect_pair", protectPair);
        return tag;
    }

    public static StationSettings load(CompoundTag tag) {
        int method = tag.getBoolean("count_mode") ? COUNT : tag.getBoolean("footprint_weight") ? FOOTPRINT : VOLUME;
        double density = tag.getDouble("density");
        // Old volume limits used four points per cow; preserve their actual capacity.
        if (!tag.getBoolean("cow_baseline")) {
            if (!Double.isFinite(density) || density < 0 || density > 64) return defaults();
            density /= method == FOOTPRINT ? 2.0 : 4.0;
        }
        var value = new StationSettings(method, density, tag.getInt("keep_count"),
                tag.getBoolean("limit_breeding"), tag.getBoolean("protect_pair"));
        return value.valid() ? value : defaults();
    }
}
