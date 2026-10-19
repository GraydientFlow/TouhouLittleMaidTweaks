// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.gates;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import io.github.graydients.tlmtweaks.MaidTweaks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Gate-local passage leases. Server OPEN is never changed for a ghost crossing. */
@EventBusSubscriber(modid=MaidTweaks.ID)
public final class GhostGates {
    // TLM task data requires a compound value for both NBT persistence and sync.
    public static final TaskDataKey<Boolean> KEY=new TaskDataRegister().register(ResourceLocation.fromNamespaceAndPath(MaidTweaks.ID,"ghost_gate"),Codec.BOOL.fieldOf("enabled").codec());
    private static final Map<ServerLevel,Map<Long,Map<UUID,Long>>> ACTIVE=new HashMap<>();
    private static final VoxelShape X=Block.box(6,0,0,10,24,16), Z=Block.box(0,0,6,16,24,10);
    private GhostGates() {}
    public static void initialize() {}
    public static int mode(EntityMaid maid) { return !maid.getConfigManager().isOpenFenceGate()?0:Boolean.TRUE.equals(maid.getData(KEY))?2:1; }
    public static void setMode(EntityMaid maid,int mode) { maid.setAndSyncData(KEY,mode==2);maid.getConfigManager().setOpenFenceGate(mode!=0); }
    public static boolean intercept(Entity entity,Level level,BlockState state,BlockPos pos,boolean open) {
        if(!(entity instanceof EntityMaid maid) || mode(maid)!=2 || !(level instanceof ServerLevel server)) return false;
        if(!open) return !state.getValue(FenceGateBlock.OPEN);
        if(!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN)) return false;
        var leases=ACTIVE.computeIfAbsent(server,l->new HashMap<>()).computeIfAbsent(pos.asLong(),p->new HashMap<>());
        boolean first=!leases.containsKey(maid.getUUID()); leases.put(maid.getUUID(),level.getGameTime()+80);
        if(first) PacketDistributor.sendToPlayersTrackingChunk(server,new ChunkPos(pos),new GateProtocol.Flash(pos,maid.getId(),80));
        return true;
    }
    public static VoxelShape closedShape(BlockState state) { return state.getValue(FenceGateBlock.FACING).getAxis()==Direction.Axis.X?X:Z; }
    public static VoxelShape collision(Level level,BlockPos pos,BlockState state,CollisionContext context) {
        if(!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN)
                || !(level instanceof ServerLevel server)) return null;
        var gates=ACTIVE.get(server); var leases=gates==null?null:gates.get(pos.asLong()); if(leases==null) return null;
        if(context instanceof EntityCollisionContext entities && entities.getEntity() instanceof EntityMaid maid && mode(maid)==2
                && leases.getOrDefault(maid.getUUID(),0L)>level.getGameTime()) return Shapes.empty();
        return closedShape(state);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for(var levels:ACTIVE.entrySet()) {
            var level=levels.getKey(); long now=level.getGameTime(); var gates=levels.getValue().entrySet().iterator();
            while(gates.hasNext()) { var gate=gates.next(); var pos=BlockPos.of(gate.getKey());
                gate.getValue().entrySet().removeIf(lease->{if(lease.getValue()<=now)return true;var entity=level.getEntity(lease.getKey());return entity==null || entity.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>16;});
                if(gate.getValue().isEmpty()) gates.remove();
            }
        }
        ACTIVE.entrySet().removeIf(entry->entry.getValue().isEmpty());
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) { ACTIVE.remove(event.getLevel()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
