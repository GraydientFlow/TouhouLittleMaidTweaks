// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.mixin.accessor.FenceGateBlockAccessor;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.gates.GateProtocol;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only visual OPEN state, with collision masked throughout the passage lease. */
@EventBusSubscriber(modid=MaidTweaks.ID,value=Dist.CLIENT)
public final class GhostGateView {
    private static final Map<Long,View> VIEWS=new HashMap<>();
    private static boolean applying;
    private static final class View {
        BlockState original; boolean showing; long close;
        final Map<Integer,Long> leases=new HashMap<>();
    }
    private GhostGateView() {}
    public static void flash(GateProtocol.Flash packet) {
        ClientLevel level=Minecraft.getInstance().level;if(level==null || packet.duration()<1 || packet.duration()>100)return;
        var state=level.getBlockState(packet.pos());if(!(state.getBlock() instanceof FenceGateBlock))return;
        View view=VIEWS.computeIfAbsent(packet.pos().asLong(),p->{var v=new View();v.original=state;return v;});
        view.leases.put(packet.maidId(),level.getGameTime()+packet.duration());view.close=level.getGameTime()+12;view.showing=true;
        set(level,packet.pos(),view.original.setValue(FenceGateBlock.OPEN,true));sound(level,packet.pos(),view.original,true);
    }
    private static void set(ClientLevel level,BlockPos pos,BlockState state) {applying=true;try{level.setBlock(pos,state,11);}finally{applying=false;}}
    public static void changed(Level level,BlockPos pos,BlockState state) {
        if(applying || !(level instanceof ClientLevel))return;
        var view=VIEWS.get(pos.asLong());if(view==null)return;
        // An authoritative update supersedes both the picture and the collision lease.
        VIEWS.remove(pos.asLong());
    }
    private static void sound(ClientLevel level,BlockPos pos,BlockState state,boolean open) {
        var event=state.getBlock() instanceof FenceGateBlockAccessor access?(open?access.tlmOpenSound():access.tlmCloseSound()):(open?SoundEvents.FENCE_GATE_OPEN:SoundEvents.FENCE_GATE_CLOSE);
        level.playLocalSound(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,event,SoundSource.BLOCKS,.8f,1,false);
    }
    public static VoxelShape collision(Level level,BlockPos pos,BlockState state,CollisionContext context) {
        if(!(level instanceof ClientLevel) || !(state.getBlock() instanceof FenceGateBlock))return null;
        var view=VIEWS.get(pos.asLong());if(view==null)return null;
        if(context instanceof EntityCollisionContext entities && entities.getEntity()!=null
                && view.leases.getOrDefault(entities.getEntity().getId(),0L)>level.getGameTime())return Shapes.empty();
        return GhostGates.closedShape(state);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(VIEWS.isEmpty())return;
        var level=Minecraft.getInstance().level;if(level==null){VIEWS.clear();return;}
        long now=level.getGameTime();
        var entries=VIEWS.entrySet().iterator();
        while(entries.hasNext()) {var entry=entries.next();var view=entry.getValue();var pos=BlockPos.of(entry.getKey());
            if(view.showing && now>=view.close){view.showing=false;set(level,pos,view.original);sound(level,pos,view.original,false);}
            view.leases.entrySet().removeIf(lease->{if(lease.getValue()<=now)return true;var entity=level.getEntity(lease.getKey());return entity!=null && entity.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>16;});
            if(view.leases.isEmpty()){if(view.showing)set(level,pos,view.original);entries.remove();}
        }
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event){if(event.getLevel() instanceof ClientLevel)VIEWS.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){VIEWS.clear();}
}
