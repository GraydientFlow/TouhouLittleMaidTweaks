// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Shows the script-execution risk for five seconds before accepting custom-pack consent. */
public final class CustomPackWarningScreen extends Screen {
    private static final String KEY = MaidTweaks.ID + ".settings.custom.confirm.";
    private final Screen parent;
    private final Runnable accept;
    private final long unlockAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    private List<List<FormattedCharSequence>> paragraphs = List.of();
    private Button confirm;
    private int bodyTop;

    public CustomPackWarningScreen(Screen parent, Runnable accept) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.accept = accept;
    }

    @Override
    protected void init() {
        int textWidth = Math.min(width - 32, 430);
        var wrapped = new ArrayList<List<FormattedCharSequence>>();
        int rowCount = 0;
        for (int index = 1; index <= 3; index++) {
            var lines = font.split(Component.translatable(KEY + "line" + index), textWidth);
            wrapped.add(lines);
            rowCount += lines.size();
        }
        paragraphs = List.copyOf(wrapped);
        int contentHeight = 22 + rowCount * 11 + paragraphs.size() * 4 + 34;
        bodyTop = Math.max(10, (height - contentHeight) / 2);
        int buttonY = Math.min(height - 26, bodyTop + contentHeight - 22);
        int confirmWidth = Math.min(200, Math.max(120, width - 110));
        int buttonsLeft = (width - confirmWidth - 90) / 2;
        confirm = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            if (System.nanoTime() < unlockAt) return;
            accept.run();
            Minecraft.getInstance().setScreen(parent);
        }).bounds(buttonsLeft, buttonY, confirmWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(buttonsLeft + confirmWidth + 10, buttonY, 80, 20).build());
        updateConfirm();
    }

    private void updateConfirm() {
        long nanosLeft = Math.max(0L, unlockAt - System.nanoTime());
        int secondsLeft = (int) ((nanosLeft + 999_999_999L) / 1_000_000_000L);
        confirm.active = secondsLeft == 0;
        confirm.setMessage(secondsLeft == 0
                ? Component.translatable(KEY + "allow")
                : Component.translatable(KEY + "countdown", secondsLeft));
    }

    @Override public void tick() { updateConfirm(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render draws the blurred background and buttons. Draw the text afterwards.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, bodyTop, 0xff5555);
        int y = bodyTop + 22;
        for (var paragraph : paragraphs) {
            for (var line : paragraph) {
                graphics.drawCenteredString(font, line, width / 2, y, 0xffffff);
                y += 11;
            }
            y += 4;
        }
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
