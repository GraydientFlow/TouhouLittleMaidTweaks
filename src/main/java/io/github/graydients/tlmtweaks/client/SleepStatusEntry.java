// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Read-only text that updates when the server publishes a new queue or bed forecast. */
final class SleepStatusEntry extends AbstractConfigListEntry<Object> {
    private final Supplier<Component> text;
    private Component shown;
    private int wrapWidth = 320;
    private List<FormattedCharSequence> lines = List.of();

    SleepStatusEntry(Supplier<Component> text) { super(Component.empty(), false); this.text = text; }

    private void wrap(int width) {
        Component current = text.get();
        if (width != wrapWidth || !current.equals(shown)) {
            wrapWidth = width; shown = current;
            lines = Minecraft.getInstance().font.split(current, width);
        }
    }

    @Override public int getItemHeight() { wrap(wrapWidth); return 14 + lines.size() * 12; }
    @Override public Object getValue() { return null; }
    @Override public Optional<Object> getDefaultValue() { return Optional.empty(); }
    @Override public List<? extends GuiEventListener> children() { return List.of(); }
    @Override public List<? extends NarratableEntry> narratables() { return List.of(); }
    @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                                 int mouseX, int mouseY, boolean hovered, float partialTick) {
        super.render(graphics, index, top, left, width, height, mouseX, mouseY, hovered, partialTick);
        wrap(width);
        int y = top + 7;
        for (var line : lines) { graphics.drawString(Minecraft.getInstance().font, line, left, y, 0xffffff); y += 12; }
    }
}
