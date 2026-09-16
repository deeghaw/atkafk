package com.automine.AutoMineMod.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public final class ResourceScanner {
    private static final int ORE_DY_UP = 4;
    private static final int ORE_DY_DOWN = 6;

    private ResourceScanner() {
    }

    public static boolean isOre(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    public static boolean isLog(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_log") || path.endsWith("_wood");
    }

    public static boolean isBedrock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.equals("bedrock");
    }

    public static boolean isDiggable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isBedrock(state)) return false;
        if (state.getFluidState().is(FluidTags.LAVA)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean hasSolidFeet(Level level, BlockPos pos) {
        return TerrainScanner.isSolidFloor(level, pos)
                && TerrainScanner.isWalkableThrough(level, pos)
                && TerrainScanner.isWalkableThrough(level, pos.above());
    }

    public static boolean hasRoofAbove(Level level, BlockPos pos, int upTo) {
        for (int k = 1; k <= upTo; k++) {
            if (!level.getBlockState(pos.above(k)).getCollisionShape(level, pos.above(k)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos findOre(Level level, BlockPos origin, int radius) {
        return findBlockBy(level, origin, radius, ResourceScanner::isOre);
    }

    public static BlockPos findLog(Level level, BlockPos origin, int radius) {
        return findBlockBy(level, origin, radius, ResourceScanner::isLog);
    }

    private static BlockPos findBlockBy(Level level, BlockPos origin, int radius, Predicate<BlockState> matcher) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -ORE_DY_DOWN; dy <= ORE_DY_UP; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !matcher.test(state)) continue;
                    double dist = origin.distSqr(pos);
                    double noise = ((pos.getX() * 73856093L) ^ (pos.getY() * 19349663L)
                            ^ (pos.getZ() * 83492791L)) % 977;
                    double score = dist + noise;
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    public static BlockPos findCaveOpening(Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            outer:
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos cell = origin.offset(dx, 0, dz);
                if (!hasSolidFeet(level, cell)) continue;
                if (!hasRoofAbove(level, cell, 3)) continue;
                for (int oi = -1; oi <= 1; oi++) {
                    for (int ok = -1; ok <= 1; ok++) {
                        if (oi == 0 && ok == 0) continue;
                        BlockPos pitFeet = cell.offset(oi, 0, ok);
                        if (!TerrainScanner.isWalkableThrough(level, pitFeet)) continue;
                        if (TerrainScanner.dropDepth(level, pitFeet) >= 3) {
                            double dist = origin.distSqr(cell);
                            if (dist < bestScore) {
                                bestScore = dist;
                                best = cell;
                            }
                            continue outer;
                        }
                    }
                }
            }
        }
        return best;
    }

    public static BlockPos findLandBeyondWater(Level level, BlockPos origin, int minWater, int maxRange) {
        BlockPos best = null;
        int bestWater = 0;
        for (int a = 0; a < 24; a++) {
            double ang = a * (Math.PI * 2.0 / 24.0);
            int dx = (int) Math.round(Math.cos(ang));
            int dz = (int) Math.round(Math.sin(ang));
            if (dx == 0 && dz == 0) continue;
            boolean sawWater = false;
            int waterSeen = 0;
            for (int step = 1; step <= maxRange; step++) {
                BlockPos cell = origin.offset(dx * step, 0, dz * step);
                if (TerrainScanner.isWaterAt(level, cell) || TerrainScanner.isWaterAt(level, cell.above())) {
                    sawWater = true;
                    waterSeen++;
                    continue;
                }
                if (hasSolidFeet(level, cell)) {
                    if (sawWater && waterSeen >= minWater && waterSeen > bestWater) {
                        bestWater = waterSeen;
                        best = cell;
                    }
                    break;
                }
                break;
            }
        }
        return best;
    }

    public static BlockPos findAirForBreath(Level level, BlockPos origin, int radius) {
        for (int y = origin.getY(); y < level.getHeight(); y++) {
            BlockPos p = new BlockPos(origin.getX(), y, origin.getZ());
            if (!TerrainScanner.isWalkableThrough(level, p)) break;
            if (p.getY() > origin.getY() && !TerrainScanner.isWaterAt(level, p)) {
                return p;
            }
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos cell = origin.offset(dx, 0, dz);
                if (!hasSolidFeet(level, cell)) continue;
                if (TerrainScanner.isWaterAt(level, cell)) continue;
                double dist = origin.distSqr(cell);
                if (dist < bestScore) {
                    bestScore = dist;
                    best = cell;
                }
            }
        }
        return best;
    }

    public static boolean isUnderground(Level level, BlockPos pos) {
        if (pos.getY() < 40) return true;
        return hasRoofAbove(level, pos, 16);
    }
}