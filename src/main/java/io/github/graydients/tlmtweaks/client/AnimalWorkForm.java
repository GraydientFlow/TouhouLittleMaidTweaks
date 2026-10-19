// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.jobs.AnimalSettings;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

/** General unfenced settings: separate rule/value rows, without station lists or fence controls. */
public final class AnimalWorkForm {
    public static final int WIDTH = 166;
    private final Consumer<AnimalSettings> submit;
    private final Runnable close;
    private AnimalSettings baseline, pending;
    private boolean limitBreeding, protectPair, loading, closing;
    private int method;
    private String status = "";
    private EditBox width, density, count;
    private Button mode, breed, pair, apply, cancel, done;
    private Font font;
    private int x, y;
    public AnimalWorkForm(AnimalSettings baseline, Consumer<AnimalSettings> submit, Runnable close) {
        this.baseline = baseline; this.submit = submit; this.close = close;
        method = baseline.method(); limitBreeding = baseline.limitBreeding(); protectPair = baseline.protectPair();
    }
    private static Component text(String key, Object... args) { return Component.translatable("gui." + MaidTweaks.ID + ".animals." + key, args); }
    private static Component edited(Component text, boolean changed) { return changed ? text.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC) : text; }
    public void init(Font font, int x, int y, Consumer<AbstractWidget> add) {
        String w = width == null ? Integer.toString(baseline.searchWidth()) : width.getValue();
        String d = density == null ? Double.toString(baseline.density()) : density.getValue();
        String c = count == null ? Integer.toString(baseline.keepCount()) : count.getValue();
        this.font = font; this.x = x; this.y = y;
        width = new EditBox(font, x + 112, y, 54, 16, text("width")); width.setMaxLength(2); width.setValue(w); add.accept(width);
        mode = Button.builder(Component.empty(), b -> {
            method = method == AnimalSettings.VOLUME ? AnimalSettings.FOOTPRINT : method == AnimalSettings.FOOTPRINT ? AnimalSettings.COUNT : AnimalSettings.VOLUME;
            changed();
        }).bounds(x + 76, y + 22, 90, 18).build(); add.accept(mode);
        density = new EditBox(font, x + 112, y + 44, 54, 16, text("density")); density.setMaxLength(12); density.setValue(d); add.accept(density);
        count = new EditBox(font, x + 112, y + 44, 54, 16, text("count")); count.setMaxLength(5); count.setValue(c); add.accept(count);
        width.setTooltip(Tooltip.create(text("width.tip"))); density.setTooltip(Tooltip.create(text("density.tip"))); count.setTooltip(Tooltip.create(text("count.tip")));
        width.setResponder(ignored -> { if (!loading) changed(); }); density.setResponder(ignored -> { if (!loading) changed(); }); count.setResponder(ignored -> { if (!loading) changed(); });
        width.setFormatter((value, offset) -> FormattedCharSequence.forward(value, Style.EMPTY.withColor(widthDirty() ? ChatFormatting.YELLOW : ChatFormatting.WHITE).withItalic(widthDirty())));
        density.setFormatter((value, offset) -> FormattedCharSequence.forward(value, Style.EMPTY.withColor(numericDirty() ? ChatFormatting.YELLOW : ChatFormatting.WHITE).withItalic(numericDirty())));
        count.setFormatter((value, offset) -> FormattedCharSequence.forward(value, Style.EMPTY.withColor(numericDirty() ? ChatFormatting.YELLOW : ChatFormatting.WHITE).withItalic(numericDirty())));
        breed = Button.builder(Component.empty(), b -> { limitBreeding = !limitBreeding; changed(); }).bounds(x, y + 66, 82, 18).build(); add.accept(breed);
        pair = Button.builder(Component.empty(), b -> { protectPair = !protectPair; changed(); }).bounds(x + 84, y + 66, 82, 18).build(); add.accept(pair);
        breed.setTooltip(Tooltip.create(text("breeding.tip"))); pair.setTooltip(Tooltip.create(text("pair.tip")));
        apply = Button.builder(Component.empty(), b -> save(false)).bounds(x, y + 98, 54, 18).build(); add.accept(apply);
        cancel = Button.builder(Component.translatable("gui.cancel"), b -> close.run()).bounds(x + 56, y + 98, 54, 18).build(); add.accept(cancel);
        done = Button.builder(Component.translatable("gui.done"), b -> save(true)).bounds(x + 112, y + 98, 54, 18).build(); add.accept(done);
        refresh();
    }
    public AnimalSettings selected() {
        try {
            var value = new AnimalSettings(Integer.parseInt(width.getValue()), method == AnimalSettings.COUNT ? baseline.density() : Double.parseDouble(density.getValue()),
                    limitBreeding, protectPair, method, method == AnimalSettings.COUNT ? Integer.parseInt(count.getValue()) : baseline.keepCount());
            return value.valid() ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }
    public boolean dirty() { var value = selected(); return value == null || !value.equals(baseline); }
    public boolean navigation(AbstractWidget widget) { return widget == cancel || widget == done; }
    private boolean widthDirty() { try { return Integer.parseInt(width.getValue()) != baseline.searchWidth(); } catch (NumberFormatException invalid) { return true; } }
    private boolean numericDirty() {
        try { return method == AnimalSettings.COUNT ? Integer.parseInt(count.getValue()) != baseline.keepCount() : Double.compare(Double.parseDouble(density.getValue()), baseline.density()) != 0; }
        catch (NumberFormatException invalid) { return true; }
    }
    private void changed() { pending = null; closing = false; status = ""; refresh(); }
    private void refresh() {
        if (breed == null) return;
        mode.setMessage(edited(text("method." + method), method != baseline.method()));
        mode.setTooltip(Tooltip.create(text("method." + method + ".tip")));
        density.visible = method != AnimalSettings.COUNT; count.visible = method == AnimalSettings.COUNT;
        breed.setMessage(edited(text("breeding", Component.translatable(limitBreeding ? "options.on" : "options.off")), limitBreeding != baseline.limitBreeding()));
        pair.setMessage(edited(text("pair", Component.translatable(protectPair ? "options.on" : "options.off")), protectPair != baseline.protectPair()));
        apply.setMessage(edited(text("apply"), true)); apply.visible = dirty(); apply.active = selected() != null && pending == null;
    }
    private void save(boolean finish) {
        if (pending != null) { closing |= finish; return; }
        if (!dirty()) { if (finish) close.run(); return; }
        var value = selected(); if (value == null) { status = "invalid"; return; }
        pending = value; closing = finish; status = ""; submit.accept(value); refresh();
    }
    public void receive(AnimalSettings current) {
        if (width == null) return;
        boolean clean = !dirty(), acknowledged = current.equals(pending);
        baseline = current;
        if (clean || acknowledged) {
            method = current.method(); limitBreeding = current.limitBreeding(); protectPair = current.protectPair(); loading = true;
            try { width.setValue(Integer.toString(current.searchWidth())); density.setValue(Double.toString(current.density())); count.setValue(Integer.toString(current.keepCount())); }
            finally { loading = false; }
        }
        if (acknowledged) { pending = null; status = "saved"; if (closing) { close.run(); return; } }
        refresh();
    }
    public void render(GuiGraphics g) {
        g.drawString(font, edited(text("width"), widthDirty()), x, y + 4, 0x303030, false);
        g.drawString(font, text("rule"), x, y + 27, 0x303030, false);
        g.drawString(font, edited(text(method == AnimalSettings.COUNT ? "count" : "density"), numericDirty()), x, y + 48, 0x303030, false);
        if (!status.isEmpty()) g.drawString(font, text(status), x, y + 122, status.equals("invalid") ? 0xaa2020 : 0x375138, false);
    }
}
