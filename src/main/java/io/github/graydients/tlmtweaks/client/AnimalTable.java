// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.station.*;
import java.math.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

/** Horizontally scrolling species columns with fixed row labels and actual weighted details. */
public final class AnimalTable extends AbstractWidget {
    private static final int LABEL_WIDTH = 56, MIN_COLUMN = 62, OVER_LIMIT = 0xb33f20;
    private List<StationAnimals.Row> received = List.of(), rows = List.of();
    private List<List<Component>> details = List.of();
    private Map<ResourceLocation, Long> limits = Map.of();
    private StationSettings policy;
    private int area = -1;
    private double scroll, dragOffset;
    private boolean verified, butcher, breed, dragging;
    public AnimalTable() { super(0, 0, 368, 70, text("table.title")); }
    private static Component text(String key, Object... args) {
        return Component.translatable("gui." + MaidTweaks.ID + ".station." + key, args);
    }
    public void layout(int x, int y, int width) {
        setPosition(x, y); setWidth(width); scroll = Mth.clamp(scroll, 0, maxScroll());
    }
    private int viewport() { return getWidth() - LABEL_WIDTH; }
    private int cell() { return Math.max(MIN_COLUMN, viewport() / Math.max(1, rows.size())); }
    private int maxScroll() { return Math.max(0, cell() * rows.size() - viewport()); }
    public boolean scrollable() { return maxScroll() > 0; }
    public double scrollOffset() { return scroll; }
    public boolean butcher() { return butcher; }
    public boolean breed() { return breed; }
    public void update(List<StationAnimals.Row> source, int floor, StationSettings selected, boolean validPen) {
        if (source.equals(received) && floor == area && Objects.equals(policy, selected) && verified == validPen) return;
        received = List.copyOf(source); area = floor; policy = selected; verified = validPen;
        int method = selected == null ? StationSettings.VOLUME : selected.method();
        rows = source.stream().sorted(Comparator.<StationAnimals.Row>comparingLong(r -> r.unit(method)).reversed()
                .thenComparing(Comparator.comparingLong(StationAnimals.Row::adultVolume).reversed())
                .thenComparing(r -> r.type().toString())).toList();
        scroll = Mth.clamp(scroll, 0, maxScroll()); butcher = breed = false;
        if (selected == null) limits = Map.of();
        else {
            var sizes = new HashMap<ResourceLocation, Long>();
            rows.forEach(r -> sizes.put(r.type(), r.unit(method)));
            long budget = BigDecimal.valueOf(selected.density()).multiply(BigDecimal.valueOf(floor * 4L)).longValue();
            limits = selected.countMode() ? Map.of() : SpeciesCapacity.allocate(budget, sizes, Comparator.comparing(ResourceLocation::toString));
            if (validPen) for (var row : rows) if (row.ready() > 0) {
                boolean space = selected.countMode() ? row.adults() < selected.keepCount() : row.used(method) < limits.get(row.type());
                butcher |= over(row) && (!selected.protectPair() || row.adults() > 2);
                breed |= !selected.limitBreeding() || space;
            }
        }
        details = rows.stream().map(this::details).toList();
    }
    private boolean over(StationAnimals.Row row) {
        return policy != null && (policy.countMode() ? row.adults() > policy.keepCount() : row.used(policy.method()) > limits.get(row.type()));
    }
    private Component name(StationAnimals.Row row) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(row.type()).map(t -> t.getDescription())
                .orElseGet(() -> Component.translatable("entity." + row.type().getNamespace() + "." + row.type().getPath()));
    }
    private static String number(long numerator, long denominator) {
        var value = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP).stripTrailingZeros();
        boolean rounded = value.multiply(BigDecimal.valueOf(denominator)).compareTo(BigDecimal.valueOf(numerator)) != 0;
        return (rounded ? "≈" : "") + value.toPlainString();
    }
    private List<Component> details(StationAnimals.Row row) {
        if (policy == null) return List.of(name(row));
        boolean count = policy.countMode(); int method = policy.method();
        long unit = count ? 1 : row.unit(method), young = count ? 0 : row.babyUsed(method);
        long used = count ? row.adults() : row.used(method), adults = used - young;
        var font = Minecraft.getInstance().font;
        Component a = text("table.adults"), b = text("table.babies"), t = text("table.used"), c = text("table.capacity");
        int labelWidth = Math.max(Math.max(font.width(a), font.width(b)), Math.max(font.width(t), font.width(c)));
        var result = new ArrayList<Component>();
        result.add(name(row).copy().withStyle(ChatFormatting.BOLD));
        result.add(line(a, labelWidth, row.adults() + " × " + (row.adults() == 0 ? "1" : number(adults, unit * row.adults()))));
        result.add(line(b, labelWidth, row.babies() + " × " + (row.babies() == 0 ? "0" : number(young, unit * row.babies()))));
        var total = line(t, labelWidth, number(used, unit));
        result.add(over(row) ? total.copy().withStyle(ChatFormatting.GOLD) : total);
        result.add(Component.empty());
        result.add(line(c, labelWidth, count ? Integer.toString(policy.keepCount()) : number(limits.get(row.type()), unit)));
        result.add(text("table.units").copy().withStyle(ChatFormatting.GRAY));
        return List.copyOf(result);
    }
    private static Component line(Component label, int width, String value) {
        int gap = Math.max(1, (width - Minecraft.getInstance().font.width(label) + 11) / 4);
        return label.copy().append(":" + " ".repeat(gap) + value);
    }
    private int hoveredColumn(double mouseX, double mouseY) {
        int left = getX() + LABEL_WIDTH;
        if (mouseX < left || mouseX >= getX() + getWidth() || mouseY < getY() || mouseY >= getY() + 58) return -1;
        int index = (int)((mouseX - left + scroll) / cell());
        return index < rows.size() ? index : -1;
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), width = getWidth(), left = x + LABEL_WIDTH;
        g.drawString(font, text("table.capacity"), x, y + 23, 0x404040, false);
        g.drawString(font, text("table.current"), x, y + 43, 0x404040, false);
        g.fill(x, y + 16, x + width, y + 17, 0xffa8a793);
        g.fill(x, y + 36, x + width, y + 37, 0xffc0bea9);
        if (rows.isEmpty()) { g.drawCenteredString(font, text("table.empty"), left + viewport() / 2, y + 23, 0x777766); return; }
        int hovered = hoveredColumn(mouseX, mouseY), first = (int)scroll / cell();
        g.enableScissor(left, y, x + width, y + 58);
        for (int i = first; i < rows.size() && left + i * cell() - scroll < x + width; i++) {
            var row = rows.get(i); int start = left + i * cell() - (int)scroll, center = start + cell() / 2;
            if (i == hovered) g.fill(start, y, start + cell(), y + 57, 0x16816c4b);
            String title = name(row).getString();
            if (font.width(title) > cell() - 8) title = font.plainSubstrByWidth(title, cell() - 14) + "…";
            g.drawString(font, title, center - font.width(title) / 2, y + 3, 0x303030, false);
            String capacity = policy == null ? "—" : Long.toString(policy.countMode() ? policy.keepCount() : limits.get(row.type()) / row.unit(policy.method()));
            g.drawString(font, capacity, center - font.width(capacity) / 2, y + 23, 0x404040, false);
            String current = Integer.toString(row.total());
            g.drawString(font, current, center - font.width(current) / 2, y + 43, over(row) ? OVER_LIMIT : 0x404040, false);
            if (i > 0) g.fill(start, y, start + 1, y + 57, 0xffc0bea9);
        }
        g.disableScissor();
        if (scrollable()) {
            g.fill(left, y + 63, x + width, y + 68, 0xffc0bea9);
            g.fill(thumbLeft(), y + 63, thumbLeft() + thumbWidth(), y + 68, dragging ? 0xff686552 : 0xff918d76);
        }
    }
    public void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = hoveredColumn(mouseX, mouseY);
        if (index >= 0) g.renderComponentTooltip(Minecraft.getInstance().font, details.get(index), mouseX, mouseY);
    }
    private int thumbWidth() { return Math.max(20, viewport() * viewport() / (cell() * rows.size())); }
    private int thumbLeft() { return getX() + LABEL_WIDTH + (int)(scroll / maxScroll() * (viewport() - thumbWidth())); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY) || !scrollable()) return false;
        double amount = Math.abs(horizontal) > Math.abs(vertical) ? horizontal : vertical;
        scroll = Mth.clamp(scroll - amount * cell(), 0, maxScroll()); return amount != 0;
    }
    @Override public void onClick(double mouseX, double mouseY) {
        if (!scrollable() || mouseY < getY() + 61 || mouseX < getX() + LABEL_WIDTH) return;
        dragOffset = mouseX >= thumbLeft() && mouseX < thumbLeft() + thumbWidth() ? mouseX - thumbLeft() : thumbWidth() / 2.0;
        dragging = true; drag(mouseX);
    }
    private void drag(double mouseX) { scroll = Mth.clamp((mouseX - getX() - LABEL_WIDTH - dragOffset) / (viewport() - thumbWidth()), 0, 1) * maxScroll(); }
    @Override protected void onDrag(double mouseX, double mouseY, double dx, double dy) { if (dragging) drag(mouseX); }
    @Override public void onRelease(double mouseX, double mouseY) { dragging = false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!scrollable()) return false;
        double target = switch (key) {
            case InputConstants.KEY_LEFT -> scroll - cell(); case InputConstants.KEY_RIGHT -> scroll + cell();
            case InputConstants.KEY_HOME -> 0; case InputConstants.KEY_END -> maxScroll(); default -> Double.NaN;
        };
        if (Double.isNaN(target)) return false;
        scroll = Mth.clamp(target, 0, maxScroll()); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (scrollable()) output.add(NarratedElementType.USAGE, text("table.scroll"));
    }
}
