// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-18.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import io.github.graydients.tlmtweaks.jobs.SeatWork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Recognize Create seats for schedule departures without a dependency or repeated registry lookup. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.seat.SeatEntity", remap = false)
public abstract class CreateSeatEntityMixin implements SeatWork.ScheduleSeat {}
