// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.permissions;

import com.github.tartaricacid.touhoulittlemaid.compat.curios.CuriosCompat;
import com.github.tartaricacid.touhoulittlemaid.compat.curios.menu.CuriosContainer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.config.MaidConfigContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Local interaction checks; never changes actual ownership or grants operator access. */
@EventBusSubscriber(modid = MaidTweaks.ID)
public final class PlayerPermissions {
    private PlayerPermissions() {}

    private static boolean nearby(Player player, EntityMaid maid) {
        return player != null && !(player instanceof FakePlayer) && maid != null && maid.isTame()
                && maid.isAlive() && player.level() == maid.level() && player.canInteractWithEntity(maid, 4.0);
    }

    public static boolean canView(Player player, EntityMaid maid) {
        return maid != null && player != null && (maid.isOwnedBy(player)
                || PermissionConfig.settings().view() && nearby(player, maid) && !maid.isSleeping());
    }

    public static boolean canMoveItems(Player player, EntityMaid maid) {
        return maid != null && player != null && (maid.isOwnedBy(player)
                || PermissionConfig.settings().moveItems() && canView(player, maid));
    }

    public static boolean canChangeSettings(Player player, EntityMaid maid) {
        return maid != null && player != null && (maid.isOwnedBy(player)
                || PermissionConfig.settings().changeSettings() && canView(player, maid));
    }

    public static boolean canCarry(Player player, EntityMaid maid) {
        return maid != null && player != null && (maid.isOwnedBy(player)
                || PermissionConfig.settings().carry() && nearby(player, maid));
    }

    public static boolean sharedMenu(AbstractMaidContainer menu) {
        return menu instanceof MaidMainContainer || menu instanceof MaidConfigContainer || menu instanceof TaskConfigContainer;
    }

    /** Curios buttons 0/1 change the viewed page, without editing items or settings. */
    public static boolean inventoryPageButton(AbstractMaidContainer menu, int button) {
        return (button == 0 || button == 1) && CuriosCompat.isLoadedOrEnable() && menu instanceof CuriosContainer;
    }

    public static boolean canUseMenu(Player player, AbstractMaidContainer menu) {
        return sharedMenu(menu) && canView(player, menu.getMaid());
    }

    public static boolean canOpenTab(Player player, EntityMaid maid, int tab) {
        return tab >= 0 && tab <= 4 && canView(player, maid);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void openSharedMenu(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof EntityMaid maid)
                || maid.isOwnedBy(player) || !canView(player, maid)) return;
        if (maid.openMaidGui(player)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
