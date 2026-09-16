package com.automine.AutoMineMod.client;

import com.automine.AutoMineMod.navigation.ResourceScanner;
import com.automine.AutoMineMod.navigation.TerrainScanner;
import com.automine.AutoMineMod.navigation.ToolSelector;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

public class ExplorationController {
    public static final double REACH = 4.8;

    private static final int HUNT_HUNGER = 12;
    private static final int FOOD_RADIUS = 22;
    private static final int ORE_RADIUS = 12;
    private static final int LOG_RADIUS = 14;
    private static final int MAX_AIR_TICKS = 45;
    private static final int CAVE_RADIUS = 10;
    private static final int BREATH_RADIUS = 8;
    private static final int FOOTSTEP_TICKS = 40;
    private static final int SKIP_TICKS = 200;
    private static final int TORCH_COOLDOWN = 34;
    private static final int IDLE_BEGIN_STAIRS = 35;
    private static final int IDLE_RETURN = 200;
    private static final int MIN_BREADCRUMBS_TO_RETURN = 6;
    private static final int IDLE_CAVE = 80;
    private static final int DESCEND_REARM = 70;
    private static final int WATER_COOLDOWN = 500;
    private static final int STAIR_STOP_DEPTH = 6;
    private static final int STAIR_MAX_STEPS = 70;
    private static final int MAX_BREADCRUMBS = 64;
    private static final double POINT_REACHED_SQR = 1.9 * 1.9;

    private static final int WANDER_TICKS_MIN = 30;
    private static final int WANDER_TICKS_MAX = 160;

    private static final String[] FOOD_MOBS = {"cow", "pig", "sheep", "chicken", "rabbit", "mooshroom"};

    public BlockPos mineTarget;
    public BlockPos walkTarget;
    public Vec3 forcedMoveDir;
    public BlockPos swimTo;
    public boolean swimEngaged;
    public LivingEntity foodTarget;
    public boolean allowDrop;
    public BlockPos torchPlace;

    private int torchCooldown;
    private int airTicks;
    private int footstepTicks;
    private int idleTicks;
    private BlockPos skipPos;
    private int skipUntil;
    private Vec3 wanderDir = new Vec3(1, 0, 0);
    private final ArrayDeque<BlockPos> breadcrumbs = new ArrayDeque<>();
    private int descendCooldown;
    private boolean stairActive;
    private Vec3 stairDir;
    private int stairSteps;
    private double stairStartY;
    private int waterCooldown;
    private int tick;
    private int lootId = -1;
    private int wanderTicks;

    public void plan(LocalPlayer player) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        boolean underwater = player.isInWater();
        tick++;

        if (torchCooldown > 0) torchCooldown--;
        if (skipUntil > 0 && tick > skipUntil) {
            skipPos = null;
            skipUntil = 0;
        }
        if (waterCooldown > 0) waterCooldown--;

        forcedMoveDir = null;
        torchPlace = null;
        walkTarget = null;
        allowDrop = false;
        swimTo = null;
        swimEngaged = false;

        if (lootId != -1) {
            Entity loot = level.getEntity(lootId);
            if (loot == null || !loot.isAlive()) {
                lootId = -1;
            } else {
                walkTarget = loot.blockPosition();
                return;
            }
        }

        boolean hungry = player.getFoodData().getFoodLevel() <= HUNT_HUNGER;
        boolean hasEdible = hasEdible(player);
        if (hungry && !hasEdible) {
            LivingEntity mob = nearestFoodMob(player);
            if (mob != null) {
                foodTarget = mob;
                idleTicks = 0;
                return;
            }
            foodTarget = null;
            Entity loot = nearestFoodLoot(player);
            if (loot != null) {
                lootId = loot.getId();
                return;
            }
            lootId = -1;
        } else {
            foodTarget = null;
        }

        if (underwater) {
            airTicks++;
            planUnderwater(player);
            return;
        }
        boolean surfaced = airTicks > 0;
        airTicks = 0;

        boolean underground = ResourceScanner.isUnderground(level, feet);

        if (underground && torchCooldown <= 0 && inventoryCount(player, Items.TORCH) > 0) {
            torchPlace = pickTorchBlock(player, feet);
            if (torchPlace != null) torchCooldown = TORCH_COOLDOWN;
        }

        if (mineTarget != null) {
            BlockState s = level.getBlockState(mineTarget);
            if (s.isAir() || !ResourceScanner.isDiggable(level, mineTarget)) mineTarget = null;
        }
        if (mineTarget == null) {
            mineTarget = findResourceTarget(player, feet);
        }
        if (mineTarget != null) {
            walkTarget = mineTarget;
            idleTicks = 0;
            if (surfaced) waterCooldown = WATER_COOLDOWN;
            return;
        }

        idleTicks++;

        if (underground) {
            if (!stairActive && breadcrumbs.size() >= MIN_BREADCRUMBS_TO_RETURN && idleTicks > IDLE_RETURN) {
                BlockPos back = breadcrumbs.peekLast();
                walkTarget = back;
                allowDrop = true;
                if (player.distanceToSqr(Vec3.atCenterOf(back)) < POINT_REACHED_SQR) {
                    breadcrumbs.pollLast();
                }
                if (breadcrumbs.isEmpty()) idleTicks = 0;
                return;
            }
            pushBreadcrumbs(player, feet, underground);
            if (stairActive) {
                if (stairDone(player)) {
                    stairActive = false;
                    stairSteps = 0;
                    idleTicks = 0;
                    descendCooldown = DESCEND_REARM;
                    return;
                }
                doStair(player, feet);
                return;
            }
            if (idleTicks > IDLE_BEGIN_STAIRS && descendCooldown <= 0) {
                beginStair(feet);
                return;
            }
            refreshWanderDir();
            forcedMoveDir = wanderDir;
            return;
        }

        if (idleTicks > IDLE_CAVE && descendCooldown <= 0) {
            BlockPos opening = ResourceScanner.findCaveOpening(level, feet, CAVE_RADIUS);
            if (opening != null) {
                walkTarget = opening;
                allowDrop = true;
                descendCooldown = DESCEND_REARM;
                return;
            }
            descendCooldown = 60;
        }

        if (waterCooldown <= 0 && idleTicks > IDLE_CAVE && !surfaced) {
            BlockPos water = findWaterEnter(level, feet);
            if (water != null) {
                swimTo = water;
                return;
            }
            waterCooldown = 240;
        }

        refreshWanderDir();
        forcedMoveDir = wanderDir;
        if (surfaced) waterCooldown = WATER_COOLDOWN;
    }

    private boolean stairDone(LocalPlayer player) {
        return stairActive && (player.getY() <= stairStartY - STAIR_STOP_DEPTH || stairSteps >= STAIR_MAX_STEPS);
    }

    private void beginStair(BlockPos feet) {
        stairActive = true;
        stairDir = wanderDir != null ? wanderDir : new Vec3(1, 0, 0);
        stairSteps = 0;
        stairStartY = feet.getY();
        descendCooldown = DESCEND_REARM;
        idleTicks = 0;
    }

    private void doStair(LocalPlayer player, BlockPos feet) {
        int dx = (int) Math.round(stairDir.x);
        int dz = (int) Math.round(stairDir.z);
        if (dx == 0 && dz == 0) {
            stairDir = new Vec3(1, 0, 0);
            dx = 1;
            dz = 0;
        }
        BlockPos front = feet.offset(dx, 0, dz);
        BlockPos frontBelow = front.below();
        Vec3 dir = new Vec3(dx, 0, dz);

        if (cellOpen(player.level(), frontBelow) && cellOpen(player.level(), front)) {
            walkTarget = front;
            forcedMoveDir = dir;
            stairSteps++;
            return;
        }
        BlockState frontState = player.level().getBlockState(front);
        if (!frontState.getCollisionShape(player.level(), front).isEmpty()
                && ResourceScanner.isDiggable(player.level(), front)) {
            mineTarget = front;
            walkTarget = front;
            forcedMoveDir = dir;
            stairSteps++;
            return;
        }
        if (ResourceScanner.isDiggable(player.level(), frontBelow)) {
            mineTarget = frontBelow;
            walkTarget = front;
            forcedMoveDir = dir;
            stairSteps++;
            return;
        }
        stairDir = TerrainScanner.rotateY(stairDir, 90.0).normalize();
        stairSteps = 0;
    }

    private boolean cellOpen(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private BlockPos pickTorchBlock(LocalPlayer player, BlockPos feet) {
        Vec3 dir = wanderDir != null ? wanderDir : new Vec3(1, 0, 0);
        int dx = (int) Math.round(dir.x);
        int dz = (int) Math.round(dir.z);
        if (dx == 0 && dz == 0) dx = 1;
        BlockPos look = feet.offset(dx, 0, dz);
        for (int k = 0; k < 3; k++) {
            BlockPos cand = feet.offset(dx * (k + 1), 0, dz * (k + 1));
            if (!player.level().getBlockState(cand).getCollisionShape(player.level(), cand).isEmpty()) {
                return cand;
            }
        }
        BlockPos floor = feet.offset(dx, -1, dz);
        if (!player.level().getBlockState(floor).getCollisionShape(player.level(), floor).isEmpty()) {
            return floor;
        }
        return look;
    }

    private BlockPos findResourceTarget(LocalPlayer player, BlockPos feet) {
        Level level = player.level();
        BlockPos ore = isBlocked(skipPos) ? null : ResourceScanner.findOre(level, feet, ORE_RADIUS);
        if (ore != null && !isBlocked(ore)) {
            int dy = ore.getY() - feet.getY();
            if (dy >= -8 && dy <= 4) {
                BlockState s = level.getBlockState(ore);
                if (ToolSelector.selectFor(player.getInventory(), s) >= 0) return ore;
            }
        }
        BlockPos log = ResourceScanner.findLog(level, feet, LOG_RADIUS);
        if (log != null && !isBlocked(log)) {
            int dy = log.getY() - feet.getY();
            if (dy >= -2 && dy <= 4) {
                BlockState s = level.getBlockState(log);
                if (ToolSelector.selectFor(player.getInventory(), s) >= 0) return log;
            }
        }
        return null;
    }

    private boolean isBlocked(BlockPos pos) {
        return pos != null && skipPos != null && pos.equals(skipPos);
    }

    private void planUnderwater(LocalPlayer player) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        if (airTicks > MAX_AIR_TICKS) {
            swimTo = ResourceScanner.findAirForBreath(level, feet, BREATH_RADIUS);
            if (swimTo == null) swimTo = feet.above();
            swimEngaged = true;
            return;
        }
        if (mineTarget != null) {
            BlockState s = level.getBlockState(mineTarget);
            if (s.isAir() || !ResourceScanner.isOre(s)) mineTarget = null;
        }
        if (mineTarget == null) {
            BlockPos ore = ResourceScanner.findOre(level, feet, BREATH_RADIUS);
            if (ore != null && !isBlocked(ore)
                    && ToolSelector.selectFor(player.getInventory(), level.getBlockState(ore)) >= 0) {
                mineTarget = ore;
            }
        }
        if (mineTarget != null) {
            swimTo = mineTarget;
            swimEngaged = true;
            airTicks -= 2;
            if (airTicks < 0) airTicks = 0;
            return;
        }
        refreshWanderDir();
        int sx = feet.getX() + (int) Math.round(wanderDir.x * 6);
        int sz = feet.getZ() + (int) Math.round(wanderDir.z * 6);
        swimTo = new BlockPos(sx, feet.getY(), sz);
        swimEngaged = true;
    }

    public Vec3 desiredDir(LocalPlayer player) {
        if (forcedMoveDir != null) {
            Vec3 flat = new Vec3(forcedMoveDir.x, 0, forcedMoveDir.z);
            return flat.lengthSqr() < 1.0E-4 ? null : flat.normalize();
        }
        if (swimTo != null) {
            Vec3 to = Vec3.atCenterOf(swimTo).subtract(player.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 0.04) return null;
            return flat.normalize();
        }
        if (walkTarget != null) {
            Vec3 to = Vec3.atCenterOf(walkTarget).subtract(player.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1.0) return null;
            return flat.normalize();
        }
        return null;
    }

    public Vec3 swimLookTarget(LocalPlayer player) {
        if (swimTo == null) return null;
        return Vec3.atCenterOf(swimTo);
    }

    private LivingEntity nearestFoodMob(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(FOOD_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == player || !e.isAlive() || e instanceof Enemy) continue;
            if (!isFoodMob(e)) continue;
            double d = player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean isFoodMob(LivingEntity e) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        for (String name : FOOD_MOBS) {
            if (path.equals(name) || path.contains(name)) return true;
        }
        return false;
    }

    private Entity nearestFoodLoot(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(FOOD_RADIUS);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (!e.isAlive()) continue;
            ItemStack stack = e.getItem();
            if (stack.isEmpty() || stack.get(DataComponents.FOOD) == null) continue;
            double d = player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private boolean hasEdible(LocalPlayer player) {
        int size = Math.min(player.getInventory().getContainerSize(), 9);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) return true;
        }
        return false;
    }

    private int inventoryCount(LocalPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item)) total += s.getCount();
        }
        return total;
    }

    private BlockPos findWaterEnter(Level level, BlockPos feet) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos cell = feet.offset(dx, 0, dz);
                if (TerrainScanner.isWaterAt(level, cell) || TerrainScanner.isWaterAt(level, cell.offset(0, -1, 0))) {
                    double dist = feet.distSqr(cell);
                    if (dist < bestScore) {
                        bestScore = dist;
                        best = cell;
                    }
                }
            }
        }
        return best;
    }

    private Vec3 nextWanderDir() {
        double angle = (Math.random() - 0.5) * 70.0;
        wanderDir = TerrainScanner.rotateY(wanderDir, angle).normalize();
        if (Math.abs(wanderDir.x) < 0.2 && Math.abs(wanderDir.z) < 0.2) {
            wanderDir = new Vec3(1, 0, 0);
        }
        return wanderDir;
    }

    private void refreshWanderDir() {
        if (wanderTicks <= 0) {
            wanderDir = nextWanderDir();
            wanderTicks = WANDER_TICKS_MIN + (int) (Math.random() * (WANDER_TICKS_MAX - WANDER_TICKS_MIN));
        } else {
            wanderTicks--;
        }
    }

    private void pushBreadcrumbs(LocalPlayer player, BlockPos feet, boolean underground) {
        if (!underground || !player.onGround()) return;
        if (footstepTicks > 0) {
            footstepTicks--;
            return;
        }
        footstepTicks = FOOTSTEP_TICKS;
        if (!breadcrumbs.isEmpty() && breadcrumbs.peekLast().equals(feet)) return;
        breadcrumbs.addLast(feet.immutable());
        if (breadcrumbs.size() > MAX_BREADCRUMBS) breadcrumbs.pollFirst();
    }

    public void markBlocked(BlockPos pos) {
        if (pos != null) {
            skipPos = pos.immutable();
            skipUntil = tick + SKIP_TICKS;
        }
        if (mineTarget != null && mineTarget.equals(pos)) mineTarget = null;
    }

    public void onStuck() {
        wanderDir = TerrainScanner.rotateY(wanderDir, 20.0 + Math.random() * 90.0).normalize();
        wanderTicks = 0;
        mineTarget = null;
        walkTarget = null;
        swimTo = null;
        forcedMoveDir = null;
        stairDir = wanderDir;
    }

    public void reset() {
        mineTarget = null;
        walkTarget = null;
        forcedMoveDir = null;
        swimTo = null;
        swimEngaged = false;
        foodTarget = null;
        allowDrop = false;
        torchPlace = null;
        torchCooldown = 0;
        airTicks = 0;
        footstepTicks = 0;
        idleTicks = 0;
        skipPos = null;
        skipUntil = 0;
        descendCooldown = 0;
        stairActive = false;
        stairSteps = 0;
        waterCooldown = 0;
        lootId = -1;
        wanderTicks = 0;
        breadcrumbs.clear();
    }

    public void clearCommand() {
        forcedMoveDir = null;
        swimTo = null;
        swimEngaged = false;
        torchPlace = null;
        walkTarget = null;
    }
}