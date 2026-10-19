// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.SyncConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates uploads independently and relays them only to individually consenting peers. */
@EventBusSubscriber(modid=MaidTweaks.ID)
public final class CustomServer {
    private static volatile CustomServer active;
    private final MinecraftServer server;
    private final ExecutorService io=PackFiles.worker("Graydients custom maid packs server");
    private final Path root=Path.of("config",MaidTweaks.ID),cache=root.resolve("custom-cache");
    private final Map<UUID,Long> consent=new ConcurrentHashMap<>();
    private long nextEpoch; // server thread; no retained record for logged-out players
    private final Map<UUID,long[]> rates=new HashMap<>(); // server thread
    private final ArrayBlockingQueue<Outbound> network=new ArrayBlockingQueue<>(256);
    private final Map<UUID,Upload> uploads=new HashMap<>(); // IO thread
    private final Map<UUID,Download> downloads=new HashMap<>(); // IO thread
    private Map<String,CustomEntry> entries=new LinkedHashMap<>(); // IO thread
    private volatile List<CustomEntry> published=List.of();
    private volatile boolean ready;
    private volatile boolean stopping;
    private long cleanupTick;
    private record Upload(UUID sender,CustomFiles.Incoming incoming){}
    private record Download(UUID ticket,CustomEntry entry,int next,long epoch,long touched){}
    private record Outbound(UUID player,long epoch,CustomPacketPayload packet){}
    private CustomServer(MinecraftServer server){this.server=server;}
    private boolean allowed(UUID sender,long epoch){return ready && active==this && SyncConfig.customTransferEnabled() && consent.getOrDefault(sender,-1L)==epoch;}
    private void execute(Runnable task){try{io.execute(()->{if(stopping){cancelAll();return;}task.run();});}catch(RejectedExecutionException full){MaidTweaks.LOG.warn("Custom pack server queue full");}}
    private void send(UUID player,long epoch,CustomPacketPayload packet) {
        // Bound queued bytes as well as worker tasks; stalled peers expire normally.
        if (allowed(player,epoch)) network.offer(new Outbound(player,epoch,packet));
    }
    private void load() {
        try {
            Files.createDirectories(cache);Path journal=root.resolve("shared-custom-packs.json");
            if(Files.exists(journal)){
                CustomEntry[] loaded=MaidTweaks.JSON.fromJson(Files.readString(journal),CustomEntry[].class);
                if(loaded==null || loaded.length>CustomEntry.MAX_ENTRIES)throw new IOException("Invalid custom registry");
                for(var entry:loaded){entry.validated();if(entries.putIfAbsent(entry.id(),entry)!=null)throw new IOException("Duplicate custom identity");}
            }
            published=entries.values().stream().filter(e->{try{return Files.isRegularFile(cache.resolve(e.sha256()+".zip")) && PackFiles.hash(cache.resolve(e.sha256()+".zip")).equals(e.sha256());}catch(IOException error){return false;}}).toList();
            ready=true;server.execute(()->{if(active==this)broadcast();});
        }catch(Exception error){MaidTweaks.LOG.error("Custom sharing disabled: could not read registry",error);published=List.of();}
    }
    public static void hello(ServerPlayer player,Protocol.Hello hello){
        var m=active;if(m==null)return;UUID id=player.getUUID();
        boolean on=hello.enabled() && hello.customConsent();
        if(on && m.consent.containsKey(id)){m.sendManifest(player);return;}
        long epoch=++m.nextEpoch;m.consent.remove(id);if(on)m.consent.put(id,epoch);
        m.execute(()->m.cancel(id));if(on)m.sendManifest(player);
    }
    public static void sendManifest(ServerPlayer player){var m=active;if(m==null)return;var epoch=m.consent.get(player.getUUID());String id=ServerSync.serverId();if(epoch==null || id==null || !m.allowed(player.getUUID(),epoch))return;
        PacketDistributor.sendToPlayer(player,new CustomProtocol.Manifest(MaidTweaks.JSON.toJson(new CustomEntry.Manifest(id,m.published))));}
    private void broadcast(){for(var player:server.getPlayerList().getPlayers())sendManifest(player);}
    public static void policyChanged(){var m=active;if(m==null)return;m.server.execute(()->{if(!SyncConfig.customTransferEnabled())m.execute(m::cancelAll);else m.broadcast();});}
    public static void offer(CustomProtocol.Offer packet,IPayloadContext context){
        var m=active;if(m==null || !(context.player() instanceof ServerPlayer player))return;UUID sender=player.getUUID();Long epoch=m.consent.get(sender);
        if(epoch==null || !m.allowed(sender,epoch))return;
        final CustomEntry entry;try{entry=new CustomEntry(CustomEntry.identity(sender,packet.name()),packet.name(),packet.hash(),packet.bytes()).validated();}catch(RuntimeException invalid){return;}
        long now=System.currentTimeMillis();var rate=m.rates.computeIfAbsent(sender,k->new long[]{now,0});if(now-rate[0]>=60000){rate[0]=now;rate[1]=0;}if(++rate[1]>32)return;
        m.execute(()->m.accept(sender,epoch,entry));
    }
    private void accept(UUID sender,long epoch,CustomEntry entry){
        try {
            if(!allowed(sender,epoch) || uploads.size()>=4 || uploads.values().stream().anyMatch(u->u.sender.equals(sender)))return;
            var previous=entries.get(entry.id());if(previous!=null && previous.equals(entry) && published.contains(entry)){server.execute(this::broadcast);return;}
            if(previous==null && entries.size()>=CustomEntry.MAX_ENTRIES)return;
            var incoming=new CustomFiles.Incoming(entry,cache,epoch);UUID ticket=UUID.randomUUID();uploads.put(ticket,new Upload(sender,incoming));send(sender,epoch,new CustomProtocol.Pull(ticket,entry,0));
        }catch(Exception error){MaidTweaks.LOG.warn("Could not start custom upload: {}",error.toString());}
    }
    public static void chunk(CustomProtocol.Chunk packet,IPayloadContext context){var m=active;if(m==null || !(context.player() instanceof ServerPlayer player))return;UUID sender=player.getUUID();Long epoch=m.consent.get(sender);if(epoch==null || !m.allowed(sender,epoch))return;m.execute(()->{if(m.allowed(sender,epoch))m.receive(sender,packet);});}
    private void receive(UUID sender,CustomProtocol.Chunk packet){
        var upload=uploads.get(packet.ticket());if(upload==null || !upload.sender.equals(sender))return;var incoming=upload.incoming;
        try {
            if(!allowed(sender,incoming.generation))throw new IOException("Consent ended");
            if(!incoming.append(packet)){send(sender,incoming.generation,new CustomProtocol.Pull(packet.ticket(),incoming.entry,incoming.offset));return;}
            CustomFiles.validate(incoming.file,incoming.entry);
            if(!allowed(sender,incoming.generation))throw new IOException("Consent ended");
            Path finalFile=cache.resolve(incoming.entry.sha256()+".zip");
            CustomFiles.move(incoming.file,finalFile);
            var next=new LinkedHashMap<>(entries);var previous=next.put(incoming.entry.id(),incoming.entry);
            if(!allowed(sender,incoming.generation))throw new IOException("Consent ended");
            CustomFiles.install(incoming.entry,previous,finalFile,Path.of("tlm_custom_pack"),()->allowed(sender,incoming.generation),()->PackFiles.atomicJson(root.resolve("shared-custom-packs.json"),next.values()));
            entries=next;var visible=new java.util.ArrayList<>(published);visible.removeIf(e->e.id().equals(incoming.entry.id()));visible.add(incoming.entry);published=List.copyOf(visible);
            if(previous!=null && !previous.sha256().equals(incoming.entry.sha256()) && entries.values().stream().noneMatch(e->e.sha256().equals(previous.sha256())))Files.deleteIfExists(cache.resolve(previous.sha256()+".zip"));
            uploads.remove(packet.ticket());incoming.close();server.execute(()->{if(active==this){com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader.reloadPacks();broadcast();}});
            MaidTweaks.LOG.info("Shared custom maid pack {} from {}",incoming.entry.name(),sender);
        }catch(Exception error){discard(packet.ticket());send(sender,incoming.generation,new CustomProtocol.Abort(packet.ticket()));MaidTweaks.LOG.warn("Rejected custom maid upload: {}",error.toString());}
    }
    public static void pull(CustomProtocol.Pull packet,IPayloadContext context){var m=active;if(m==null || !(context.player() instanceof ServerPlayer player))return;UUID sender=player.getUUID();Long epoch=m.consent.get(sender);if(epoch==null || !m.allowed(sender,epoch))return;m.execute(()->m.read(sender,epoch,packet));}
    private void read(UUID sender,long epoch,CustomProtocol.Pull packet){
        try{
            if(!allowed(sender,epoch) || !packet.pack().equals(entries.get(packet.pack().id())))return;
            var state=downloads.get(sender);
            if(packet.offset()==0 && state==null){state=new Download(packet.ticket(),packet.pack(),0,epoch,System.currentTimeMillis());downloads.put(sender,state);}
            if(state==null || !state.ticket.equals(packet.ticket()) || !state.entry.equals(packet.pack()) || state.next!=packet.offset() || state.epoch!=epoch)throw new IOException("Invalid download sequence");
            var bytes=CustomFiles.chunk(cache.resolve(state.entry.sha256()+".zip"),packet.offset(),state.entry.bytes());
            int next=packet.offset()+bytes.length;if(next==state.entry.bytes())downloads.remove(sender);else downloads.put(sender,new Download(state.ticket,state.entry,next,epoch,System.currentTimeMillis()));
            send(sender,epoch,new CustomProtocol.Chunk(packet.ticket(),packet.offset(),bytes));
        }catch(Exception error){downloads.remove(sender);send(sender,epoch,new CustomProtocol.Abort(packet.ticket()));}
    }
    public static void abort(CustomProtocol.Abort packet,IPayloadContext context){var m=active;if(m==null || !(context.player() instanceof ServerPlayer player))return;UUID sender=player.getUUID();m.execute(()->{var upload=m.uploads.get(packet.ticket());if(upload!=null && upload.sender.equals(sender))m.discard(packet.ticket());var down=m.downloads.get(sender);if(down!=null && down.ticket.equals(packet.ticket()))m.downloads.remove(sender);});}
    private void discard(UUID ticket){var upload=uploads.remove(ticket);if(upload!=null)try{upload.incoming.close();}catch(IOException error){MaidTweaks.LOG.warn("Could not remove custom upload staging",error);}}
    private void cancel(UUID sender){for(var e:List.copyOf(uploads.entrySet()))if(e.getValue().sender.equals(sender))discard(e.getKey());downloads.remove(sender);}
    private void cancelAll(){for(UUID ticket:List.copyOf(uploads.keySet()))discard(ticket);downloads.clear();}
    @SubscribeEvent public static void started(ServerStartedEvent event){var m=new CustomServer(event.getServer());active=m;m.execute(m::load);}
    private void close(){stopping=true;network.clear();execute(()->{});io.shutdown();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){var m=active;if(m!=null && m.server==event.getServer()){active=null;m.close();}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){var m=active;if(m==null)return;UUID id=event.getEntity().getUUID();m.consent.remove(id);m.rates.remove(id);m.execute(()->m.cancel(id));}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var m=active;if(m==null)return;
        for(int i=0;i<4;i++) {
            var outgoing=m.network.poll();if(outgoing==null)break;
            var player=m.server.getPlayerList().getPlayer(outgoing.player);
            if(player!=null && m.allowed(outgoing.player,outgoing.epoch))PacketDistributor.sendToPlayer(player,outgoing.packet);
        }
        if(++m.cleanupTick%200==0)m.execute(()->{
            long now=System.currentTimeMillis();
            for(var entry:List.copyOf(m.uploads.entrySet()))if(now-entry.getValue().incoming.touched>60000 || !m.allowed(entry.getValue().sender,entry.getValue().incoming.generation))m.discard(entry.getKey());
            m.downloads.entrySet().removeIf(e->now-e.getValue().touched>60000 || !m.allowed(e.getKey(),e.getValue().epoch));
        });
    }
}
