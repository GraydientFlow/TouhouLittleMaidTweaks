// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.pickup;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitAttribute;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** One item-drop timestamp, shared by all maids and preserved through entity saves and merges. */
@EventBusSubscriber(modid = MaidTweaks.ID)
public final class PickupDelay {
    private static final String DROPPED_AT = MaidTweaks.ID + ":dropped_at";
    private PickupDelay() {}

    private static long now(ItemEntity item) { return item.level().getServer().overworld().getGameTime(); }

    private static long droppedAt(ItemEntity item) {
        var data = item.getPersistentData();
        if (!data.contains(DROPPED_AT, Tag.TAG_LONG)) data.putLong(DROPPED_AT, now(item) - Math.max(0, item.getAge()));
        return data.getLong(DROPPED_AT);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof ItemEntity item)) return;
        var data = item.getPersistentData();
        if (!data.contains(DROPPED_AT, Tag.TAG_LONG))
            data.putLong(DROPPED_AT, now(item) - (event.loadedFromDisk() ? Math.max(0, item.getAge()) : 0));
    }

    public static void merged(ItemEntity destination, ItemEntity source) {
        if (destination.level().isClientSide()) return;
        // Protect newly dropped items even when they join an older stack.
        destination.getPersistentData().putLong(DROPPED_AT, Math.max(droppedAt(destination), droppedAt(source)));
    }

    public static double immediateRange(EntityMaid maid, PickupSettings settings) {
        if (settings.immediateRange() >= 0) return settings.immediateRange();
        var attribute = maid.getAttribute(InitAttribute.MAID_PICKUP_RANGE);
        return attribute == null ? 0.5 : attribute.getValue();
    }

    public static boolean allowed(EntityMaid maid, ItemEntity item) {
        PickupSettings settings = PickupConfig.settings();
        if (maid.level().isClientSide() || settings.delaySeconds() == 0) return true;
        return allowed(item, immediateRange(maid, settings), maid.getBoundingBox(), now(item), settings.delayTicks());
    }

    private static boolean allowed(ItemEntity item, double range, AABB box, long now, long delay) {
        AABB itemBox = item.getBoundingBox();
        return box.intersects(itemBox.minX - range, itemBox.minY - range, itemBox.minZ - range,
                itemBox.maxX + range, itemBox.maxY + range, itemBox.maxZ + range)
                || now - droppedAt(item) >= delay;
    }

    /** Test the sensor batch before starting a path search, with one rules/reach/time snapshot. */
    public static boolean hasAllowedTarget(EntityMaid maid, List<Entity> targets) {
        PickupSettings settings = PickupConfig.settings();
        if (maid.level().isClientSide() || settings.delaySeconds() == 0) return true;
        if (targets.isEmpty()) return false;
        double range = immediateRange(maid, settings);
        AABB box = maid.getBoundingBox();
        long now = maid.level().getServer().overworld().getGameTime(), delay = settings.delayTicks();
        for (Entity entity : targets) {
            if (!(entity instanceof ItemEntity item) || allowed(item, range, box, now, delay)) return true;
        }
        return false;
    }

    /** Keep the sensor's original list when nothing is delayed; never mutate its cached contents. */
    public static List<Entity> filterTargets(EntityMaid maid, List<Entity> targets) {
        PickupSettings settings = PickupConfig.settings();
        if (targets.isEmpty() || maid.level().isClientSide() || settings.delaySeconds() == 0) return targets;
        double range = immediateRange(maid, settings);
        AABB box = maid.getBoundingBox();
        long now = maid.level().getServer().overworld().getGameTime();
        long delay = settings.delayTicks();
        List<Entity> filtered = null;
        for (int index = 0; index < targets.size(); index++) {
            Entity entity = targets.get(index);
            boolean allowed = !(entity instanceof ItemEntity item) || allowed(item, range, box, now, delay);
            if (!allowed && filtered == null) {
                filtered = new ArrayList<>(targets.size());
                for (int previous = 0; previous < index; previous++) filtered.add(targets.get(previous));
            } else if (allowed && filtered != null) filtered.add(entity);
        }
        return filtered == null ? targets : filtered;
    }

    public static List<Entity> nearby(Level level, EntityMaid maid, AABB originalBox, Predicate<? super Entity> predicate) {
        double range = PickupConfig.settings().immediateRange();
        if (range < 0) return level.getEntities(maid, originalBox, predicate);
        AABB itemBox = maid.getBoundingBox().inflate(range);
        return level.getEntities(maid, originalBox.minmax(itemBox), entity ->
                (entity instanceof ItemEntity ? itemBox : originalBox).intersects(entity.getBoundingBox()) && predicate.test(entity));
    }

    /** Withdraw only an item-directed walk if a merge or settings edit makes that item too young. */
    public static void cancelDelayedWalk(EntityMaid maid) {
        if (maid.level().isClientSide() || PickupConfig.settings().delaySeconds() == 0) return;
        var brain = maid.getBrain();
        var walk = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (walk.isEmpty() || !(walk.get().getTarget() instanceof EntityTracker tracker)
                || !(tracker.getEntity() instanceof ItemEntity item) || allowed(maid, item)) return;
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET).filter(target -> target instanceof EntityTracker look && look.getEntity() == item).isPresent())
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getNavigation().stop();
    }
}
