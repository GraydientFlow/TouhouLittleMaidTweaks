// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import io.github.graydients.tlmtweaks.jobs.*;
import io.github.graydients.tlmtweaks.permissions.PlayerPermissions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AnimalConfigScreen extends MaidTaskConfigGui<AnimalConfigMenu> {
    private AnimalWorkForm form;
    public AnimalConfigScreen(AnimalConfigMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void initAdditionWidgets() {
        if (form == null) form = new AnimalWorkForm(AnimalSettings.get(getMaid()), selected -> {
            if (PlayerPermissions.canChangeSettings(minecraft.player, getMaid())) PacketDistributor.sendToServer(new JobProtocol.SetAnimal(getMaid().getId(), selected));
            else super.onClose();
        }, () -> super.onClose());
        form.init(font, leftPos + 86, topPos + 34, widget -> addRenderableWidget(widget));
    }
    public boolean navigation(AbstractWidget widget) { return form != null && form.navigation(widget); }
    @Override protected void containerTick() { super.containerTick(); form.receive(AnimalSettings.get(getMaid())); }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        super.renderBg(g, partial, mouseX, mouseY);
        g.fill(leftPos + 83, topPos + 29, leftPos + 254, topPos + 165, 0xffb8b8b8);
    }
    @Override protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partial) { form.render(g); }
}
