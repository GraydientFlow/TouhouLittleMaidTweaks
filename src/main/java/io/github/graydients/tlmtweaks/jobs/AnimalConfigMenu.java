// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.jobs;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import io.github.graydients.tlmtweaks.MaidTweaks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AnimalConfigMenu extends TaskConfigContainer {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MaidTweaks.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<AnimalConfigMenu>> TYPE = MENUS.register("animal_job", () ->
            IMenuTypeExtension.create((window, inventory, data) -> new AnimalConfigMenu(window, inventory, data.readInt())));
    public AnimalConfigMenu(int window, Inventory inventory, int maidId) { super(TYPE.get(), window, inventory, maidId); }
    public static MenuProvider provider(EntityMaid maid) {
        return new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("gui.touhou_little_maid_tweaks.animals.title"); }
            @Override public AnimalConfigMenu createMenu(int window, Inventory inventory, Player player) {
                return new AnimalConfigMenu(window, inventory, maid.getId());
            }
            @Override public boolean shouldTriggerClientSideContainerClosingOnOpen() { return false; }
        };
    }
}
