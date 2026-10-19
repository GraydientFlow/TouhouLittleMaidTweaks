// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import io.github.graydients.tlmtweaks.sleep.MaidSleep;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse vanilla time advancement, NeoForge's sleep event, weather rules and player wakeup. */
@Mixin(ServerLevel.class)
public abstract class MaidSleepLevelMixin {
    @Inject(method = "announceSleepStatus", at = @At("HEAD"), cancellable = true)
    private void mixedSleepNotice(CallbackInfo callback) {
        if (MaidSleep.announcePlayerSleep((ServerLevel) (Object) this)) callback.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean enoughSleeping(SleepStatus players, int percentage) {
        return players.areEnoughSleeping(percentage) || MaidSleep.ready((ServerLevel) (Object) this);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
    private boolean enoughDeepSleeping(SleepStatus players, int percentage, List<ServerPlayer> online) {
        return players.areEnoughDeepSleeping(percentage, online) || MaidSleep.ready((ServerLevel) (Object) this);
    }
}
