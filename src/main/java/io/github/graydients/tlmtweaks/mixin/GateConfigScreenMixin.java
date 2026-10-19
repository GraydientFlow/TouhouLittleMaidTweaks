// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.config.MaidConfigContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.MaidConfigButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidConfigManager;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.config.MaidConfigContainer;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.gates.GateProtocol;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaidConfigContainerGui.class,remap=false)
public abstract class GateConfigScreenMixin extends AbstractMaidContainerGui<MaidConfigContainer> {
    @Shadow @Final private MaidConfigManager.SyncNetwork syncNetwork;
    @Unique private int graydients$mode;
    @Unique private MaidConfigButton graydients$button;
    protected GateConfigScreenMixin(MaidConfigContainer menu,Inventory inv,Component title){super(menu,inv,title);}
    @Unique private Component graydients$value(){return Component.translatable("gui."+MaidTweaks.ID+".gates.mode."+graydients$mode);}
    @Inject(method="initAdditionWidgets",at=@At("RETURN"))
    private void values(CallbackInfo callback){
        graydients$mode=GhostGates.mode(getMaid());
        for(var child:java.util.List.copyOf(children()))if(child instanceof MaidConfigButton button && button.getMessage().equals(Component.translatable("gui.touhou_little_maid.maid_config.open_fence_gate"))) {
            removeWidget(button);
            graydients$button=addRenderableWidget(new MaidConfigButton(button.getX(),button.getY(),button.getMessage(),graydients$value(),b->graydients$cycle(b,-1),b->graydients$cycle(b,1)));
            break;
        }
    }
    @Unique private void graydients$cycle(MaidConfigButton button,int direction){
        graydients$mode=(GhostGates.mode(getMaid())+direction+3)%3;
        getMaid().setData(GhostGates.KEY,graydients$mode==2);getMaid().getConfigManager().setOpenFenceGate(graydients$mode!=0);
        syncNetwork.setOpenFenceGate(graydients$mode!=0);button.setValue(graydients$value());
        PacketDistributor.sendToServer(new GateProtocol.SetMode(getMaid().getId(),graydients$mode));
    }
    @Inject(method="renderBg",at=@At("HEAD"))
    private void acknowledge(net.minecraft.client.gui.GuiGraphics graphics,float partial,int mouseX,int mouseY,CallbackInfo callback){
        if(graydients$button!=null){graydients$mode=GhostGates.mode(getMaid());graydients$button.setValue(graydients$value());syncNetwork.setOpenFenceGate(graydients$mode!=0);}
    }
}
