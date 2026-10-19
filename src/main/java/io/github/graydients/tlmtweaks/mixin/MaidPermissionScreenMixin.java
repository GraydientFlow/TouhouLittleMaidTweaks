// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.compat.curios.CuriosCompat;
import com.github.tartaricacid.touhoulittlemaid.compat.curios.client.CuriosButton;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.AttackTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.*;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractMaidContainerGui.class, remap = false)
public abstract class MaidPermissionScreenMixin<T extends AbstractMaidContainer> extends AbstractContainerScreen<T> {
    @Shadow private TouhouImageButton info, pageDown, pageUp, pageClose, taskSwitch, skin, ysmSkin, sound;
    @Unique private final Map<AbstractWidget, Boolean> graydients$originalActive = new IdentityHashMap<>();
    protected MaidPermissionScreenMixin(T menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Inject(method = "init", at = @At("HEAD"))
    private void graydients$resetWidgets(CallbackInfo callback) { graydients$originalActive.clear(); }

    @Inject(method = "init", at = @At("RETURN"))
    private void graydients$initialPermissions(CallbackInfo callback) { graydients$applyPermissions(); }

    @Inject(method = "render", at = @At("HEAD"))
    private void graydients$refreshPermissions(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo callback) {
        graydients$applyPermissions();
    }

    @Unique private void graydients$applyPermissions() {
        var maid = getMenu().getMaid();
        if (minecraft == null || minecraft.player == null || maid == null || maid.isOwnedBy(minecraft.player)) return;
        boolean view = PlayerPermissions.canView(minecraft.player, maid);
        boolean edit = PlayerPermissions.canChangeSettings(minecraft.player, maid);
        boolean items = PlayerPermissions.canMoveItems(minecraft.player, maid);
        for (var child : children()) if (child instanceof AbstractWidget widget) {
            boolean original = graydients$originalActive.computeIfAbsent(widget, key -> key.active);
            boolean navigation = (Object)this instanceof io.github.graydients.tlmtweaks.client.AnimalConfigScreen screen && screen.navigation(widget)
                    || widget instanceof MaidTabButton || widget instanceof MaidSideTabButton
                    || widget instanceof BaubleButton || CuriosCompat.isLoadedOrEnable() && widget instanceof CuriosButton
                    || widget == info || widget == taskSwitch || widget == pageDown || widget == pageUp || widget == pageClose;
            // Attack-list arrows only change the page being viewed.
            boolean attackPage = (Object)this instanceof AttackTaskConfigGui && widget instanceof TouhouImageButton
                    && widget != skin && widget != ysmSkin && widget != sound && widget != taskSwitch;
            boolean setting = widget instanceof MaidConfigButton || widget instanceof TaskButton
                    || widget instanceof ScheduleButton || widget instanceof TouhouStateSwitchButton
                    || widget == skin || widget == ysmSkin || widget == sound || widget instanceof MaidDownloadButton;
            boolean inventoryPage = PlayerPermissions.inventoryPageButton(getMenu(), 0)
                    && widget instanceof TouhouImageButton && !setting;
            boolean allowed = view && (navigation || attackPage || inventoryPage
                    || (setting || !(getMenu() instanceof MaidMainContainer) ? edit : items));
            widget.active = original && allowed;
        }
    }
}
