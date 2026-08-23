package com.automine.AutoMineMod.client;

import com.automine.AutoMineMod.AutoMineMod;
import com.automine.AutoMineMod.navigation.CameraSteering;
import com.automine.AutoMineMod.navigation.StepResult;
import com.automine.AutoMineMod.navigation.Targeting;
import com.automine.AutoMineMod.navigation.TerrainScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ClientAutoPlayManager {
    private static final double SEARCH_RADIUS = 32.0;
    private static final double ATTACK_RANGE = 3.2;
    private static final double BOW_MIN_RANGE = 4.0;
    private static final double BOW_MAX_RANGE = 16.0;
    private static final int ATTACK_COOLDOWN = 10;
    private static final int BOW_HOLD_TICKS = 22;
    private static final float CAMERA_SMOOTHING = 0.45f;
    private static final float MOVE_HEADING_SMOOTHING = 0.65f;
    private static final double SPRINT_DISTANCE_SQR = 7.0 * 7.0;
    private static final double BRAKE_RANGE_SQR = 2.85 * 2.85;
    private static final int BLACKLIST_TICKS = 200;
    private static final int STUCK_EVENTS_BEFORE_BLACKLIST = 2;
    private static final double MIN_CHASE_HORIZONTAL_SQR = 1.2 * 1.2;
    private static final int AVOID_LOCK_TICKS = 20;
    private static final double AVOID_LOCK_MAX_ANGLE_DEG = 50.0;
    private static final int EXPLORATION_TURN_TICKS = 160;
    private static final int TARGET_STUCK_TICKS = 36;
    private static final double DEFENSE_RADIUS = 8.0;
    private static final double FOOD_SAFE_RADIUS = 10.0;
    private static final double CREEPER_FLEE_DISTANCE = 8.0;
    private static final double FALL_PREVENTION_DISTANCE = 1.4;

    private int cooldown = 0;
    private LivingEntity currentTarget = null;

    private final CameraSteering camera = new CameraSteering();
    private final Map<Integer, Integer> targetBlacklist = new HashMap<>();

    private boolean usingBow = false;
    private int bowTicks = 0;

    private Vec3 lastSafePos = null;
    private Vec3 lastProgressPos = null;
    private int stuckTicks = 0;
    private int stuckEvents = 0;
    private int jumpPulseTicks = 0;
    private boolean fleeingCreeper = false;
    private Creeper creeperThreat = null;

    private Vec3 lockedAvoidDir = null;
    private int avoidLockTicksLeft = 0;
    private boolean moveHeadingActive = false;
    private boolean needJumpAhead = false;
    private int explorationTicks = 0;
    private Vec3 explorationDir = new Vec3(1, 0, 0);
    private int targetStuckTicks = 0;
    private double lastTargetDistance = Double.MAX_VALUE;
    private int lastTargetId = -1;
    private boolean eating = false;
    private int previousCombatSlot = -1;
    private boolean controlsActive = false;

    public void tick(Minecraft client) {
        currentClient = client;
        LocalPlayer player = client.player;
        if (client.level == null || player == null) return;
        if (!AutoMineMod.autoPlayEnabled) {
            if (controlsActive) {
                releaseAllKeys(client);
                resetNavigationMemory();
                currentTarget = null;
                controlsActive = false;
            }
            return;
        }
        controlsActive = true;

        if (!player.isAlive()) {
            releaseAllKeys(client);
            resetNavigationMemory();
            return;
        }

        if (client.gui.screen() != null) {
            releaseAllKeys(client);
            return;
        }

        if (cooldown > 0) cooldown--;
        updateBlacklist();

        syncTargetFromServer(client, player);

        if (currentTarget != null && !isTargetValid(player, currentTarget)) {
            currentTarget = null;
        }
        if (currentTarget == null) {
            currentTarget = findBestTarget(player);
        }
        LivingEntity nearbyThreat = findNearestThreat(player);
        if (nearbyThreat != null && (currentTarget == null
                || player.distanceToSqr(currentTarget) > DEFENSE_RADIUS * DEFENSE_RADIUS)) {
            if (usingBow) {
                stopBowUse(client, player);
            }
            currentTarget = nearbyThreat;
        }
        resetTargetProgressIfNeeded();

        updateLastSafePos(player);

        creeperThreat = Targeting.findNearestCreeper(player, Targeting.CREEPER_FLEE_RADIUS);
        fleeingCreeper = creeperThreat != null
            && player.distanceTo(creeperThreat) < CREEPER_FLEE_DISTANCE;
        if (fleeingCreeper) {
            currentTarget = creeperThreat;
        }

        if (isFallingDangerously(player)) {
            releaseAllKeys(client);
            steerCamera(player, aimPointFor(player));
            return;
        }

        if (player.isInLava()) {
            escapeFluid(client, player);
            steerCamera(player, aimPointFor(player));
            return;
        }

        if (eatIfNeeded(client, player)) {
            releaseMovementKeys(client);
            return;
        }

        boolean swimming = player.isInWater();

        Vec3 desiredDir = computeDesiredDir(player);
        if (currentTarget == null && !fleeingCreeper && !hasNearbyThreat(player)) {
            desiredDir = explorationDirection(player);
        }
        if (isNearUnsafeDrop(player, desiredDir)) {
            releaseMovementKeys(client);
            steerCamera(player, aimPointFor(player));
            return;
        }
        Vec3 moveDir = pickMoveDir(player, desiredDir);
        applyMovement(client, player, moveDir, swimming);

        if (moveDir != null) {
            trackStuck(player);
        } else if (lastProgressPos != null) {
            lastProgressPos = player.position();
        }
        trackTargetReachability(player, moveDir);

        Float idleYawGoal = null;
        if (moveDir == null && currentTarget != null) {
            Vec3 toT = currentTarget.position().subtract(player.position());
            if (toT.horizontalDistanceSqr() > 1.0E-4) {
                idleYawGoal = headingYawOf(new Vec3(toT.x, 0, toT.z));
            }
        }
        steerCamera(player, aimPointFor(player), idleYawGoal);

        if (currentTarget != null) {
            combatTick(client, player, currentTarget);
        } else if (usingBow) {
            player.releaseUsingItem();
            client.options.keyUse.setDown(false);
            usingBow = false;
        }
    }

    private Vec3 aimPointFor(LocalPlayer player) {
        if (fleeingCreeper && creeperThreat != null) {
            return bodyCenter(creeperThreat);
        }
        if (currentTarget != null) {
            return bodyCenter(currentTarget);
        }
        return null;
    }

    private Vec3 bodyCenter(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.75, e.getZ());
    }

    private void syncTargetFromServer(Minecraft client, LocalPlayer player) {
        try {
            int tid = player.getScore();
            if (tid >= 0 && client.level.getEntity(tid) instanceof LivingEntity le) {
                if (!targetBlacklist.containsKey(le.getId())) {
                    currentTarget = le;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isFallingDangerously(LocalPlayer player) {
        return !player.onGround() && player.fallDistance > 3.0f && !player.isInWater() && !player.isInLava();
    }

    private boolean isNearUnsafeDrop(LocalPlayer player, Vec3 desiredDir) {
        if (!player.onGround()) return false;
        Vec3 direction = desiredDir == null
                ? new Vec3(player.getDeltaMovement().x, 0, player.getDeltaMovement().z)
                : new Vec3(desiredDir.x, 0, desiredDir.z);
        if (direction.lengthSqr() < 0.0025) return false;
        direction = direction.normalize();
        Vec3 probe = player.position().add(direction.scale(FALL_PREVENTION_DISTANCE));
        return !TerrainScanner.isSafeStand(player.level(), new BlockPos(
                (int) Math.floor(probe.x), player.blockPosition().getY(), (int) Math.floor(probe.z)));
    }

    private void updateBlacklist() {
        Iterator<Map.Entry<Integer, Integer>> it = targetBlacklist.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) it.remove();
            else entry.setValue(remaining);
        }
    }

    private void blacklistCurrentTarget() {
        if (currentTarget == null) return;
        targetBlacklist.put(currentTarget.getId(), BLACKLIST_TICKS);
        currentTarget = null;
    }

    private void updateLastSafePos(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        if (player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && TerrainScanner.dropDepth(player.level(), feet) == 0
                && TerrainScanner.isSafeStand(player.level(), feet)) {
            lastSafePos = player.position();
        }
    }

    private Vec3 computeDesiredDir(LocalPlayer player) {
        if (player.isInWater() || player.isInLava()) {
            Vec3 escape = TerrainScanner.findEscapeDirection(player.level(), player.position());
            if (escape != null) return escape;
            if (lastSafePos != null) {
                Vec3 to = lastSafePos.subtract(player.position());
                if (to.horizontalDistanceSqr() > 0.01) return new Vec3(to.x, 0, to.z).normalize();
            }
            return null;
        }

        if (fleeingCreeper && creeperThreat != null) {
            Vec3 away = player.position().subtract(creeperThreat.position());
            Vec3 flatAway = new Vec3(away.x, 0, away.z);
            if (flatAway.lengthSqr() > 1.0E-4) return flatAway.normalize();
        }

        if (currentTarget == null) return null;

        Vec3 toTarget = currentTarget.position().subtract(player.position());
        double horizontalSqr = toTarget.x * toTarget.x + toTarget.z * toTarget.z;

        boolean targetBelow = currentTarget.getY() < player.getY() - 1.5;
        if (targetBelow && horizontalSqr < MIN_CHASE_HORIZONTAL_SQR) {
            return null;
        }

        if (horizontalSqr <= ATTACK_RANGE * ATTACK_RANGE * 0.6) return null;

        return new Vec3(toTarget.x, 0, toTarget.z).normalize();
    }

    private boolean eatIfNeeded(Minecraft client, LocalPlayer player) {
        if (eating) {
            if (player.isUsingItem()) {
                client.options.keyUse.setDown(true);
                return true;
            }
            eating = false;
            client.options.keyUse.setDown(false);
            if (previousCombatSlot >= 0) {
                player.getInventory().setSelectedSlot(previousCombatSlot);
                previousCombatSlot = -1;
            }
            return false;
        }
        if (player.getFoodData().getFoodLevel() >= 18
            || (fleeingCreeper && creeperThreat != null
            && player.distanceTo(creeperThreat) < CREEPER_FLEE_DISTANCE)) return false;
        for (int slot = 0; slot < Math.min(9, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null && player.canEat(false)) {
                previousCombatSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(slot);
                client.options.keyUse.setDown(true);
                if (client.gameMode != null) {
                    client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                } else {
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                }
                if (player.isUsingItem()) {
                    eating = true;
                    return true;
                }
                player.getInventory().setSelectedSlot(previousCombatSlot);
                previousCombatSlot = -1;
            }
        }
        return false;
    }

    private boolean hasNearbyThreat(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(FOOD_SAFE_RADIUS);
        return !player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy).isEmpty();
    }

    private Vec3 explorationDirection(LocalPlayer player) {
        if (--explorationTicks <= 0 || !TerrainScanner.evaluateStep(
                player.level(), player.position(), explorationDir).walkable()) {
            explorationTicks = EXPLORATION_TURN_TICKS;
            double angle = Math.random() * 180.0 - 90.0;
            explorationDir = TerrainScanner.rotateY(explorationDir, angle).normalize();
        }
        return explorationDir;
    }

    private Vec3 pickMoveDir(LocalPlayer player, Vec3 desiredDir) {
        if (avoidLockTicksLeft > 0) avoidLockTicksLeft--;
        needJumpAhead = false;
        if (desiredDir == null) {
            lockedAvoidDir = null;
            return null;
        }

        boolean descending = currentTarget != null
                && currentTarget.getY() < player.getY() - 1.5;
        int maxDrop = descending && !fleeingCreeper
            ? TerrainScanner.MAX_CONTROLLED_DESCENT : TerrainScanner.MAX_SAFE_DROP;

        StepResult straight = TerrainScanner.evaluateStep(player.level(), player.position(), desiredDir, maxDrop);
        if (straight.walkable()) {
            lockedAvoidDir = null;
            needJumpAhead = straight.needJump();
            return desiredDir;
        }

        if (straight.needJump()) {
            needJumpAhead = true;
            return desiredDir;
        }

        if (lockedAvoidDir != null && avoidLockTicksLeft > 0) {
            double angleDeg = Math.toDegrees(Math.acos(Math.clamp(
                    lockedAvoidDir.dot(desiredDir), -1.0, 1.0)));
            StepResult lockedStep = TerrainScanner.evaluateStep(
                    player.level(), player.position(), lockedAvoidDir, maxDrop);
            if (lockedStep.walkable() && angleDeg <= AVOID_LOCK_MAX_ANGLE_DEG) {
                avoidLockTicksLeft = AVOID_LOCK_TICKS;
                needJumpAhead = lockedStep.needJump();
                return lockedAvoidDir;
            }
        }

        Vec3 avoided = TerrainScanner.chooseAvoidDirection(
                player.level(), player.position(), desiredDir, 0.0, maxDrop);
        if (avoided != null) {
            lockedAvoidDir = avoided;
            avoidLockTicksLeft = AVOID_LOCK_TICKS;
            StepResult step = TerrainScanner.evaluateStep(
                    player.level(), player.position(), avoided, maxDrop);
            needJumpAhead = step.needJump();
        } else {
            lockedAvoidDir = null;
        }
        return avoided;
    }

    private float headingYawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0f;
    }

    private void applyMovement(Minecraft client, LocalPlayer player, Vec3 moveDir, boolean swimming) {
        boolean fwd = false, sprint = false, jump = false;
        boolean retreating = fleeingCreeper && creeperThreat != null;
        moveHeadingActive = moveDir != null;

        if (moveDir != null) {
            boolean nearTarget = currentTarget != null
                    && player.distanceToSqr(currentTarget) <= BRAKE_RANGE_SQR;
                fwd = !retreating && !nearTarget;

                Vec3 facingDir = retreating
                    ? creeperThreat.position().subtract(player.position())
                    : moveDir;
                float targetYaw = headingYawOf(new Vec3(facingDir.x, 0, facingDir.z).normalize());
            camera.steer(targetYaw, camera.getPitch(), MOVE_HEADING_SMOOTHING);

            boolean farAway = currentTarget != null
                    ? player.distanceToSqr(currentTarget) >= SPRINT_DISTANCE_SQR
                    : fleeingCreeper;
            sprint = farAway && player.onGround() && !swimming && !needJumpAhead;

            if (jumpPulseTicks > 0) {
                jumpPulseTicks--;
                jump = true;
            } else if (needJumpAhead && player.onGround()) {
                jumpPulseTicks = 5;
                jump = true;
            }
        }

        client.options.keyUp.setDown(fwd);
        client.options.keyDown.setDown(retreating && moveDir != null);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keySprint.setDown(sprint);
        client.options.keyJump.setDown(jump || swimming);
    }

    private void trackStuck(LocalPlayer player) {
        if (lastProgressPos == null) {
            lastProgressPos = player.position();
            stuckTicks = 0;
            return;
        }
        double dx = player.getX() - lastProgressPos.x;
        double dz = player.getZ() - lastProgressPos.z;
        double movedSq = dx * dx + dz * dz;
        if (movedSq < 9.0E-4) {
            stuckTicks++;
        } else {
            lastProgressPos = player.position();
            if (movedSq > 0.04) stuckTicks = 0;
        }
        if (stuckTicks >= 14) {
            stuckEvents++;
            jumpPulseTicks = Math.max(jumpPulseTicks, 6);
            lockedAvoidDir = null;
            avoidLockTicksLeft = 0;
            stuckTicks = 0;
            lastProgressPos = null;
            if (stuckEvents >= STUCK_EVENTS_BEFORE_BLACKLIST && !fleeingCreeper) {
                blacklistCurrentTarget();
                resetNavigationMemory();
            }
        }
    }

    private void steerCamera(LocalPlayer player, Vec3 aimPoint) {
        steerCamera(player, aimPoint, null);
    }

    private void steerCamera(LocalPlayer player, Vec3 aimPoint, Float yawGoal) {
        float targetYaw = yawGoal != null ? yawGoal : camera.getYaw();
        float targetPitch;
        if (aimPoint != null) {
            Vec3 eye = player.getEyePosition();
            Vec3 aim = aimPoint.subtract(eye);
            targetPitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
        } else {
            targetPitch = 0.0f;
        }
        float smoothing = moveHeadingActive ? MOVE_HEADING_SMOOTHING : CAMERA_SMOOTHING;
        camera.steer(targetYaw, targetPitch, smoothing);
        player.setYRot(camera.getYaw());
        player.setXRot(camera.getPitch());
    }

    private boolean hasClearShot(LocalPlayer player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = bodyCenter(target);
        BlockHitResult hit = player.level().clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void combatTick(Minecraft client, LocalPlayer player, LivingEntity target) {
        double distSqr = player.distanceToSqr(target);
        boolean targetIsCreeper = Targeting.isCreeper(target);
        boolean wantsBow = targetIsCreeper && hasBowAndArrows(player);
        selectCombatWeapon(player, wantsBow);
        ItemStack handStack = player.getMainHandItem();
        boolean isBow = handStack.getItem() instanceof BowItem;

        if (isBow) {
            boolean inBowRange = distSqr >= BOW_MIN_RANGE * BOW_MIN_RANGE
                    && distSqr <= BOW_MAX_RANGE * BOW_MAX_RANGE;
            boolean safeDistance = !targetIsCreeper
                    || Math.sqrt(distSqr) >= Targeting.CREEPER_SAFE_DISTANCE - 1.0;
            boolean clearShot = inBowRange && safeDistance && hasClearShot(player, target);
            if (clearShot) {
                if (!usingBow) {
                    if (client.gameMode != null) {
                        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    } else {
                        player.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                    usingBow = true;
                    bowTicks = 0;
                } else {
                    bowTicks++;
                }
                client.options.keyUse.setDown(true);
                if (bowTicks >= BOW_HOLD_TICKS) {
                    if (client.gameMode != null) client.gameMode.releaseUsingItem(player);
                    else player.releaseUsingItem();
                    client.options.keyUse.setDown(false);
                    usingBow = false;
                    cooldown = ATTACK_COOLDOWN;
                }
            } else if (usingBow) {
                if (client.gameMode != null) client.gameMode.releaseUsingItem(player);
                else player.releaseUsingItem();
                client.options.keyUse.setDown(false);
                usingBow = false;
            }
        } else {
            if (usingBow) {
                stopBowUse(client, player);
            }
            boolean meleeSafe = !(targetIsCreeper && Math.sqrt(distSqr) < Targeting.CREEPER_SAFE_DISTANCE - 1.0);
            boolean hasMeleeWeapon = !(player.getMainHandItem().getItem() instanceof BowItem)
                    && player.getMainHandItem().get(DataComponents.WEAPON) != null;
            if (hasMeleeWeapon && distSqr <= ATTACK_RANGE * ATTACK_RANGE
                    && cooldown <= 0 && meleeSafe) {
                if (client.gameMode != null) {
                    client.gameMode.attack(player, target);
                }
                player.swing(InteractionHand.MAIN_HAND, true);
                cooldown = ATTACK_COOLDOWN;
            }
        }
    }

    private void selectCombatWeapon(LocalPlayer player, boolean preferBow) {
        if (preferBow && hasBowAndArrows(player) && selectHotbarItem(player, BowItem.class)) return;
        selectWeaponItem(player);
    }

    private boolean hasBowAndArrows(LocalPlayer player) {
        boolean hasBow = false;
        boolean hasArrows = false;
        for (int slot = 0; slot < Math.min(9, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BowItem) hasBow = true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.ARROW)) {
                hasArrows = true;
                break;
            }
        }
        return hasBow && hasArrows;
    }

    private boolean selectHotbarItem(LocalPlayer player, Class<?> itemClass) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack selected = player.getInventory().getItem(selectedSlot);
        if (itemClass.isInstance(selected.getItem())) return true;
        for (int slot = 0; slot < Math.min(9, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (itemClass.isInstance(stack.getItem())) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    private boolean selectWeaponItem(LocalPlayer player) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack selected = player.getInventory().getItem(selectedSlot);
        if (!(selected.getItem() instanceof BowItem)
                && selected.get(DataComponents.WEAPON) != null) return true;
        for (int slot = 0; slot < Math.min(9, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof BowItem) && stack.get(DataComponents.WEAPON) != null) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    private LivingEntity findNearestThreat(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(DEFENSE_RADIUS);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isAquatic(e)
                        && hasClearShot(player, e))) {
            double distance = player.distanceToSqr(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    private void stopBowUse(Minecraft client, LocalPlayer player) {
        if (client.gameMode != null) client.gameMode.releaseUsingItem(player);
        else player.releaseUsingItem();
        client.options.keyUse.setDown(false);
        usingBow = false;
        bowTicks = 0;
    }

    private boolean isTargetValid(LocalPlayer player, LivingEntity target) {
        return target.isAlive()
                && player.level().getEntity(target.getId()) != null
                && player.distanceToSqr(target) <= SEARCH_RADIUS * SEARCH_RADIUS;
    }

    private void resetTargetProgressIfNeeded() {
        int targetId = currentTarget == null ? -1 : currentTarget.getId();
        if (targetId != lastTargetId) {
            lastTargetId = targetId;
            targetStuckTicks = 0;
            lastTargetDistance = Double.MAX_VALUE;
        }
    }

    private void trackTargetReachability(LocalPlayer player, Vec3 moveDir) {
        if (currentTarget == null) return;
        boolean clearShot = hasClearShot(player, currentTarget);
        if (!clearShot) {
            targetStuckTicks++;
        } else {
            double distance = player.distanceToSqr(currentTarget);
            if (distance <= ATTACK_RANGE * ATTACK_RANGE || distance < lastTargetDistance - 0.04) {
                targetStuckTicks = 0;
            } else if (moveDir == null || !TerrainScanner.hasVisibleRoute(
                    player.level(), player.position(), currentTarget.position())) {
                targetStuckTicks++;
            }
            lastTargetDistance = distance;
        }
        if (targetStuckTicks >= TARGET_STUCK_TICKS) {
            blacklistCurrentTarget();
            resetNavigationMemory();
            resetTargetProgressIfNeeded();
        }
    }

    private LivingEntity findBestTarget(LocalPlayer player) {
        Vec3 pos = player.position();
        AABB box = new AABB(
                pos.x - SEARCH_RADIUS, pos.y - SEARCH_RADIUS, pos.z - SEARCH_RADIUS,
                pos.x + SEARCH_RADIUS, pos.y + SEARCH_RADIUS, pos.z + SEARCH_RADIUS
        );

        List<LivingEntity> list = player.level().getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isAquatic(e)
                && hasClearShot(player, e));

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            if (targetBlacklist.containsKey(e.getId())) continue;
            double d = player.distanceToSqr(e);
            double score = d;
            if (!hasClearShot(player, e)) score += 4.0;
            if (Targeting.isCreeper(e)) score += Targeting.CREEPER_TARGET_PENALTY;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private void escapeFluid(Minecraft client, LocalPlayer player) {
        client.options.keyJump.setDown(true);
        client.options.keySprint.setDown(false);
        Vec3 escape = TerrainScanner.findEscapeDirection(player.level(), player.position());
        Vec3 dir = null;
        if (escape != null) {
            dir = escape;
        } else if (lastSafePos != null) {
            Vec3 to = lastSafePos.subtract(player.position());
            if (to.horizontalDistanceSqr() > 0.01) dir = new Vec3(to.x, 0, to.z).normalize();
        }
        if (dir != null) {
            client.options.keyUp.setDown(true);
            camera.steer(headingYawOf(dir), camera.getPitch(), MOVE_HEADING_SMOOTHING);
            player.setYRot(camera.getYaw());
        } else {
            client.options.keyUp.setDown(false);
        }
    }

    private void releaseAllKeys(Minecraft client) {
        releaseMovementKeys(client);
        if (client.player != null && client.player.isUsingItem()) {
            client.player.releaseUsingItem();
        }
        client.options.keyUse.setDown(false);
    }

    private void releaseMovementKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private void resetNavigationMemory() {
        lastSafePos = null;
        lastProgressPos = null;
        stuckTicks = 0;
        stuckEvents = 0;
        jumpPulseTicks = 0;
        usingBow = false;
        fleeingCreeper = false;
        creeperThreat = null;
        lockedAvoidDir = null;
        avoidLockTicksLeft = 0;
        explorationTicks = 0;
        explorationDir = new Vec3(1, 0, 0);
        eating = false;
        previousCombatSlot = -1;
        camera.invalidate();
    }

    public void reset() {
        if (currentClient != null && currentClient.player != null) {
            LocalPlayer player = currentClient.player;
            if (player.isUsingItem()) player.releaseUsingItem();
            if (previousCombatSlot >= 0) {
                player.getInventory().setSelectedSlot(previousCombatSlot);
            }
        }
        currentTarget = null;
        controlsActive = false;
        cooldown = 0;
        targetBlacklist.clear();
        resetNavigationMemory();
        if (currentClient != null) {
            releaseAllKeys(currentClient);
        }
    }

    private Minecraft currentClient;
}
