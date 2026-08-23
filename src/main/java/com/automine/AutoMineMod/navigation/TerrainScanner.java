package com.automine.AutoMineMod.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class TerrainScanner {
    public static final int MAX_SAFE_DROP = 3;
    public static final int MAX_SCAN_DEPTH = 8;

    private static final double[] DEVIATIONS = {
            0.0, 22.5, -22.5, 45.0, -45.0, 67.5, -67.5,
            90.0, -90.0, 112.5, -112.5, 157.5, 180.0
    };

    private static final double NEAR_PROBE = 0.75;
    private static final double FAR_PROBE = 1.4;
    public static final int MAX_CONTROLLED_DESCENT = 6;

    public static final double HAZARD_MARGIN = 3.5;
    public static final double FLEE_HAZARD_PENALTY = 24.0;
    private static final double MARGIN_START = 1.0;
    private static final double MARGIN_SAMPLE_STEP = 0.7;

    private TerrainScanner() {
    }

    public static boolean isDamageBlock(BlockState state) {
        return state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    public static boolean isLavaAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
    }

    public static boolean isWaterAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.WATER);
    }

    public static boolean isWalkableThrough(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return !isDamageBlock(state);
    }

    public static boolean isSolidFloor(Level level, BlockPos feetPos) {
        BlockPos floor = feetPos.below();
        BlockState state = level.getBlockState(floor);
        if (state.getCollisionShape(level, floor).isEmpty()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return !isDamageBlock(state);
    }

    public static int dropDepth(Level level, BlockPos feetPos) {
        int depth = 0;
        BlockPos p = feetPos.below();
        while (depth <= MAX_SCAN_DEPTH) {
            BlockState state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty()) return depth;
            depth++;
            p = p.below();
        }
        return Integer.MAX_VALUE;
    }

    public static boolean isSafeStand(Level level, BlockPos feetPos) {
        if (!isWalkableThrough(level, feetPos)) return false;
        if (!isWalkableThrough(level, feetPos.above())) return false;
        if (!isSolidFloor(level, feetPos)) return false;
        return dropDepth(level, feetPos) <= MAX_SAFE_DROP;
    }

    private static BlockPos blockOf(Vec3 pos) {
        return new BlockPos(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );
    }

    public static StepResult evaluateStep(Level level, Vec3 feetPos, Vec3 dir) {
        return evaluateStep(level, feetPos, dir, MAX_SAFE_DROP);
    }

    public static StepResult evaluateStep(Level level, Vec3 feetPos, Vec3 dir, int maxDrop) {
        Vec3 near = feetPos.add(dir.x * NEAR_PROBE, 0, dir.z * NEAR_PROBE);
        Vec3 far = feetPos.add(dir.x * FAR_PROBE, 0, dir.z * FAR_PROBE);

        BlockPos nearFeet = blockOf(near);
        StepResult nearResult = evaluateProbe(level, nearFeet, maxDrop);
        if (!nearResult.walkable()) return StepResult.BLOCKED;

        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(0.42);
        StepResult nearSide = evaluateProbe(level, blockOf(near.add(side)), maxDrop);
        StepResult farSide = evaluateProbe(level, blockOf(far.add(side)), maxDrop);
        if (!nearSide.walkable() || !farSide.walkable()) return StepResult.BLOCKED;

        BlockPos farFeet = blockOf(far);
        if (isLavaAt(level, farFeet) || isLavaAt(level, farFeet.above())) return StepResult.BLOCKED;
        if (dropDepth(level, farFeet) > Math.max(maxDrop, MAX_SAFE_DROP)) return StepResult.BLOCKED;
        if (isDamageBlock(level.getBlockState(blockOf(far)))) return StepResult.BLOCKED;

        return nearResult.needJump() || nearSide.needJump() || farSide.needJump()
            ? StepResult.STEP_UP : StepResult.CLEAR;
    }

    private static StepResult evaluateProbe(Level level, BlockPos feet) {
        return evaluateProbe(level, feet, MAX_SAFE_DROP);
    }

    private static StepResult evaluateProbe(Level level, BlockPos feet, int maxDrop) {
        if (isLavaAt(level, feet) || isLavaAt(level, feet.below())) return StepResult.BLOCKED;
        if (isWaterAt(level, feet) || isWaterAt(level, feet.below())) return StepResult.BLOCKED;
        if (isDamageBlock(level.getBlockState(feet))) return StepResult.BLOCKED;

        if (!isWalkableThrough(level, feet)) {
            BlockPos above = feet.above();
            if (!isDamageBlock(level.getBlockState(feet))
                    && level.getFluidState(above).isEmpty()
                    && isWalkableThrough(level, above)
                    && isSafeStand(level, above)) {
                return StepResult.STEP_UP;
            }
            return StepResult.BLOCKED;
        }

        if (!isWalkableThrough(level, feet.above())) return StepResult.BLOCKED;

        int depth = dropDepth(level, feet);
        if (depth <= MAX_SAFE_DROP) return StepResult.CLEAR;

        if (depth > maxDrop) return StepResult.BLOCKED;
        if (!isDescentLandingSafe(level, feet)) return StepResult.BLOCKED;

        return StepResult.CLEAR;
    }

    public static boolean isDescentLandingSafe(Level level, BlockPos feetPos) {
        BlockPos p = feetPos.below();
        int depth = 0;
        while (depth < MAX_CONTROLLED_DESCENT + 2) {
            BlockState state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty()) {
                if (!state.getFluidState().isEmpty()) return false;
                if (isDamageBlock(state)) return false;
                return isWalkableThrough(level, p.above()) && isWalkableThrough(level, p.above(2));
            }
            depth++;
            p = p.below();
        }
        return false;
    }

    public static boolean hasHazardAhead(Level level, Vec3 feetPos, Vec3 dir, double margin) {
        if (margin <= 0.0) return false;
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) return false;
        flat = flat.normalize();
        int baseY = (int) Math.floor(feetPos.y);
        for (double d = MARGIN_START; d <= margin + 1.0E-4; d += MARGIN_SAMPLE_STEP) {
            double sx = Math.floor(feetPos.x + flat.x * d);
            double sz = Math.floor(feetPos.z + flat.z * d);
            if (isMarginSampleHazardous(level, new BlockPos((int) sx, baseY, (int) sz), baseY)) return true;
        }
        return false;
    }

    private static boolean isMarginSampleHazardous(Level level, BlockPos sample, int baseY) {
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = new BlockPos(sample.getX(), baseY + dy, sample.getZ());
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty()) return true;
            if (isDamageBlock(state)) return true;
        }
        for (int dy = 2; dy >= -MAX_SAFE_DROP; dy--) {
            BlockPos feet = new BlockPos(sample.getX(), baseY + dy, sample.getZ());
            if (isWalkableThrough(level, feet)
                    && isWalkableThrough(level, feet.above())
                    && isSolidFloor(level, feet)
                    && dropDepth(level, feet) <= MAX_SAFE_DROP) {
                return false;
            }
        }
        return true;
    }

    public static Vec3 chooseAvoidDirection(Level level, Vec3 feetPos, Vec3 desiredDir, double biasDegrees) {
        return chooseAvoidDirection(level, feetPos, desiredDir, biasDegrees, MAX_SAFE_DROP);
    }

    public static Vec3 chooseAvoidDirection(Level level, Vec3 feetPos, Vec3 desiredDir, double biasDegrees, int maxDrop) {
        return chooseAvoidDirection(level, feetPos, desiredDir, biasDegrees, maxDrop, 0.0);
    }

    public static Vec3 chooseAvoidDirection(Level level, Vec3 feetPos, Vec3 desiredDir, double biasDegrees, int maxDrop, double hazardMargin) {
        Vec3 biased = biasDegrees != 0.0 ? rotateY(desiredDir, biasDegrees).normalize() : desiredDir;
        Vec3 best = null;
        int bestScore = Integer.MIN_VALUE;
        for (double dev : DEVIATIONS) {
            Vec3 candidate = dev == 0.0 ? biased : rotateY(biased, dev);
            StepResult step = evaluateStep(level, feetPos, candidate, maxDrop);
            if (!step.walkable()) continue;
            int score = dev == 0.0 ? 20 : 0;
            if (evaluateStep(level, feetPos.add(candidate.scale(1.5)), candidate, maxDrop).walkable()) score += 8;
            if (evaluateStep(level, feetPos.add(candidate.scale(3.0)), candidate, maxDrop).walkable()) score += 8;
            if (step.needJump()) score -= 2;
            if (hazardMargin > 0.0 && hasHazardAhead(level, feetPos, candidate, hazardMargin)) score -= FLEE_HAZARD_PENALTY;
            if (score > bestScore) {
                bestScore = score;
                best = candidate.normalize();
            }
        }
        return best;
    }

    public static Vec3 chooseFleeDirection(Level level, Vec3 feetPos, Vec3 threatPos, int maxDrop, double hazardMargin) {
        Vec3 away = new Vec3(feetPos.x - threatPos.x, 0.0, feetPos.z - threatPos.z);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1.0, 0.0, 0.0);
        away = away.normalize();
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double dev : DEVIATIONS) {
            Vec3 candidate = dev == 0.0 ? away : rotateY(away, dev);
            StepResult step = evaluateStep(level, feetPos, candidate, maxDrop);
            if (!step.walkable()) continue;
            double score = candidate.dot(away) * 12.0;
            if (dev == 0.0) score += 3.0;
            if (evaluateStep(level, feetPos.add(candidate.scale(1.5)), candidate, maxDrop).walkable()) score += 6.0;
            if (evaluateStep(level, feetPos.add(candidate.scale(3.0)), candidate, maxDrop).walkable()) score += 6.0;
            if (step.needJump()) score -= 2.0;
            if (hazardMargin > 0.0 && hasHazardAhead(level, feetPos, candidate, hazardMargin)) score -= FLEE_HAZARD_PENALTY;
            if (score > bestScore) {
                bestScore = score;
                best = candidate.normalize();
            }
        }
        return best;
    }

    public static boolean hasVisibleRoute(Level level, Vec3 from, Vec3 to) {
        Vec3 start = from.add(0.0, 1.0, 0.0);
        Vec3 end = to.add(0.0, 1.0, 0.0);
        BlockHitResult hit = level.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    public static Vec3 findEscapeDirection(Level level, Vec3 feetPos) {
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 45.0);
            Vec3 dir = new Vec3(Math.sin(angle), 0, -Math.cos(angle));
            for (double dist = 1.0; dist <= 8.0; dist += 1.0) {
                BlockPos probe = blockOf(feetPos.add(dir.scale(dist)));
                for (int yOffset = 2; yOffset >= -2; yOffset--) {
                    BlockPos candidate = probe.offset(0, yOffset, 0);
                    if (!isSafeStand(level, candidate)) continue;
                    double score = 20.0 - dist + (yOffset > 0 ? yOffset : 0);
                    if (score > bestScore) {
                        bestScore = score;
                        best = dir;
                    }
                }
            }
        }
        return best;
    }

    public static Vec3 rotateY(Vec3 v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }
}
