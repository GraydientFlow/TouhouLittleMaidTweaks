// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sync.ClientPreferences;
import net.minecraft.network.chat.Component;

public final class ClothPackSyncConfig {
    private static final String KEY = MaidTweaks.ID + ".settings.cloth.";

    private ClothPackSyncConfig() {}

    public static void add(AddClothConfigEvent event, me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder category) {
        var entries = event.getEntryBuilder();
        String scopeKey = ClientSync.currentScopeKey();
        if (scopeKey == null) {
            category.add(entries.startTextDescription(Component.translatable(KEY + "no_server")).build());
            return;
        }
        category.add(entries.startTextDescription(Component.translatable(KEY + (ClientSync.localWorldActive() ? "world_scope" : "server_scope"))).build());
        if (!ClientSync.preferencesReady()) {
            category.add(entries.startTextDescription(Component.translatable(KEY + "unavailable")).build());
            return;
        }

        ClientPreferences.Consent consent = ClientSync.preference(scopeKey);
        var sync = entries.startBooleanToggle(Component.translatable(KEY + "sync"), consent.enabled())
                .setDefaultValue(false).build();
        var custom = new CustomConsentEntry(Component.translatable(KEY + "custom"), consent.customPacks());
        category.add(sync);
        category.add(custom);

        Runnable previousSave = event.getRoot().getSavingRunnable();
        event.getRoot().setSavingRunnable(() -> {
            if (previousSave != null) previousSave.run();
            ClientPreferences.Consent selected = new ClientPreferences.Consent(sync.getValue(), custom.getValue());
            if (!selected.equals(ClientSync.preference(scopeKey))) ClientSync.setPreference(scopeKey, selected);
        });
    }
}
