// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.station.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

/** Station draft controls, shared by the real menu and its native rendered preview. */
public final class StationForm {
    public static final int WIDTH = 396, HEIGHT = 256;
    private final Consumer<StationSettings> submit;
    private final IntConsumer detectPen;
    private final Runnable close;
    private final AnimalTable table = new AnimalTable();
    private StationSettings baseline, pending;
    private int method, detectionMode, actualDetection, pendingDetection = -1, selectedSide, area, population, status;
    private boolean limitBreeding, protectPair, loading, closing;
    private String feedback = "";
    private Font font;
    private int x, y;
    private EditBox density, count;
    private Button mode, breed, pair, detection, detect, apply;
    public StationForm(StationSettings initial, Consumer<StationSettings> submit, IntConsumer detectPen, Runnable close) {
        baseline = initial; method = initial.method(); limitBreeding = initial.limitBreeding(); protectPair = initial.protectPair();
        this.submit = submit; this.detectPen = detectPen; this.close = close;
    }
    private static Component text(String key, Object... args) { return Component.translatable("gui." + MaidTweaks.ID + ".station." + key, args); }
    private static Component edited(Component value, boolean changed) { return changed ? value.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC) : value; }
    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private Button button(Consumer<AbstractWidget> add, Component label, int dx, int dy, int width, Runnable action) {
        var b = Button.builder(label, ignored -> action.run()).bounds(x + dx, y + dy, width, 20).build(); add.accept(b); return b;
    }
    public void init(Font font, int x, int y, Consumer<AbstractWidget> add) {
        String densityText = density == null ? number(baseline.density() * 4) : density.getValue();
        String countText = count == null ? Integer.toString(baseline.keepCount()) : count.getValue();
        this.font = font; this.x = x; this.y = y;
        mode = button(add, Component.empty(), 86, 30, 123, () -> { method = (method + 1) % 3; changed(); });
        density = new EditBox(font, x + 338, y + 31, 44, 18, text("density")); density.setMaxLength(16); density.setValue(densityText); add.accept(density);
        count = new EditBox(font, x + 338, y + 31, 44, 18, text("count")); count.setMaxLength(5); count.setValue(countText); add.accept(count);
        density.setTooltip(Tooltip.create(text("density.tip"))); count.setTooltip(Tooltip.create(text("count.tip")));
        density.setResponder(ignored -> { if (!loading) changed(); }); count.setResponder(ignored -> { if (!loading) changed(); });
        density.setFormatter((value, offset) -> FormattedCharSequence.forward(value, Style.EMPTY.withColor(numericDirty() ? ChatFormatting.YELLOW : ChatFormatting.WHITE).withItalic(numericDirty())));
        count.setFormatter((value, offset) -> FormattedCharSequence.forward(value, Style.EMPTY.withColor(numericDirty() ? ChatFormatting.YELLOW : ChatFormatting.WHITE).withItalic(numericDirty())));
        table.layout(x + 14, y + 62, WIDTH - 28); add.accept(table);
        breed = button(add, Component.empty(), 108, 140, 64, () -> { limitBreeding = !limitBreeding; changed(); });
        pair = button(add, Component.empty(), 108, 166, 64, () -> { protectPair = !protectPair; changed(); });
        breed.setTooltip(Tooltip.create(text("breeding.tip"))); pair.setTooltip(Tooltip.create(text("pair.tip")));
        detection = button(add, Component.empty(), 280, 140, 102, () -> { detectionMode = (detectionMode + 1) % 6; changed(); });
        detection.setTooltip(Tooltip.create(text("detection_hint")));
        detect = button(add, text("detect"), 210, 166, 172, () -> { pendingDetection = detectionMode; detectPen.accept(detectionMode); refresh(); });
        detect.setTooltip(Tooltip.create(text("detection_hint")));
        apply = button(add, text("apply"), 78, 204, 72, () -> save(false));
        button(add, Component.translatable("gui.cancel"), 162, 204, 72, close);
        button(add, Component.translatable("gui.done"), 246, 204, 72, () -> save(true));
        refresh();
    }
    private void changed() { pending = null; pendingDetection = -1; closing = false; feedback = ""; refresh(); }
    public StationSettings selected() {
        try {
            double d = method == StationSettings.COUNT ? baseline.density() : Double.parseDouble(density.getValue()) / 4;
            int c = method == StationSettings.COUNT ? Integer.parseInt(count.getValue()) : baseline.keepCount();
            var selected = new StationSettings(method, Math.round(d * 1_000_000) / 1_000_000.0, c, limitBreeding, protectPair);
            return Double.isFinite(d) && d >= 0 && d <= 64 && selected.valid() ? selected : null;
        } catch (NumberFormatException invalid) { return null; }
    }
    public boolean dirty() { var selected = selected(); return selected == null || !selected.equals(baseline) || detectionMode != actualDetection; }
    private boolean numericDirty() {
        try { return method == StationSettings.COUNT ? Integer.parseInt(count.getValue()) != baseline.keepCount()
                : Double.compare(Double.parseDouble(density.getValue()), baseline.density() * 4) != 0; }
        catch (NumberFormatException invalid) { return true; }
    }
    private void refresh() {
        if (mode == null) return;
        String key = method == StationSettings.COUNT ? "count" : method == StationSettings.FOOTPRINT ? "footprint" : "density";
        mode.setMessage(edited(text("mode." + key), method != baseline.method())); mode.setTooltip(Tooltip.create(text("mode." + key + ".tip")));
        density.visible = density.active = method != StationSettings.COUNT; count.visible = count.active = method == StationSettings.COUNT;
        breed.setMessage(edited(Component.translatable(limitBreeding ? "options.on" : "options.off"), limitBreeding != baseline.limitBreeding()));
        pair.setMessage(edited(Component.translatable(protectPair ? "options.on" : "options.off"), protectPair != baseline.protectPair()));
        detection.setMessage(edited(text("detection." + detectionMode), detectionMode != actualDetection));
        apply.visible = dirty(); apply.active = selected() != null && pending == null && pendingDetection < 0 && (detectionMode == actualDetection || status != 0 && status != 6);
        apply.setMessage(edited(text("apply"), true)); detect.active = status != 0 && status != 6 && pendingDetection < 0;
    }
    private void save(boolean finish) {
        if (pending != null) { closing |= finish; return; }
        if (!dirty()) { if (finish) close.run(); return; }
        var selected = selected();
        if (selected == null) { feedback = "invalid"; return; }
        if (detectionMode != actualDetection && (status == 0 || status == 6)) return;
        closing = finish; feedback = ""; pending = selected;
        if (!selected.equals(baseline)) submit.accept(selected);
        if (detectionMode != actualDetection) { pendingDetection = detectionMode; detectPen.accept(detectionMode); }
        refresh();
    }
    public void receive(StationSettings current, int detection, int side, int area, int population, int status, List<StationAnimals.Row> rows) {
        if (density == null) return;
        boolean clean = !dirty(), acknowledged = pending != null && pending.equals(current) && (pendingDetection < 0 || pendingDetection == detection);
        if (pendingDetection == detection) pendingDetection = -1;
        baseline = current; actualDetection = detection; selectedSide = side; this.area = area; this.population = population; this.status = status;
        if (clean || acknowledged) {
            method = current.method(); limitBreeding = current.limitBreeding(); protectPair = current.protectPair(); detectionMode = detection;
            loading = true;
            try { density.setValue(number(current.density() * 4)); count.setValue(Integer.toString(current.keepCount())); }
            finally { loading = false; }
        }
        if (acknowledged) { pending = null; feedback = "saved"; if (closing) { close.run(); return; } }
        table.update(rows, area, selected(), status == 1 || status == 4 || status == 5);
        refresh();
    }
    public void renderTooltip(GuiGraphics g, int mouseX, int mouseY) { table.renderTooltip(g, mouseX, mouseY); }
    public void render(GuiGraphics g, Component title, int mouseX, int mouseY) {
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xff252d26);
        g.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, 0xffd4d0bb);
        g.drawString(font, title, x + 14, y + 11, 0x242824, false);
        if (!feedback.isEmpty()) g.drawString(font, text(feedback), x + WIDTH - 14 - font.width(text(feedback)), y + 11, feedback.equals("invalid") ? 0xaa2020 : 0x375138, false);
        if (feedback.isEmpty() && status != 1 && status != 4 && status != 5) {
            Component notice = text("status." + status);
            g.drawString(font, notice, x + WIDTH - 14 - font.width(notice), y + 11, 0x985d22, false);
        }
        g.drawString(font, text("method"), x + 14, y + 36, 0x303030, false);
        g.drawString(font, "→", x + 215, y + 36, 0x303030, false);
        g.drawString(font, edited(text(method == StationSettings.COUNT ? "count" : "density"), numericDirty()), x + 229, y + 36, 0x303030, false);
        g.drawString(font, text("label", text("breeding")), x + 14, y + 146, 0x303030, false);
        g.drawString(font, text("label", text("pair")), x + 14, y + 172, 0x303030, false);
        g.drawString(font, text("label", text("detection")), x + 210, y + 146, 0x303030, false);
        g.fill(x + 194, y + 140, x + 195, y + 186, 0xffc0bea9);
        Component left = text("footer", text("detection." + selectedSide), area, population);
        Component slaughter = text("allow_butcher"), breeding = text("allow_breeding");
        int right = x + WIDTH - 14, breedingWidth = font.width(breeding), slaughterWidth = font.width(slaughter);
        g.drawString(font, breeding, right - breedingWidth, y + 238, table.breed() ? 0x375138 : 0x858573, false);
        g.drawString(font, slaughter, right - breedingWidth - slaughterWidth - 10, y + 238, table.butcher() ? 0x375138 : 0x858573, false);
        int divider = right - breedingWidth - slaughterWidth - 18;
        g.fill(divider, y + 236, divider + 1, y + 248, 0xffa8a793);
        float scale = Math.min(1f, (divider - x - 24f) / Math.max(1, font.width(left)));
        g.pose().pushPose(); g.pose().translate(x + 14, y + 238, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, left, 0, 0, 0x404040, false); g.pose().popPose();
    }
}
