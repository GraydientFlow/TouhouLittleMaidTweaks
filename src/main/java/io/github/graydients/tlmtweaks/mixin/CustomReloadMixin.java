// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.event.ReloadResourceEvent;
import io.github.graydients.tlmtweaks.client.CustomClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ReloadResourceEvent.class,remap=false)
public abstract class CustomReloadMixin {
    @Inject(method="asyncReloadAllPack",at=@At("RETURN"))
    private static void reload(CallbackInfo callback){CustomClient.requestScan();}
}
