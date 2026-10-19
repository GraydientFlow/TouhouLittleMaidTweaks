// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.client.event.ReloadResourceEvent;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sync.CustomEntry;
import io.github.graydients.tlmtweaks.sync.CustomFiles;
import io.github.graydients.tlmtweaks.sync.CustomOwnership;
import io.github.graydients.tlmtweaks.sync.CustomProtocol;
import io.github.graydients.tlmtweaks.sync.PackEntry;
import io.github.graydients.tlmtweaks.sync.PackFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Connection-scoped consent, background discovery, and serial bounded transfers. */
@EventBusSubscriber(modid=MaidTweaks.ID,value=Dist.CLIENT)
public final class CustomClient {
    private static final ExecutorService IO=PackFiles.worker("Graydients custom maid packs client");
    private static volatile long generation;
    private static volatile boolean enabled;
    private static final AtomicBoolean scanQueued=new AtomicBoolean();
    private static final AtomicBoolean cleanupPending=new AtomicBoolean();
    private static CustomOwnership ownership;
    private static WatchService watcher;
    private static final Map<String,CustomProtocol.Offer> local=new HashMap<>();
    private static final Map<String,String> attempted=new HashMap<>();
    private static final ArrayDeque<CustomEntry> queue=new ArrayDeque<>();
    private static CustomEntry.Manifest manifest;
    private static CustomFiles.Incoming incoming;
    private static UUID ticket;
    private static String outgoing;
    private static long outgoingTouched;
    private static int ticks;
    private static boolean reloadPending;
    private CustomClient(){}
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(MaidTweaks.ID);}
    private static boolean allowed(long token){return enabled && generation==token && ClientSync.customAllowed();}
    private static boolean execute(Runnable action){try{IO.execute(()->{if(cleanupPending.getAndSet(false))cancel();action.run();});return true;}catch(RejectedExecutionException full){MaidTweaks.LOG.warn("Custom pack client queue full");return false;}}
    private static void send(long token,CustomPacketPayload packet){Minecraft.getInstance().execute(()->{if(allowed(token))PacketDistributor.sendToServer(packet);});}
    public static void reset(){enabled=false;generation++;cleanupPending.set(true);execute(()->{});}
    public static void policy(boolean on){
        if(!on){reset();return;}if(enabled)return;enabled=true;long token=generation;execute(()->{
            if(!allowed(token))return;
            try{if(ownership==null)ownership=new CustomOwnership(root());if(watcher==null){Files.createDirectories(CustomPackLoader.PACK_FOLDER);watcher=CustomPackLoader.PACK_FOLDER.getFileSystem().newWatchService();CustomPackLoader.PACK_FOLDER.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_DELETE);}if(allowed(token))scan(token);}
            catch(Exception error){enabled=false;MaidTweaks.LOG.error("Custom pack sharing could not initialize",error);}
        });
    }
    private static void cancel(){if(incoming!=null)try{incoming.close();}catch(IOException error){MaidTweaks.LOG.warn("Custom staging cleanup failed",error);}incoming=null;ticket=null;outgoing=null;manifest=null;queue.clear();local.clear();attempted.clear();reloadPending=false;scanQueued.set(false);
        if(watcher!=null){try{watcher.close();}catch(IOException error){MaidTweaks.LOG.warn("Custom watch cleanup failed",error);}watcher=null;}
    }
    public static void requestScan(){long token=generation;if(!allowed(token) || !scanQueued.compareAndSet(false,true))return;if(!execute(()->{try{if(allowed(token))scan(token);}finally{scanQueued.set(false);}}))scanQueued.set(false);}
    private static void scan(long token){
        try(var paths=Files.list(CustomPackLoader.PACK_FOLDER)) {
            var discovered=new HashMap<String,CustomProtocol.Offer>();
            for(var path:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")).sorted().limit(256).toList()) {
                if(!allowed(token))return;String name=path.getFileName().toString();
                if(!PackEntry.safeFileName(name) || ownership.excluded(name) || ClientSync.uploadExcluded(name)
                        || name.matches("graydients-tlm-custom-[0-9a-f-]{36}\\.zip"))continue;
                try {
                    long size=Files.size(path),modified=Files.getLastModifiedTime(path).toMillis();if(size<=0 || size>PackEntry.MAX_BYTES)continue;
                    String hash=PackFiles.hash(path);var entry=new CustomEntry(UUID.randomUUID().toString(),name,hash,size);CustomFiles.validate(path,entry);
                    if(Files.size(path)!=size || Files.getLastModifiedTime(path).toMillis()!=modified)continue;
                    discovered.put(name,new CustomProtocol.Offer(name,hash,size));
                }catch(Exception invalid){MaidTweaks.LOG.debug("Custom pack {} was not offered: {}",name,invalid.toString());}
            }
            local.clear();local.putAll(discovered);nextOffer(token);
        }catch(IOException error){MaidTweaks.LOG.warn("Custom ZIP discovery failed: {}",error.toString());}
    }
    private static void nextOffer(long token){
        if(!allowed(token) || outgoing!=null)return;
        local.values().stream().sorted(java.util.Comparator.comparing(CustomProtocol.Offer::name)).filter(p->!p.hash().equals(attempted.get(p.name()))).findFirst().ifPresent(p->{outgoing=p.name();outgoingTouched=System.currentTimeMillis();send(token,p);});
    }
    public static void manifest(CustomProtocol.Manifest packet){long token=generation;if(!allowed(token))return;execute(()->{
        try {
            if(!allowed(token))return;var next=MaidTweaks.JSON.fromJson(packet.json(),CustomEntry.Manifest.class).validated();
            manifest=next;
            if(outgoing!=null){var offered=local.get(outgoing);if(offered!=null && next.packs().stream().anyMatch(e->e.name().equals(offered.name()) && e.sha256().equals(offered.hash()))){attempted.put(outgoing,offered.hash());outgoing=null;nextOffer(token);}}
            queue.clear();for(var entry:next.packs()) {
                if(incoming!=null && incoming.entry.equals(entry))continue;
                if(local.values().stream().anyMatch(o->o.hash().equals(entry.sha256())))continue;
                var owned=ownership.previous(next.serverId(),entry.id());Path target=PackFiles.target(CustomPackLoader.PACK_FOLDER,entry.fileName());
                if(owned!=null && owned.equals(entry) && Files.exists(target) && PackFiles.hash(target).equals(entry.sha256()))continue;
                queue.add(entry);
            }
            nextDownload(token);
        }catch(Exception error){MaidTweaks.LOG.warn("Ignored custom manifest: {}",error.toString());}
    });}
    private static void nextDownload(long token){
        if(!allowed(token) || incoming!=null || manifest==null)return;
        while(!queue.isEmpty()){
            var entry=queue.removeFirst();var previous=ownership.previous(manifest.serverId(),entry.id());
            try {
                Path target=PackFiles.target(CustomPackLoader.PACK_FOLDER,entry.fileName());
                if(Files.exists(target) && (previous==null || !PackFiles.hash(target).equals(previous.sha256()))) {notice(token,"local_conflict",entry.name());continue;}
                incoming=new CustomFiles.Incoming(entry,root().resolve("custom-staging"),token);ticket=UUID.randomUUID();send(token,new CustomProtocol.Pull(ticket,entry,0));return;
            }catch(IOException error){MaidTweaks.LOG.warn("Could not start custom download: {}",error.toString());}
        }
        if(reloadPending){reloadPending=false;Minecraft.getInstance().execute(()->{if(allowed(token) && Minecraft.getInstance().player!=null)ReloadResourceEvent.asyncReloadAllPack();});}
    }
    public static void pull(CustomProtocol.Pull packet){long token=generation;if(!allowed(token))return;execute(()->{
        try {
            if(!allowed(token))return;packet.pack().validated();var offered=local.get(packet.pack().name());
            if(offered==null || !offered.hash().equals(packet.pack().sha256()) || offered.bytes()!=packet.pack().bytes() || !packet.pack().name().equals(outgoing))return;
            outgoingTouched=System.currentTimeMillis();var file=PackFiles.target(CustomPackLoader.PACK_FOLDER,offered.name());
            byte[] bytes=CustomFiles.chunk(file,packet.offset(),offered.bytes());send(token,new CustomProtocol.Chunk(packet.ticket(),packet.offset(),bytes));
        }catch(Exception error){send(token,new CustomProtocol.Abort(packet.ticket()));if(outgoing!=null){var old=local.get(outgoing);if(old!=null)attempted.put(outgoing,old.hash());outgoing=null;}nextOffer(token);}
    });}
    public static void chunk(CustomProtocol.Chunk packet){long token=generation;if(!allowed(token))return;execute(()->{
        if(incoming==null || !packet.ticket().equals(ticket))return;
        try {
            if(!allowed(token) || incoming.generation!=token)throw new IOException("Consent ended");
            if(!incoming.append(packet)){send(token,new CustomProtocol.Pull(ticket,incoming.entry,incoming.offset));return;}
            CustomFiles.validate(incoming.file,incoming.entry);var current=manifest;var entry=incoming.entry;
            if(!allowed(token) || current==null || !current.packs().contains(entry))throw new IOException("Custom manifest changed");
            var previous=ownership.previous(current.serverId(),entry.id());
            CustomFiles.install(entry,previous,incoming.file,CustomPackLoader.PACK_FOLDER,()->allowed(token) && manifest==current,()->ownership.own(current.serverId(),entry));
            incoming.close();incoming=null;ticket=null;reloadPending=true;notice(token,"custom_synced",entry.name());nextDownload(token);
        }catch(Exception error){MaidTweaks.LOG.warn("Custom download preserved local files: {}",error.toString());send(token,new CustomProtocol.Abort(packet.ticket()));closeIncoming();nextDownload(token);}
    });}
    private static void closeIncoming(){if(incoming!=null)try{incoming.close();}catch(IOException error){MaidTweaks.LOG.warn("Could not remove custom staging",error);}incoming=null;ticket=null;}
    public static void abort(CustomProtocol.Abort packet){long token=generation;execute(()->{if(!allowed(token))return;if(packet.ticket().equals(ticket)){closeIncoming();nextDownload(token);}else if(outgoing!=null){var offer=local.get(outgoing);if(offer!=null)attempted.put(outgoing,offer.hash());outgoing=null;nextOffer(token);}});}
    private static void notice(long token,String key,Object...args){Minecraft.getInstance().execute(()->{if(allowed(token) && Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendSystemMessage(Component.translatable("message."+MaidTweaks.ID+".packs."+key,args));});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled)return;long token=generation;
        if(++ticks%20==0)execute(()->{if(!allowed(token))return;boolean changed=false;if(watcher!=null){java.nio.file.WatchKey key;while((key=watcher.poll())!=null){changed|=!key.pollEvents().isEmpty();key.reset();}}
            if(changed)scan(token);
            long now=System.currentTimeMillis();if(outgoing!=null && now-outgoingTouched>60000){outgoing=null;nextOffer(token);}if(incoming!=null && now-incoming.touched>60000){send(token,new CustomProtocol.Abort(ticket));closeIncoming();nextDownload(token);}
        });
    }
}
