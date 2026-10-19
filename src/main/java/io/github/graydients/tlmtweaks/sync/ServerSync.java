// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.SyncConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@EventBusSubscriber(modid = MaidTweaks.ID)
public final class ServerSync {
    private static volatile ServerSync active;
    private final MinecraftServer server;
    private final Path packFolder = Path.of("tlm_custom_pack");
    private final ExecutorService io = PackFiles.worker("Graydients maid packs server");
    private final Catalogue catalogue = new Catalogue();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> optedIn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();
    private volatile long policyGeneration;
    private final Map<UUID, long[]> requestCounts = new LinkedHashMap<>(); // server thread
    private ServerJournal journal; // IO worker
    private volatile PackEntry.Registry published;

    private ServerSync(MinecraftServer server) { this.server = server; }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerSync manager = new ServerSync(event.getServer());
        active = manager;
        manager.io.execute(() -> {
            try {
                manager.journal = new ServerJournal(Path.of("config"), manager.packFolder);
                manager.published = manager.journal.manifest();
                manager.server.execute(() -> { if (active == manager) manager.broadcast(); });
                MaidTweaks.LOG.info("Official maid pack sync ready with {} verified packs; history retained", manager.published.packs().size());
            } catch (Exception error) {
                MaidTweaks.LOG.error("Could not initialize maid pack sharing; existing journals preserved", error);
            }
        });
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ServerSync manager = active;
        if (manager != null && manager.server == event.getServer()) {
            active = null;
            manager.io.shutdownNow();
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerSync manager = active;
        if (manager != null) {
            manager.optedIn.remove(event.getEntity().getUUID());
            manager.sessions.remove(event.getEntity().getUUID());
            manager.requestCounts.remove(event.getEntity().getUUID());
        }
    }

    public static void policyChanged() {
        ServerSync manager = active;
        if (manager != null) {
            manager.policyGeneration++;
            manager.server.execute(() -> { if (active == manager) manager.broadcast(); });
        }
    }

    public static String serverId() { var manager = active; return manager == null || manager.published == null ? null : manager.published.serverId(); }

    public static void hello(Protocol.Hello hello, IPayloadContext context) {
        ServerSync manager = active;
        if (manager == null || !(context.player() instanceof ServerPlayer player)) return;
        CustomServer.hello(player, hello);
        if (!hello.enabled()) {
            manager.optedIn.remove(player.getUUID());
            manager.sessions.remove(player.getUUID());
            return;
        }
        manager.optedIn.add(player.getUUID());
        manager.sessions.computeIfAbsent(player.getUUID(), key -> new Object());
        manager.send(player);
    }

    public static void offer(Protocol.Offer offer, IPayloadContext context) {
        ServerSync manager = active;
        if (manager == null || !SyncConfig.enabled() || manager.published == null
                || !(context.player() instanceof ServerPlayer player) || !manager.optedIn.contains(player.getUUID())
                || !PackEntry.safeFileName(offer.fileName()) || offer.checksum() < 0 || offer.checksum() > 0xffffffffL) return;
        long now = System.currentTimeMillis();
        long[] count = manager.requestCounts.computeIfAbsent(player.getUUID(), id -> new long[]{now, 0});
        if (now - count[0] >= 60_000) { count[0] = now; count[1] = 0; }
        if (++count[1] > 32 || !manager.pending.add(offer.fileName())) return;
        UUID sender = player.getUUID();
        Object session = manager.sessions.get(sender);
        long policy = manager.policyGeneration;
        try { manager.io.execute(() -> manager.accept(offer, sender, session, policy)); }
        catch (RejectedExecutionException full) { manager.pending.remove(offer.fileName()); }
    }

    private boolean allowed(UUID sender, Object session, long policy) {
        return active == this && SyncConfig.enabled() && session != null
                && sessions.get(sender) == session && policyGeneration == policy;
    }

    private void accept(Protocol.Offer offer, UUID sender, Object session, long policy) {
        try {
            if (!allowed(sender, session, policy)) return;
            PackEntry offered = catalogue.find(offer.fileName(), offer.checksum());
            if (offered == null || !allowed(sender, session, policy)) return;
            PackEntry previous = journal.previous(offered.id());
            if (previous != null && previous.fileName().equals(offered.fileName()) && previous.checksum() == offered.checksum()
                    && journal.verified(previous)) return;
            if (previous == null && journal.size() >= 256) throw new IOException("Shared pack limit reached");
            if (previous != null && !previous.fileName().equals(offered.fileName())) {
                Path old = PackFiles.target(packFolder, previous.fileName());
                if (Files.exists(old) && !PackFiles.hash(old).equals(previous.sha256()))
                    throw new IOException("Previous server pack was manually modified");
            }
            String replace = previous != null && previous.fileName().equals(offered.fileName()) ? previous.sha256() : null;
            PackEntry approved = PackFiles.download(offered, packFolder, replace,
                    () -> allowed(sender, session, policy), PackFiles::get, journal::publish);
            published = journal.manifest();
            if (previous != null && !previous.fileName().equals(approved.fileName())) PackFiles.removeOwned(previous, packFolder);
            server.execute(() -> {
                if (active != this || !SyncConfig.enabled()) return;
                try { ServerCustomPackLoader.reloadPacks(); }
                catch (RuntimeException error) { MaidTweaks.LOG.error("Maid pack metadata reload failed", error); }
                broadcast();
                for (ServerPlayer player : server.getPlayerList().getPlayers())
                    if (optedIn.contains(player.getUUID())) player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".packs.official_shared", approved.fileName()));
            });
            MaidTweaks.LOG.info("Published official maid pack {} requested by {}", approved.fileName(), sender);
        } catch (Exception error) {
            MaidTweaks.LOG.warn("Could not share official maid pack {}: {}", offer.fileName(), error.toString());
            server.execute(() -> {
                if (!allowed(sender, session, policy)) return;
                ServerPlayer player = server.getPlayerList().getPlayer(sender);
                if (player != null) player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".packs.share_failed", offer.fileName()));
            });
        } finally { pending.remove(offer.fileName()); }
    }

    private void send(ServerPlayer player) {
        if (!optedIn.contains(player.getUUID()) || !player.connection.hasChannel(Protocol.Policy.TYPE)) return;
        boolean enabled = SyncConfig.enabled();
        PacketDistributor.sendToPlayer(player, new Protocol.Policy(enabled, SyncConfig.customTransferEnabled()));
        if (enabled && published != null && player.connection.hasChannel(Protocol.Manifest.TYPE))
            PacketDistributor.sendToPlayer(player, new Protocol.Manifest(MaidTweaks.JSON.toJson(published)));
        if (enabled) CustomServer.sendManifest(player);
    }

    private void broadcast() { for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player); }
}
