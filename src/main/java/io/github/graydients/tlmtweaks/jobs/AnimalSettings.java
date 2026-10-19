// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;

/** Unfenced maid policy; legacy volume density is also cows per four search blocks. */
public record AnimalSettings(int searchWidth, double density, boolean limitBreeding, boolean protectPair,
                             int method, int keepCount) {
    public static final int VOLUME = 0, COUNT = 1, FOOTPRINT = 2;
    public AnimalSettings(int searchWidth, double density, boolean limitBreeding, boolean protectPair) {
        this(searchWidth, density, limitBreeding, protectPair, VOLUME, 10);
    }
    public static final Codec<AnimalSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(2, 64).fieldOf("search_width").forGetter(AnimalSettings::searchWidth),
            Codec.doubleRange(0, 64).fieldOf("density").forGetter(AnimalSettings::density),
            Codec.BOOL.fieldOf("limit_breeding").forGetter(AnimalSettings::limitBreeding),
            Codec.BOOL.fieldOf("protect_pair").forGetter(AnimalSettings::protectPair),
            Codec.intRange(VOLUME, FOOTPRINT).optionalFieldOf("method", VOLUME).forGetter(AnimalSettings::method),
            Codec.intRange(0, 10000).optionalFieldOf("keep_count", 10).forGetter(AnimalSettings::keepCount)
    ).apply(instance, AnimalSettings::new));
    public static final TaskDataKey<AnimalSettings> KEY = new TaskDataRegister().register(
            ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID, "animal_settings"), CODEC);
    public static void initialize() {} // Register on both sides before maid data loads.
    public boolean valid() {
        return searchWidth >= 2 && searchWidth <= 64 && Double.isFinite(density) && density >= 0 && density <= 64
                && method >= VOLUME && method <= FOOTPRINT && keepCount >= 0 && keepCount <= 10000;
    }
    public boolean countMode() { return method == COUNT; }
    public double area() { return (double) searchWidth * searchWidth; }
    public double weight(Animal animal) {
        return countMode() ? animal.isBaby() ? 0 : 1
                : method == FOOTPRINT ? AnimalWork.footprintWeight(animal) : AnimalWork.sizeWeight(animal);
    }
    public static AnimalSettings get(EntityMaid maid) {
        AnimalSettings saved = maid.getData(KEY);
        return saved == null ? JobConfig.defaults() : saved;
    }
}
