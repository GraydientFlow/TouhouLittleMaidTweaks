// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.client;

import io.github.graydients.tlmtweaks.station.StationBlockEntity;
import io.github.graydients.tlmtweaks.station.StationFenceBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Adds the preserved fence at chunk mesh construction, without a per-frame block entity renderer. */
final class StationFenceModel extends BakedModelWrapper<BakedModel> {
    StationFenceModel(BakedModel model) { super(model); }
    private static BlockState fence(BlockState state, ModelData data) {
        BlockState saved = data.get(StationBlockEntity.FENCE_MODEL);
        return state != null && saved != null && saved.getBlock() instanceof FenceBlock
                ? StationFenceBlock.copyConnections(state, saved) : null;
    }
    private static BakedModel model(BlockState state) { return Minecraft.getInstance().getBlockRenderer().getBlockModel(state); }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        var station = super.getQuads(state, side, random, data, type);
        BlockState fence = fence(state, data);
        if (fence == null) return station;
        BakedModel model = model(fence);
        if (type != null && !model.getRenderTypes(fence, random, ModelData.EMPTY).contains(type)) return station;
        var rails = model.getQuads(fence, side, random, ModelData.EMPTY, type);
        if (station.isEmpty()) return rails;
        if (rails.isEmpty()) return station;
        var combined = new ArrayList<BakedQuad>(station.size() + rails.size());
        combined.addAll(station); combined.addAll(rails); return combined;
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        var types = super.getRenderTypes(state, random, data);
        BlockState fence = fence(state, data);
        return fence == null ? types : ChunkRenderTypeSet.union(types, model(fence).getRenderTypes(fence, random, ModelData.EMPTY));
    }
}
