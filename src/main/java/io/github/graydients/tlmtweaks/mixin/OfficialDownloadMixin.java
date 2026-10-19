// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.download.InfoGetManager;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import io.github.graydients.tlmtweaks.client.ClientSync;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InfoGetManager.class, remap = false)
public abstract class OfficialDownloadMixin {
    @Inject(method = "reloadPack(Lcom/github/tartaricacid/touhoulittlemaid/client/download/pojo/DownloadInfo;Ljava/io/File;Ljava/io/File;)V",
            at = @At("TAIL"), remap = false)
    private static void graydients$downloadCompleted(DownloadInfo info, File cache, File installed, CallbackInfo ci) {
        ClientSync.completedUiDownload(info, installed);
    }
}
