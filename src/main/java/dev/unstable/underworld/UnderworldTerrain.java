package dev.unstable.underworld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Version 3.3: terrain made from scratch over an EMPTY flat dimension.
 * The Nether is never accessed/mutated here. Existing v1/v2 Underworld chunks
 * are erased once and regenerated with the v3 layout; player builds in the
 * Underworld are NOT preserved during migration. Back up the world first.
 */
public final class UnderworldTerrain {
    private static final int VERSION_MARKER_Y = -187;
    private static final BlockState VERSION_MARKER = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SOUL_SAND = Blocks.SOUL_SAND.defaultBlockState();
    private static final BlockState SOUL_SOIL = Blocks.SOUL_SOIL.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    private static final BlockState NETHERRACK = Blocks.NETHERRACK.defaultBlockState();

    private UnderworldTerrain() { }

    public static void decorate(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(UnderworldMod.UNDERWORLD)) return;

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos marker = new BlockPos(startX + 1, VERSION_MARKER_Y, startZ + 1);
        if (chunk.getBlockState(marker).equals(VERSION_MARKER)) return;

        long seed = level.getSeed() ^ 0x4AD8C9BF0521DA5BL;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Newly created chunks from the empty FLAT generator cost almost
        // nothing to clear. Older v1/v2 chunks still have Overworld-derived
        // blocks, ores and caves, so we clear the WHOLE column before painting.
        boolean v32 = chunk.getBlockState(pos.set(startX + 1, -186, startZ + 1)).is(Blocks.RESPAWN_ANCHOR);
        boolean v31 = chunk.getBlockState(pos.set(startX + 1, -185, startZ + 1)).is(Blocks.LODESTONE);
        boolean v3 = chunk.getBlockState(pos.set(startX + 1, -184, startZ + 1)).is(Blocks.CRYING_OBSIDIAN);
        boolean legacy = !v3 && !v31 && !v32 && isLegacy(chunk, startX, startZ, pos);
        if (legacy) {
            for (int y = -192; y < 320; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        pos.set(startX + lx, y, startZ + lz);
                        if (!chunk.getBlockState(pos).isAir()) {
                            chunk.setBlockState(pos, AIR, 0);
                        }
                    }
                }
            }
        }

        // Replace v3.0/v3.1/v3.2 terrain and structures once. The old marker is removed
        // and every former v3 structure block is cleared before drawing v3.3.
        if (v3 || v31 || v32) {
            for (int y = 12; y <= 152; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        pos.set(startX + lx, y, startZ + lz);
                        if (!chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos, AIR, 0);
                    }
                }
            }
            if (v32) chunk.setBlockState(new BlockPos(startX + 1, -186, startZ + 1), AIR, 0);
            if (v3) chunk.setBlockState(new BlockPos(startX + 1, -184, startZ + 1), AIR, 0);
            if (v31) chunk.setBlockState(new BlockPos(startX + 1, -185, startZ + 1), AIR, 0);
        }

        for (int lx = 0; lx < 16; lx++) {
            int x = startX + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = startZ + lz;
                if (!UnderworldMath.hasLand(x, z, seed)) continue;

                int top = UnderworldMath.surfaceY(x, z, seed);
                int bottom = UnderworldMath.bottomY(x, z, seed);
                double region = UnderworldMath.geologicalRegion(x, z, seed);
                double detail = UnderworldMath.noise(x, z, 90, seed ^ 0xD34AL);

                for (int y = bottom; y <= top; y++) {
                    BlockState state;
                    int below = top - y;
                    if (below <= 3) {
                        // Wide, visually identifiable surface zones.
                        if (region > 0.14) state = BLACKSTONE;
                        else if (region < -0.14) state = SOUL_SAND;
                        else state = (detail > 0.30) ? SOUL_SAND : SOUL_SOIL;
                    } else if (below <= 9) {
                        if (region > 0.14) state = detail > 0.68 ? BASALT : BLACKSTONE;
                        else if (region < -0.14) state = SOUL_SOIL;
                        else state = SOUL_SOIL;
                    } else {
                        if (region > 0.14) state = BLACKSTONE;
                        else if (region < -0.14) state = (detail < -0.72 ? NETHERRACK : SOUL_SOIL);
                        else state = detail > 0.15 ? BASALT : BLACKSTONE;
                    }
                    pos.set(x, y, z);
                    if (!chunk.getBlockState(pos).equals(state)) {
                        chunk.setBlockState(pos, state, 0);
                    }
                }
            }
        }

        // Multi-chunk ruins are computed per chunk, with no recursive loading.
        UnderworldStructures.generate(chunk, seed);
        chunk.setBlockState(marker, VERSION_MARKER, 0);
        chunk.markUnsaved();
    }

    /** Detect previous versions without scanning every new empty chunk. */
    private static boolean isLegacy(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos p) {
        // v2 saved a gilded-blackstone marker at -182.
        if (chunk.getBlockState(p.set(x + 1, -182, z + 1)).is(Blocks.GILDED_BLACKSTONE)) return true;
        // Old Overworld-generated chunks have bedrock at -64 or terrain at Y~64.
        int[] xs = {2, 7, 13};
        int[] zs = {2, 8, 13};
        int[] ys = {-64, -63, 0, 40, 65, 80, 100};
        for (int dx : xs) for (int dz : zs) for (int y : ys) {
            if (!chunk.getBlockState(p.set(x + dx, y, z + dz)).isAir()) return true;
        }
        return false;
    }
}
