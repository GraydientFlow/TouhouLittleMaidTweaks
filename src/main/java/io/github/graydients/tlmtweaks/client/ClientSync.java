// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadInfo;
import com.github.tartaricacid.touhoulittlemaid.client.download.pojo.DownloadStatus;
import com.github.tartaricacid.touhoulittlemaid.client.event.ReloadResourceEvent;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import io.github.graydients.tlmtweaks.MaidTweaks;
import io.github.graydients.tlmtweaks.sync.ClientJournal;
import io.github.graydients.tlmtweaks.sync.ClientPreferences;
import io.github.graydients.tlmtweaks.sync.ConnectionSession;
import io.github.graydients.tlmtweaks.sync.ManagedPacks;
import io.github.graydients.tlmtweaks.sync.PackEntry;
import io.github.graydients.tlmtweaks.sync.PackFiles;
import io.github.graydients.tlmtweaks.sync.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@EventBusSubscriber(modid = MaidTweaks.ID, value = Dist.CLIENT)
public final class ClientSync {
    private static final ExecutorService IO = PackFiles.worker("Graydients maid packs client");
    private static final ClientPreferences PREFERENCES = new ClientPreferences();
    private static final ConnectionSession CONNECTION = new ConnectionSession();
    private static final ArrayDeque<Protocol.Offer> offers = new ArrayDeque<>(); // client thread
    private static volatile ClientJournal journal; // IO worker
    private static boolean initialized; // IO worker
    private static boolean preparing; // client thread
    private static boolean preferencesReady; // client thread
    private static Component preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.loading");
    private static volatile boolean customPolicy;
    private static boolean ready;
    private static boolean helloSent;
    private static int ticks;
    private static int pendingSyncs;
    private static int retriesRemaining;
    private static boolean retryNeeded;
    private static PackEntry.Registry desired;
    private static volatile String latestManifest = "";

    private static Path configRoot() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config"); }
    private static Path preferencesFile() { return configRoot().resolve(MaidTweaks.ID).resolve("server-preferences.json"); }

    private static void initialize() throws java.io.IOException {
        if (initialized) return;
        journal = new ClientJournal(configRoot());
        PREFERENCES.load(preferencesFile());
        initialized = true;
    }

    public static void prepare() {
        if (preparing || preferencesReady) return;
        preparing = true;
        try {
            IO.execute(() -> {
                try {
                    initialize();
                    Minecraft.getInstance().execute(() -> {
                        preferencesReady = true;
                        preparing = false;
                        preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.ready");
                        beginConnection();
                    });
                } catch (Exception error) {
                    MaidTweaks.LOG.error("Maid pack sync journals could not be read; sharing disabled", error);
                    Minecraft.getInstance().execute(() -> {
                        preparing = false;
                        preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.read_failed");
                    });
                }
            });
        } catch (RejectedExecutionException full) { preparing = false; }
    }

    public static boolean preferencesReady() { return preferencesReady; }
    public static Component preferenceStatus() { return preferenceStatus; }
    public static ClientPreferences.Consent preference(String key) { return PREFERENCES.get(key); }

    public static String addressKey(String address) {
        if (address == null || address.isBlank() || !ServerAddress.isValidAddress(address)) return null;
        ServerAddress parsed = ServerAddress.parseString(address.strip());
        try { return ClientPreferences.key(parsed.getHost(), parsed.getPort()); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public static boolean localWorldActive() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getSingleplayerServer() != null && minecraft.getConnection() != null;
    }

    public static String currentScopeKey() {
        var minecraft = Minecraft.getInstance();
        if (localWorldActive())
            return ClientPreferences.worldKey(minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        var server = minecraft.getCurrentServer();
        return server == null || minecraft.getConnection() == null ? null : addressKey(server.ip);
    }

    public static void setPreference(String key, ClientPreferences.Consent consent) {
        if (!preferencesReady) return;
        PREFERENCES.set(key, consent);
        if (key.equals(currentScopeKey())) {
            var connection = Minecraft.getInstance().getConnection();
            if (!consent.enabled() && connection != null && connection.hasChannel(Protocol.Hello.TYPE))
                PacketDistributor.sendToServer(new Protocol.Hello(false, false));
            beginConnection();
        }
        preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.saving");
        try {
            IO.execute(() -> {
                try {
                    PREFERENCES.save(preferencesFile());
                    Minecraft.getInstance().execute(() -> preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.saved"));
                } catch (Exception error) {
                    MaidTweaks.LOG.error("Could not save per-server maid sync preferences", error);
                    Minecraft.getInstance().execute(() -> preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.save_failed"));
                }
            });
        } catch (RejectedExecutionException full) { preferenceStatus = Component.translatable(MaidTweaks.ID + ".settings.status.queue_full"); }
    }

    public static void completedUiDownload(DownloadInfo info, File file) {
        if (info.getStatus() != DownloadStatus.DOWNLOADED || !PackEntry.safeFileName(info.getFileName())) return;
        String name = info.getFileName();
        long checksum = info.getChecksum();
        ConnectionSession.Token token = CONNECTION.token();
        try {
            IO.execute(() -> {
                try {
                    initialize();
                    if (!Files.isRegularFile(file.toPath()) || Files.size(file.toPath()) > PackEntry.MAX_BYTES
                            || PackFiles.checksum(file.toPath()) != checksum) return;
                    journal.recordDownload(name, checksum);
                    Minecraft.getInstance().execute(() -> {
                        if (ready && CONNECTION.allowed(token)) offers.add(new Protocol.Offer(name, checksum));
                    });
                } catch (Exception error) { MaidTweaks.LOG.warn("Could not record completed maid pack download", error); }
            });
        } catch (RejectedExecutionException full) { MaidTweaks.LOG.warn("Maid pack client queue is full"); }
    }

    public static boolean customAllowed() { var token = CONNECTION.token(); return CONNECTION.allowed(token) && token.consent().customPacks() && customPolicy; }
    public static boolean uploadExcluded(String name) { return journal == null || journal.uploadExcluded(name); }

    private static void resetWork() {
        customPolicy = false;
        CustomClient.reset();
        ready = false;
        helloSent = false;
        ticks = 0;
        latestManifest = "";
        pendingSyncs = 0;
        desired = null;
        retryNeeded = false;
        offers.clear();
    }

    private static void beginConnection() {
        String key = currentScopeKey();
        CONNECTION.begin(key == null ? "" : key, preferencesReady && key != null ? PREFERENCES.get(key) : ClientPreferences.Consent.OFF);
        resetWork();
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) { beginConnection(); prepare(); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        CONNECTION.begin("", ClientPreferences.Consent.OFF);
        resetWork();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ConnectionSession.Token token = CONNECTION.token();
        if (!preferencesReady || !token.consent().enabled()) return;
        var localServer = Minecraft.getInstance().getSingleplayerServer();
        if (localServer != null && !localServer.isPublished()) return;
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || Minecraft.getInstance().player == null || !connection.hasChannel(Protocol.Hello.TYPE)) return;
        ticks++;
        if (!ready && (!helloSent || ticks % 100 == 0)) {
            helloSent = true;
            PacketDistributor.sendToServer(new Protocol.Hello(true, token.consent().customPacks()));
        }
        if (!CONNECTION.allowed(token)) return;
        if (ready && ticks % 40 == 0 && !offers.isEmpty()) PacketDistributor.sendToServer(offers.removeFirst());
        if (ready && retryNeeded && pendingSyncs == 0 && retriesRemaining > 0 && desired != null && ticks % 1200 == 0) {
            retriesRemaining--;
            queueSync(desired, token, false);
        }
    }

    public static void policy(Protocol.Policy policy) {
        if (!CONNECTION.token().consent().enabled()) return;
        ConnectionSession.Token before = CONNECTION.token();
        CONNECTION.policy(policy.enabled());
        if (before != CONNECTION.token()) resetWork();
        customPolicy = policy.customTransfer();
        CustomClient.policy(customAllowed());
    }

    public static void manifest(Protocol.Manifest message) {
        ConnectionSession.Token token = CONNECTION.token();
        if (!CONNECTION.allowed(token) || message.json().equals(latestManifest)) return;
        final PackEntry.Registry registry;
        try { registry = MaidTweaks.JSON.fromJson(message.json(), PackEntry.Registry.class).validated(); }
        catch (RuntimeException invalid) { MaidTweaks.LOG.warn("Ignored invalid shared maid pack manifest", invalid); return; }
        latestManifest = message.json();
        desired = registry;
        retriesRemaining = 3;
        boolean first = !ready;
        ready = true;
        queueSync(registry, token, first);
    }

    private static void queueSync(PackEntry.Registry registry, ConnectionSession.Token token, boolean first) {
        String manifest = latestManifest;
        pendingSyncs++;
        try {
            IO.execute(() -> {
                boolean failed = false;
                boolean packsChanged = false;
                try {
                    if (!current(token, manifest)) return;
                    initialize();
                    if (first) {
                        List<Protocol.Offer> downloaded = journal.downloads().entrySet().stream()
                                .filter(e -> registry.packs().stream().noneMatch(p -> p.fileName().equals(e.getKey()) && p.checksum() == e.getValue()))
                                .map(e -> new Protocol.Offer(e.getKey(), e.getValue())).toList();
                        Minecraft.getInstance().execute(() -> { if (current(token, manifest) && ready) offers.addAll(downloaded); });
                    }
                    for (PackEntry pack : registry.packs()) {
                        if (!current(token, manifest)) break;
                        try {
                            boolean changed = ManagedPacks.install(pack, registry.serverId(), journal, CustomPackLoader.PACK_FOLDER,
                                    () -> current(token, manifest), PackFiles::get);
                            if (changed) {
                                packsChanged = true;
                                Minecraft.getInstance().execute(() -> {
                                    if (current(token, manifest)) notifyPlayer("official_synced", pack.fileName());
                                });
                            }
                        } catch (InterruptedException cancelled) { break; }
                        catch (Exception error) {
                            failed = true;
                            MaidTweaks.LOG.warn("Could not sync maid pack {}: {}", pack.fileName(), error.toString());
                            Minecraft.getInstance().execute(() -> {
                                if (current(token, manifest)) notifyPlayer("sync_failed", pack.fileName());
                            });
                        }
                    }
                    if (packsChanged && current(token, manifest)) Minecraft.getInstance().execute(() -> {
                        if (current(token, manifest) && Minecraft.getInstance().player != null) ReloadResourceEvent.asyncReloadAllPack();
                    });
                } catch (Exception error) {
                    failed = true;
                    MaidTweaks.LOG.warn("Could not initialize maid pack client", error);
                } finally {
                    boolean result = failed;
                    Minecraft.getInstance().execute(() -> {
                        if (CONNECTION.token() == token) { pendingSyncs--; if (current(token, manifest)) retryNeeded = result; }
                    });
                }
            });
        } catch (RejectedExecutionException full) {
            pendingSyncs--;
            retryNeeded = true;
            MaidTweaks.LOG.warn("Maid pack client queue is full");
        }
    }

    private static boolean current(ConnectionSession.Token token, String manifest) {
        return CONNECTION.allowed(token) && manifest.equals(latestManifest);
    }

    private static void notifyPlayer(String key, Object... args) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.translatable("message." + MaidTweaks.ID + ".packs." + key, args));
    }
}
