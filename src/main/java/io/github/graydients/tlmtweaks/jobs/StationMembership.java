// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

/** Shared index excludes station livestock from both ordinary work paths. */
public final class StationMembership {
    @FunctionalInterface public interface Index { boolean contains(ServerLevel level, Animal animal); }
    private static final Index EMPTY = (level, animal) -> false;
    private static Index index = EMPTY;
    private StationMembership() {}
    public static boolean managed(ServerLevel level, Animal animal) { return index.contains(level, animal); }
    public static void install(Index replacement) {
        index = replacement;
        AnimalWork.clearCache();
    }
    public static void clear() { install(EMPTY); }
}
