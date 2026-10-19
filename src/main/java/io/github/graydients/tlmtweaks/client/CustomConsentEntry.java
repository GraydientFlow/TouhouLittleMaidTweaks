// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** The dangerous toggle opens a timed warning before it can be selected. */
public final class CustomConsentEntry extends AbstractConfigListEntry<Boolean> {
    private final boolean initial;
    private boolean selected;
    private final Button button;

    public CustomConsentEntry(Component label, boolean current) {
        super(label, false);
        initial = selected = current;
        button = Button.builder(Component.empty(), ignored -> {
            if (selected) selected = false;
            else Minecraft.getInstance().setScreen(new CustomPackWarningScreen(getConfigScreen(), () -> selected = true));
        }).bounds(0, 0, 150, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable(MaidTweaks.ID + ".settings.custom.warning")));
    }

    @Override public Boolean getValue() { return selected; }
    @Override public Optional<Boolean> getDefaultValue() { return Optional.of(false); }
    @Override public boolean isEdited() { return selected != initial || super.isEdited(); }

    @Override
    public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTick) {
        super.render(graphics, index, top, left, width, height, mouseX, mouseY, hovered, partialTick);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, getDisplayedFieldName(), left, top + 6, getPreferredTextColor());
        button.setX(left + width - 150);
        button.setY(top);
        button.active = isEditable();
        button.setMessage(Component.translatable(selected ? "options.on" : "options.off"));
        button.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public List<? extends GuiEventListener> children() { return List.of(button); }
    @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }
}
