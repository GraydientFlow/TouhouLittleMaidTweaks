// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sync.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SyncSettingsScreen extends Screen {
    private static final String KEY = MaidTweaks.ID + ".settings.";
    private final Screen parent;
    private Button sync;
    private Button custom;

    public SyncSettingsScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int left = width / 2 - 155;
        int top = Math.max(36, height / 2 - 58);
        sync = addRenderableWidget(Button.builder(Component.empty(), ignored -> toggleSync())
                .bounds(left, top + 14, 310, 20).build());
        custom = addRenderableWidget(Button.builder(Component.empty(), ignored -> toggleCustom())
                .bounds(left, top + 40, 310, 20).build());
        custom.setTooltip(Tooltip.create(Component.translatable(KEY + "custom.warning")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 100, top + 105, 200, 20).build());
        refresh();
    }

    private void toggleSync() {
        String key = ClientSync.currentScopeKey();
        if (key == null || !ClientSync.preferencesReady()) return;
        ClientPreferences.Consent before = ClientSync.preference(key);
        ClientSync.setPreference(key, new ClientPreferences.Consent(!before.enabled(), before.customPacks()));
        refresh();
    }

    private void toggleCustom() {
        String key = ClientSync.currentScopeKey();
        if (key == null || !ClientSync.preferencesReady()) return;
        ClientPreferences.Consent before = ClientSync.preference(key);
        if (before.customPacks()) {
            ClientSync.setPreference(key, new ClientPreferences.Consent(before.enabled(), false));
            refresh();
            return;
        }
        Minecraft.getInstance().setScreen(new CustomPackWarningScreen(this, () -> {
            if (!key.equals(ClientSync.currentScopeKey()) || !ClientSync.preferencesReady()) return;
            ClientPreferences.Consent current = ClientSync.preference(key);
            ClientSync.setPreference(key, new ClientPreferences.Consent(current.enabled(), true));
        }));
    }

    private void refresh() {
        String key = ClientSync.currentScopeKey();
        ClientPreferences.Consent consent = key == null ? ClientPreferences.Consent.OFF : ClientSync.preference(key);
        sync.active = custom.active = key != null && ClientSync.preferencesReady();
        sync.setMessage(Component.translatable(KEY + "sync", Component.translatable(consent.enabled() ? "options.on" : "options.off")));
        custom.setMessage(Component.translatable(KEY + "custom", Component.translatable(consent.customPacks() ? "options.on" : "options.off")));
    }

    @Override public void tick() { refresh(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = sync.getY() - 14;
        graphics.drawCenteredString(font, title, width / 2, top - 32, 0xffffff);
        String scope = ClientSync.currentScopeKey() == null ? "no_server"
                : ClientSync.localWorldActive() ? "world_scope" : "server_scope";
        graphics.drawCenteredString(font, Component.translatable(KEY + "cloth." + scope), width / 2, top - 6, 0xffffff);
        graphics.drawCenteredString(font, Component.translatable(KEY + "custom.notice"), width / 2, top + 67, 0xffcc66);
        graphics.drawCenteredString(font, ClientSync.preferenceStatus(), width / 2, top + 84, 0xaaaaaa);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
