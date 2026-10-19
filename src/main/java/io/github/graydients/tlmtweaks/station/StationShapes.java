// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two supports and a solid bucket, with the vanilla lectern's base and shelf shapes. */
final class StationShapes {
    private static final VoxelShape[] OUTLINES = new VoxelShape[8], COLLISIONS = new VoxelShape[8];
    static {
        // The three shelf steps from LecternBlock.SHAPE_NORTH, without its central post.
        VoxelShape shelf = Shapes.or(Block.box(0,10,1,16,14,5.333333),
                Block.box(0,12,5.333333,16,16,9.666667), Block.box(0,14,9.666667,16,18,14));
        for (int mounted=0; mounted<2; mounted++) {
            int z = mounted==0 ? 0 : -3;
            // Picking and collision need only the bucket's outer bounds, including its rim.
            // The hollow interior and decorative handle do not need separate shape parts.
            VoxelShape body = Shapes.or(Block.box(2,2,8,4,14,12), Block.box(12,2,8,14,14,12),
                    Block.box(4,2,3+z,12,8,9+z));
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                int index = facing.get2DDataValue() + mounted*4;
                VoxelShape turnedBody = rotate(body,facing);
                OUTLINES[index] = Shapes.or(LecternBlock.SHAPE_BASE,turnedBody,rotate(shelf,facing));
                COLLISIONS[index] = Shapes.or(LecternBlock.SHAPE_BASE,turnedBody,LecternBlock.SHAPE_TOP_PLATE);
            }
        }
    }
    private StationShapes() {}
    static VoxelShape outline(Direction facing, boolean mounted) { return OUTLINES[index(facing,mounted)]; }
    static VoxelShape collision(Direction facing, boolean mounted) { return COLLISIONS[index(facing,mounted)]; }
    private static int index(Direction facing, boolean mounted) { return facing.get2DDataValue() + (mounted?4:0); }

    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        int turns = (facing.get2DDataValue()+2)&3;
        if (turns==0) return shape;
        VoxelShape result = Shapes.empty();
        for (var box : shape.toAabbs()) {
            double x1=box.minX,z1=box.minZ,x2=box.maxX,z2=box.maxZ;
            for (int turn=0;turn<turns;turn++) {
                double nextX1=1-z2,nextX2=1-z1;
                z1=x1;z2=x2;x1=nextX1;x2=nextX2;
            }
            result=Shapes.or(result,Shapes.box(x1,box.minY,z1,x2,box.maxY,z2));
        }
        return result;
    }
}
