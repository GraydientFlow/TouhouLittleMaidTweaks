// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.station.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StationScreen extends AbstractContainerScreen<StationMenu> {
    private final StationForm form;
    public StationScreen(StationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = StationForm.WIDTH; imageHeight = StationForm.HEIGHT;
        form = new StationForm(menu.settings(), settings -> PacketDistributor.sendToServer(new StationProtocol.Configure(menu.pos, settings)),
                selection -> { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selection); }, () -> super.onClose());
    }
    @Override protected void init() { super.init(); form.init(font, leftPos, topPos, widget -> addRenderableWidget(widget)); }
    @Override protected void containerTick() {
        form.receive(menu.settings(), menu.detectionMode(), menu.selectedSide(), menu.area(), menu.population(), menu.status(), menu.animals());
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) { form.render(g, title, mouseX, mouseY); }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial); renderTooltip(g, mouseX, mouseY); form.renderTooltip(g, mouseX, mouseY);
    }
}
