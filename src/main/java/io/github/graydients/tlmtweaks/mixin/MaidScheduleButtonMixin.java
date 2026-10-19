// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.ScheduleButton;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.graydients.tlmtweaks.client.ScheduleTooltip;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ScheduleButton.class,remap=false)
public abstract class MaidScheduleButtonMixin {
    @Shadow @Final private EntityMaid maid;
    @Shadow private MaidSchedule mode;
    @Inject(method="getTooltips",at=@At("HEAD"),cancellable=true)
    private void times(CallbackInfoReturnable<List<Component>> callback) {
        callback.setReturnValue(ScheduleTooltip.lines(mode,maid.level().getDayTime()));
    }
}
