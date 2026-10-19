// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.graydients.tlmtweaks.client.ClientSync;
import io.github.graydients.tlmtweaks.jobs.AnimalConfigMenu;
import io.github.graydients.tlmtweaks.jobs.AnimalSettings;
import io.github.graydients.tlmtweaks.jobs.JobConfig;
import io.github.graydients.tlmtweaks.jobs.JobProtocol;
import io.github.graydients.tlmtweaks.sync.Protocol;
import io.github.graydients.tlmtweaks.sync.CustomProtocol;
import io.github.graydients.tlmtweaks.sync.CustomServer;
import io.github.graydients.tlmtweaks.client.CustomClient;
import io.github.graydients.tlmtweaks.client.GhostGateView;
import io.github.graydients.tlmtweaks.client.ClothSleepConfig;
import io.github.graydients.tlmtweaks.client.ClothPickupConfig;
import io.github.graydients.tlmtweaks.client.ClothWorkConfig;
import io.github.graydients.tlmtweaks.client.ClothScheduleConfig;
import io.github.graydients.tlmtweaks.schedule.ScheduleConfig;
import io.github.graydients.tlmtweaks.schedule.ScheduleProtocol;
import io.github.graydients.tlmtweaks.permissions.PermissionConfig;
import io.github.graydients.tlmtweaks.permissions.PermissionProtocol;
import io.github.graydients.tlmtweaks.client.ClothPermissionConfig;
import io.github.graydients.tlmtweaks.pickup.PickupConfig;
import io.github.graydients.tlmtweaks.pickup.PickupProtocol;
import io.github.graydients.tlmtweaks.sleep.MaidSleep;
import io.github.graydients.tlmtweaks.sleep.SleepConfig;
import io.github.graydients.tlmtweaks.sleep.SleepProtocol;
import io.github.graydients.tlmtweaks.gates.GateProtocol;
import io.github.graydients.tlmtweaks.gates.GhostGates;
import io.github.graydients.tlmtweaks.station.StationContent;
import io.github.graydients.tlmtweaks.station.StationProtocol;
import net.minecraft.network.protocol.PacketFlow;
import io.github.graydients.tlmtweaks.sync.ServerSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MaidTweaks.ID)
public final class MaidTweaks {
    public static final String ID = BuildInfo.MOD_ID;
    public static final String VERSION = BuildInfo.VERSION;
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public MaidTweaks(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SyncConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, JobConfig.SPEC, ID + "-jobs.toml");
        container.registerConfig(ModConfig.Type.SERVER, ScheduleConfig.SPEC, ID + "-schedule.toml");
        container.registerConfig(ModConfig.Type.SERVER, SleepConfig.SPEC, ID + "-sleep.toml");
        container.registerConfig(ModConfig.Type.SERVER, PickupConfig.SPEC, ID + "-pickup.toml");
        container.registerConfig(ModConfig.Type.SERVER, PermissionConfig.SPEC, ID + "-permissions.toml");
        AnimalSettings.initialize();
        GhostGates.initialize();
        MaidSleep.initialize();
        StationContent.register(bus);
        AnimalConfigMenu.MENUS.register(bus);
        bus.addListener(SyncConfig::changed);
        bus.addListener(JobConfig::changed);
        bus.addListener(ScheduleConfig::changed);
        bus.addListener(SleepConfig::changed);
        bus.addListener(PickupConfig::changed);
        bus.addListener(PermissionConfig::changed);
        bus.addListener(MaidTweaks::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("15");
        registrar.playToServer(ScheduleProtocol.Set.TYPE, ScheduleProtocol.Set.CODEC, ScheduleProtocol::handle);
        registrar.playToClient(ScheduleProtocol.State.TYPE, ScheduleProtocol.State.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothScheduleConfig.receive(data));
        });
        registrar.playToServer(JobProtocol.SetWork.TYPE, JobProtocol.SetWork.CODEC, JobProtocol::handle);
        registrar.playToClient(JobProtocol.WorkState.TYPE, JobProtocol.WorkState.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothWorkConfig.receive(data));
        });
        registrar.playToServer(PermissionProtocol.Set.TYPE, PermissionProtocol.Set.CODEC, PermissionProtocol::handle);
        registrar.playToClient(PermissionProtocol.State.TYPE, PermissionProtocol.State.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothPermissionConfig.receive(data));
        });
        registrar.playToServer(PickupProtocol.Set.TYPE, PickupProtocol.Set.CODEC, PickupProtocol::handle);
        registrar.playToClient(PickupProtocol.State.TYPE, PickupProtocol.State.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothPickupConfig.receive(data));
        });
        registrar.playToServer(SleepProtocol.Set.TYPE, SleepProtocol.Set.CODEC, SleepProtocol::handle);
        registrar.playToClient(SleepProtocol.State.TYPE, SleepProtocol.State.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothSleepConfig.receive(data));
        });
        registrar.playToClient(SleepProtocol.Status.TYPE, SleepProtocol.Status.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClothSleepConfig.receive(data));
        });
        registrar.playToClient(StationProtocol.Animals.TYPE, StationProtocol.Animals.CODEC, (data, context) ->
                context.enqueueWork(() -> StationProtocol.receive(data, context.player())));
        registrar.playToServer(StationProtocol.Configure.TYPE, StationProtocol.Configure.CODEC, StationProtocol::handle);
        registrar.playToServer(GateProtocol.SetMode.TYPE, GateProtocol.SetMode.CODEC, GateProtocol::handle);
        registrar.playToClient(GateProtocol.Flash.TYPE, GateProtocol.Flash.CODEC, (data, context) -> { if (FMLEnvironment.dist == Dist.CLIENT) GhostGateView.flash(data); });
        registrar.playToServer(CustomProtocol.Offer.TYPE, CustomProtocol.Offer.CODEC, CustomServer::offer);
        registrar.playToClient(CustomProtocol.Manifest.TYPE, CustomProtocol.Manifest.CODEC, (data, context) -> { if (FMLEnvironment.dist == Dist.CLIENT) CustomClient.manifest(data); });
        registrar.playBidirectional(CustomProtocol.Pull.TYPE, CustomProtocol.Pull.CODEC, (data, context) -> {
            if (context.flow() == PacketFlow.SERVERBOUND) CustomServer.pull(data, context); else if (FMLEnvironment.dist == Dist.CLIENT) CustomClient.pull(data);
        });
        registrar.playBidirectional(CustomProtocol.Chunk.TYPE, CustomProtocol.Chunk.CODEC, (data, context) -> {
            if (context.flow() == PacketFlow.SERVERBOUND) CustomServer.chunk(data, context); else if (FMLEnvironment.dist == Dist.CLIENT) CustomClient.chunk(data);
        });
        registrar.playBidirectional(CustomProtocol.Abort.TYPE, CustomProtocol.Abort.CODEC, (data, context) -> {
            if (context.flow() == PacketFlow.SERVERBOUND) CustomServer.abort(data, context); else if (FMLEnvironment.dist == Dist.CLIENT) CustomClient.abort(data);
        });
        registrar.playToServer(JobProtocol.SetAnimal.TYPE, JobProtocol.SetAnimal.CODEC, JobProtocol::handle);
        registrar.playToServer(Protocol.Hello.TYPE, Protocol.Hello.CODEC, ServerSync::hello);
        registrar.playToServer(Protocol.Offer.TYPE, Protocol.Offer.CODEC, ServerSync::offer);
        registrar.playToClient(Protocol.Policy.TYPE, Protocol.Policy.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientSync.policy(data);
        });
        registrar.playToClient(Protocol.Manifest.TYPE, Protocol.Manifest.CODEC, (data, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientSync.manifest(data);
        });
    }
}
