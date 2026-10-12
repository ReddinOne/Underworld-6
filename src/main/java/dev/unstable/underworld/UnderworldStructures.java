package dev.unstable.underworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Eight original ruin families with independent loot and very rare portals.
 * They are grounded and detailed;
 * every chunk only writes blocks in its OWN 16x16 area. Geometry repeats
 * deterministically across boundaries without recursive chunk loading.
 */
final class UnderworldStructures {
    private static final ResourceKey<LootTable> RELICS = ResourceKey.create(
            Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("underworld", "chests/forgotten_relics"));
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BRICK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState CRACK = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState CHISEL = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState STONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT = Blocks.POLISHED_BASALT.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SOUL_LANTERN.defaultBlockState();
    private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState CHEST = Blocks.CHEST.defaultBlockState();
    private static final BlockState SCULK = Blocks.SCULK.defaultBlockState();
    // Minecraft 26.3: use iron bars for suspended ruin decorations.
    private static final BlockState CHAIN = BARS;
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState PORTAL = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);

    private UnderworldStructures() { }

    static void generate(LevelChunk chunk, long seed) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (UnderworldPlacement.Site site : UnderworldPlacement.inChunk(minX, minZ, seed)) {
            int cx = site.x(), cz = site.z();
            int kind = site.kind();
            if (!UnderworldMath.structureSite(cx, cz, seed, radius(kind))) continue;
            drawChunkSlice(chunk, minX, minZ, cx, cz, kind, site.hash(), seed);
        }
    }

    static int radius(int kind) {
        return switch (kind) {
            case 0 -> 25; case 1 -> 22; case 2 -> 16; case 3 -> 30;
            case 4 -> 20; case 5 -> 25; case 6 -> 34; default -> 27;
        };
    }

    // Approximately 0.22% of gateway/temple structures; substantially less
    // than one in a hundred total ruins. Changing this won't alter terrain.
    static boolean hasRarePortal(int kind, long seed) {
        return (kind == 0 || kind == 4)
                && Math.floorMod(UnderworldMath.mix(seed ^ 0x74511B028L), 110) == 0;
    }

    private static void drawChunkSlice(LevelChunk chunk, int minX, int minZ,
            int cx, int cz, int kind, long r, long seed) {
        int baseY = UnderworldMath.surfaceY(cx, cz, seed);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int radius = radius(kind);
        boolean rarePortal = hasRarePortal(kind, r);
        for (int x = Math.max(minX, cx - radius); x <= Math.min(minX+15, cx+radius); x++) {
            for (int z = Math.max(minZ, cz-radius); z <= Math.min(minZ+15, cz+radius); z++) {
                int dx = x-cx, dz = z-cz;
                if (!insideFloor(kind, dx, dz)) continue;
                if (!UnderworldMath.hasLand(x, z, seed)) continue;
                int originalSurface = UnderworldMath.surfaceY(x, z, seed);
                // A blackstone foundation bonds the ruin to the REAL surface.
                // Where necessary we shave an elevated natural hill to grade.
                for (int y = Math.min(originalSurface+1, baseY); y < baseY; y++) {
                    set(chunk, pos, x, y, z, STONE);
                }
                // Clear nature from the courtyard, never build on top of hills.
                int high = Math.max(baseY+44, originalSurface+2);
                for (int y = baseY; y <= high; y++) {
                    int dy = y-baseY;
                    BlockState state = piece(kind, dx, dz, dy, r);
                    // Actual active Nether-shaped portal; a custom dimension
                    // hand-off redirects this portal to the Overworld.
                    if (rarePortal) {
                        BlockState special = portalPiece(dx, dz, dy);
                        if (special != null) state = special;
                    }
                    // preserve terra below the ruin's foundation, but ensure
                    // the courtyard and windows contain air rather than dirt.
                    if (state == null) state = AIR;
                    if (y == baseY && state == AIR) state = floor(dx, dz, r);
                    set(chunk, pos, x, y, z, state);
                }
                // A chest is placed above the ruin floor *after* all surrounding
                // blocks have been drawn, so its inventory isn't overwritten.
                placeLootChest(chunk, x, z, dx, dz, kind, baseY, r);
            }
        }
    }

    private static void placeLootChest(LevelChunk chunk, int x, int z,
                                       int dx, int dz, int kind, int baseY, long r) {
        // Two mutually distant possible chest sites per ruin, each rolled once.
        int off = switch(kind) { case 2 -> 7; case 4 -> 11; case 5 -> 12;
                                 case 6 -> 17; case 7 -> 17; default -> 12; };
        boolean first = dx == -off && dz == 3;
        boolean second = dx == off && dz == -3;
        if (!first && !second) return;
        long h = UnderworldMath.mix(r ^ (first ? 0x0F1156L : 0xDD5918L));
        if (Math.floorMod(h, 100) >= (first ? 68 : 24)) return;
        // A non-double chest, safely accessible from above, with loot rolled
        // only on first opening. Never query adjacent chunks.
        BlockPos chestPos = new BlockPos(x, baseY + 1, z);
        chunk.setBlockState(chestPos, CHEST, 0);
        chunk.setBlockState(chestPos.above(), AIR, 0);
        BlockEntity be = chunk.getBlockEntity(chestPos, LevelChunk.EntityCreationType.IMMEDIATE);
        if (be instanceof RandomizableContainer container) {
            container.setLootTable(RELICS, h);
            be.setChanged();
        }
    }

    /** Three-wide functional portal inside an obsidian rectangle. */
    private static BlockState portalPiece(int x, int z, int y) {
        if (z != 0 || Math.abs(x) > 2 || y < 1 || y > 6) return null;
        if (Math.abs(x) == 2 || y == 1 || y == 6) return OBSIDIAN;
        return PORTAL;
    }

    private static boolean insideFloor(int kind, int x, int z) {
        int ax=Math.abs(x), az=Math.abs(z);
        return switch(kind) {
            case 0 -> ax<=25 && az<=9;
            case 1 -> x*x+z*z<=22*22;
            case 2 -> ax<=16 && az<=16;
            case 3 -> ax<=30 && az<=30;
            case 4 -> ax<=20 && az<=20;
            case 5 -> ax<=25 && az<=25;
            case 6 -> ax<=34 && az<=14;
            default -> ax<=27 && az<=27;
        };
    }

    /** Cathedral-height gateway, tiered double towers, gothic arch and collapsed wings. */
    private static BlockState gate(int x, int z, int y, long r) {
        int ax=Math.abs(x), az=Math.abs(z);
        if (y==0) return (az<=3 && ax<11) ? BASALT : floor(x,z,r);
        if (ax>=10 && ax<=15 && az<=5 && y<=33) {
            if (ax<=12 && az<=2 && y>=4 && y<=20) return AIR;
            if (y>24 && missing(x,z,y,r,23)) return AIR;
            if (y%8==0) return CHISEL;
            if (ax==15 && y%5==1) return BASALT;
            return brick(x,z,y,r);
        }
        // Arch with open central passage; lintel survives in irregular segments.
        if (az<=3 && ax<=12 && y>=19 && y<=28) {
            int arch = 19 + (int)Math.round(9.0*Math.sqrt(Math.max(0,1-(x/12.0)*(x/12.0))));
            if (y<arch) return AIR;
            if (x>2 && x<8 && y>26 && missing(x,z,y,r,8)) return AIR;
            return y==arch?CHISEL:brick(x,z,y,r);
        }
        if (ax>=17 && ax<=24 && az<=2 && y<=8+(ax%7)) {
            if (missing(x,z,y,r,13)) return AIR;
            return brick(x,z,y,r);
        }
        if (az==6 && ax>=10 && ax<=15 && y==17) return GILDED;
        if (ax==0 && az==5 && y==1) return LANTERN;
        if (y==1 && ax>=18 && ax<=23 && az==6 && !missing(x,z,y,r,4)) return CRACK;
        return null;
    }

    /** Large circular observatory, rib arches, cracked concentric floors and altar. */
    private static BlockState shrine(int x, int z, int y, long r) {
        double dist=Math.hypot(x,z);
        if (y==0) return dist>17 && dist<21 ? CHISEL : floor(x,z,r);
        if (dist<4 && y==1) return GILDED;
        if (dist<=2 && y>=2 && y<=5) return y==5 ? CRYING:CHISEL;
        for (int i=0;i<8;i++) {
            double theta=i*Math.PI/4;
            int px=(int)Math.round(17*Math.cos(theta));
            int pz=(int)Math.round(17*Math.sin(theta));
            if (Math.abs(x-px)<=2 && Math.abs(z-pz)<=2) {
                int height=15+(int)((r >>> (i*6)) & 7);
                if (y<=height) {
                    if (y>height-3 && i%3==0) return AIR;
                    return (y==height || y%6==0) ? CHISEL:brick(x,z,y,r);
                }
            }
        }
        // Four fragmented transverse beams around the central monument.
        if (y>=19 && y<=22 && ((Math.abs(x)<=1 && dist>=8 && dist<=18)
                || (Math.abs(z)<=1 && dist>=8 && dist<=18))) {
            if (missing(x,z,y,r,7)) return AIR;
            return brick(x,z,y,r);
        }
        if (y==1 && dist>=9 && dist<=10 && !missing(x,z,y,r,6)) return BASALT;
        if (y==1 && Math.abs(x)==6 && Math.abs(z)==6) return LANTERN;
        return null;
    }

    /** Several levels, battlements, shattered parapets and an exposed stair core. */
    private static BlockState tower(int x, int z, int y, long r) {
        int ax=Math.abs(x),az=Math.abs(z), edge=Math.max(ax,az);
        if (y==0) return (edge>=11 && edge<=14) ? CHISEL:floor(x,z,r);
        if (edge>=10 && edge<=13 && y<=34) {
            if ((ax==10 && az<3) && y>=2 && y<=6) return AIR;
            if (((ax==11 && az<=2) || (az==11 && ax<=2)) && y>=12 && y<=16) return BARS;
            if (y>26 && missing(x,z,y,r,11)) return AIR;
            return y%8==0 ? BASALT:brick(x,z,y,r);
        }
        if (edge<=10 && (y==10 || y==20 || y==30)) {
            if (missing(x,z,y,r,17)) return AIR;
            return brick(x,z,y,r);
        }
        if (y>=35 && y<=39 && edge>=10 && edge<=14) {
            if ((x+z)%4==0 || missing(x,z,y,r,10)) return AIR;
            return CRACK;
        }
        // Stair core shaft / fragmented perpendicular flights.
        if (Math.abs(x)<=3 && Math.abs(z)<=3 && y>=1 && y<=28 && y%7==0) return BASALT;
        if (edge==8 && y==21) return GILDED;
        if (ax==0 && az==0 && y==1) return LANTERN;
        return null;
    }

    /** Half-buried citadel with courtyard, towers, decorated entry and side cloisters. */
    private static BlockState citadel(int x, int z, int y, long r) {
        int ax=Math.abs(x),az=Math.abs(z);
        if (y==0) {
            if ((ax==0 || az==0) && ax<27 && az<27) return BASALT;
            if (ax<=7 && az<=7) return GILDED;
            return floor(x,z,r);
        }
        // Four multi-story corner towers.
        if (ax>=20 && ax<=29 && az>=20 && az<=29 && y<=42) {
            if (ax<=26 && az<=26 && y>1 && y<37 && y!=19) return AIR;
            if (y>35 && missing(x,z,y,r,19)) return AIR;
            if (y%9==0) return CHISEL;
            return ax==29 || az==29 ? BASALT:brick(x,z,y,r);
        }
        // Courtyard perimeter, ruined crenellations, doorway and window openings.
        if ((ax>=24 && ax<=27 && az<=23) || (az>=24 && az<=27 && ax<=23)) {
            if (y<=24) {
                if (ax>=24 && az<=3 && y<=9) return AIR;
                if (az>=24 && ax>=9 && ax<=13 && y>=8 && y<=12) return BARS;
                if (y>17 && missing(x,z,y,r,20)) return AIR;
                if (y%8==0) return CHISEL;
                return brick(x,z,y,r);
            }
            if (y<=27 && (Math.floorMod(x+z,6)<=2) && !missing(x,z,y,r,14)) return CRACK;
        }
        // Surviving library shelves / divided inner room supports.
        if (ax>=11 && ax<=14 && az<=13 && y<=14) {
            if (az<=2 && y<=5) return AIR;
            if (y>=8 && y<=10 && az>=7 && az<=9) return BARS;
            return brick(x,z,y,r);
        }
        if (az>=12 && az<=15 && ax<=13 && y<=14) {
            if (ax<=2 && y<=5) return AIR;
            return y%6==0 ? CHISEL:brick(x,z,y,r);
        }
        if (ax<=9 && az<=9 && (y==13 || y==14) && !missing(x,z,y,r,18)) return CRACK;
        if (ax<=3 && az<=3 && y==1) return CHISEL;
        if (x==0 && z==0 && y>=2 && y<=7) return y==7 ? CRYING:BASALT;
        if (ax==5 && az==5 && y==1) return LANTERN;
        if (y==1 && (ax==18 || az==18) && ax<=20 && az<=20 && !missing(x,z,y,r,4)) return GILDED;
        return null;
    }


    /** Square tomb containing a sealed altar, arcades, broken roof and lantern chains. */
    private static BlockState mausoleum(int x, int z, int y, long r) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (y == 0) return floor(x,z,r);
        if (ax >= 13 && ax <= 18 && az >= 13 && az <= 18 && y <= 24) {
            if (y > 16 && missing(x,z,y,r,9)) return AIR;
            return y%7 == 0 ? CHISEL : brick(x,z,y,r);
        }
        if ((ax == 14 && az <= 14 || az == 14 && ax <= 14) && y <= 18) {
            if (ax <= 3 && az == 14 && y <= 6) return AIR;
            if (y >= 9 && y <= 12 && (ax == 6 || az == 6)) return BARS;
            return brick(x,z,y,r);
        }
        if (ax <= 12 && az <= 12 && (y == 17 || y == 18) && !missing(x,z,y,r,6)) return CRACK;
        if (ax <= 4 && az <= 6 && y >= 1 && y <= 3) return y==3 ? CHISEL : BASALT;
        if (ax == 9 && az == 9 && y >= 1 && y <= 6) return y==6 ? LANTERN : CHAIN;
        if (y==1 && ax>=16 && az<=4 && !missing(x,z,y,r,4)) return SCULK;
        return null;
    }

    /** Archive of staggered shelves and broken high corridors, interrupted by sculk. */
    private static BlockState shatteredArchive(int x, int z, int y, long r) {
        int ax = Math.abs(x), az = Math.abs(z);
        if (y==0) return (ax%6==0 || az%6==0) ? BASALT : floor(x,z,r);
        if ((ax>=18 && ax<=21 && az<=22) || (az>=18 && az<=21 && ax<=22)) {
            if (y<=22 && !missing(x,z,y,r,13)) {
                if (ax<=3 && az>=18 && y<=7) return AIR;
                return y%7==0 ? CHISEL : brick(x,z,y,r);
            }
        }
        if (ax%7==0 && az>=5 && az<=17 && y<=11 && !missing(x,z,y,r,8)) {
            return y==11 ? CHISEL : BRICK;
        }
        if (az%7==0 && ax>=5 && ax<=17 && y<=11 && !missing(x,z,y,r,8)) return BRICK;
        if ((y==12 || y==21) && ax<=19 && az<=19 && !missing(x,z,y,r,4)) return CRACK;
        if (ax<=5 && az<=5 && y==1) return SCULK;
        if (ax==12 && az==12 && y>=2 && y<=8) return y==8 ? LANTERN : CHAIN;
        return null;
    }

    /** Fractured raised causeway between unequal bastions, with a partial underpass. */
    private static BlockState fracturedBridge(int x, int z, int y, long r) {
        int ax=Math.abs(x), az=Math.abs(z);
        if (y==0) return ax<=5 || ax>=20 ? floor(x,z,r) : BASALT;
        if (ax>=22 && ax<=32 && az<=13 && y<=26) {
            if (ax<30 && az<10 && y>=4 && y<=20) return AIR;
            return y%8==0 ? CHISEL : brick(x,z,y,r);
        }
        if (ax<=25 && az<=5 && y>=11 && y<=14 && !missing(x,z,y,r,12)) return BRICK;
        if (ax<=25 && (az==6 || az==7) && y>=14 && y<=17 && !missing(x,z,y,r,9)) return BARS;
        if (ax%10==0 && az<=4 && y>=2 && y<=12) return BASALT;
        if (ax>=9 && ax<=17 && az<=3 && y>=2 && y<=10 && missing(x,z,y,r,4)) return AIR;
        if (ax==30 && az==11 && y==27) return LANTERN;
        return null;
    }

    /** Half-vanished plaza, separate stalls, collapsed canopies and a central well. */
    private static BlockState forgottenMarket(int x, int z, int y, long r) {
        int ax=Math.abs(x), az=Math.abs(z);
        if (y==0) return ax<=4 || az<=4 ? BASALT : floor(x,z,r);
        if (ax>=19 && ax<=24 && az>=19 && az<=24 && y<=17) {
            if (y>11 && missing(x,z,y,r,6)) return AIR;
            return brick(x,z,y,r);
        }
        if ((ax>=11 && ax<=15 && az>=5 && az<=18) || (az>=11 && az<=15 && ax>=5 && ax<=18)) {
            if (y<=9 && ((ax==11 || ax==15) || (az==11 || az==15))) return brick(x,z,y,r);
            if (y==10 && !missing(x,z,y,r,3)) return CRACK;
        }
        if (ax<=4 && az<=4 && y<=5) {
            if (ax==4 || az==4) return CHISEL;
            if (y==1) return SCULK;
            return AIR;
        }
        if (ax==17 && az==3 && y>=1 && y<=5) return y==5 ? LANTERN : CHAIN;
        if (y==1 && ax>=6 && az>=6 && Math.floorMod(x+z,7)==0) return GILDED;
        return null;
    }

    private static BlockState piece(int kind,int x,int z,int y,long r) {
        return switch(kind) {
            case 0 -> gate(x,z,y,r);
            case 1 -> shrine(x,z,y,r);
            case 2 -> tower(x,z,y,r);
            case 3 -> citadel(x,z,y,r);
            case 4 -> mausoleum(x,z,y,r);
            case 5 -> shatteredArchive(x,z,y,r);
            case 6 -> fracturedBridge(x,z,y,r);
            default -> forgottenMarket(x,z,y,r);
        };
    }
    private static BlockState floor(int x,int z,long r) {
        if ((x*x+z*z)%53==0) return CHISEL;
        if (Math.floorMod(7*x+13*z,37)==0) return BASALT;
        if (missing(x,z,0,r,11)) return STONE;
        return brick(x,z,0,r);
    }
    private static BlockState brick(int x,int z,int y,long r) {
        return missing(x,z,y,r,7) ? CRACK:BRICK;
    }
    private static boolean missing(int x,int z,int y,long r,int rate) {
        long h=UnderworldMath.mix(r ^ (long)x*0x9E3779B97F4A7C15L
                ^ (long)z*0xC2B2AE3D27D4EB4FL ^ (long)y*0x165667B19E3779F9L);
        return Math.floorMod(h,rate)==0;
    }
    private static void set(LevelChunk chunk,BlockPos.MutableBlockPos p,int x,int y,int z,BlockState state) {
        p.set(x,y,z);
        if (!chunk.getBlockState(p).equals(state)) chunk.setBlockState(p,state,0);
    }
}
