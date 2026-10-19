// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.api.entity.fishing.IFishingType;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.fishing.FishingTypeManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Bounded, transient fishing-seat probes shared by selection and casting on one maid. */
public final class FishingSeats {
    public interface Context { FishingSeats graydients$fishingSeats(); }

    private record Probe(Vec3 origin, BlockPos water, long retryAt) {}
    private final LinkedHashMap<Integer, Probe> probes = new LinkedHashMap<>();
    private ItemStack rod = ItemStack.EMPTY;
    private BlockPos restriction;
    private float radius;
    private IFishingType type;

    /** Null means the seat cannot support the equipped rod inside native fishing reach. */
    public BlockPos water(EntityMaid maid, Entity seat) {
        var stack = maid.getMainHandItem();
        var center = maid.getRestrictCenter();
        if (type == null || !ItemStack.matches(rod, stack)
                || !center.equals(restriction) || radius != maid.getRestrictRadius()) {
            probes.clear(); rod = stack.copy(); restriction = center; radius = maid.getRestrictRadius();
            type = FishingTypeManager.getFishingType(stack);
        }
        if (!type.isFishingRod(stack)) return null;
        // Use the prospective passenger position, not the home's work-area center.
        var origin = seat.getPassengerRidingPosition(maid).subtract(maid.getVehicleAttachmentPoint(seat));
        var old = probes.get(seat.getId());
        long now = maid.level().getGameTime();
        if (old != null && old.origin.equals(origin)) {
            if (old.water != null && suitable(maid, stack, origin, old.water)) return old.water;
            if (old.water == null && now < old.retryAt) return null;
        }
        var water = find(maid, stack, origin);
        if (old == null && probes.size() == 16) probes.remove(probes.keySet().iterator().next());
        probes.put(seat.getId(), new Probe(origin, water, now + 100));
        return water;
    }

    private BlockPos find(EntityMaid maid, ItemStack stack, Vec3 origin) {
        var base = BlockPos.containing(origin);
        var pos = new BlockPos.MutableBlockPos();
        // TLM's fishing task uses range 6, vertical range 3 and starts one block below.
        for (int step = 0; step < 7; step++) {
            int dy = (step % 2 == 0 ? -step / 2 : (step + 1) / 2) - 1;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                pos.setWithOffset(base, dx, dy, dz);
                if (suitable(maid, stack, origin, pos)) return pos.immutable();
            }
        }
        return null;
    }

    private boolean suitable(EntityMaid maid, ItemStack stack, Vec3 origin, BlockPos pos) {
        double dx = origin.x - pos.getX(), dy = origin.y - pos.getY(), dz = origin.z - pos.getZ();
        return dx * dx + dy * dy + dz * dz < 36 && maid.isWithinRestriction(pos)
                && maid.level().hasChunkAt(pos) && type.suitableFishingHook(maid, maid.level(), stack, pos);
    }

    public static BlockPos waterAt(EntityMaid maid, Entity seat) {
        return ((Context) maid).graydients$fishingSeats().water(maid, seat);
    }
}
