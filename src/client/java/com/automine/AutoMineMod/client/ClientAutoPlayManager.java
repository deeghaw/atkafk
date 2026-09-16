package com.automine.AutoMineMod.client;

import com.automine.AutoMineMod.AutoMineMod;
import com.automine.AutoMineMod.client.ai.AiIntent;
import com.automine.AutoMineMod.client.ai.AutonomousAgent;
import com.automine.AutoMineMod.navigation.CameraSteering;
import com.automine.AutoMineMod.navigation.HumanMovement;
import com.automine.AutoMineMod.navigation.StepResult;
import com.automine.AutoMineMod.navigation.Targeting;
import com.automine.AutoMineMod.navigation.TerrainScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ClientAutoPlayManager {
    private static final double ATTACK_RANGE = 3.2;
    private static final double BOW_MAX_RANGE = 18.0;
    private static final double SHOOT_MIN_DIST = Targeting.CREEPER_SAFE_DISTANCE + 1.5;
    private static final double CREEPER_DANGER_DIST = 5.0;
    private static final double DRAW_ABORT_DIST = 4.5;
    private static final int MIN_CHARGE_TO_RELEASE_EARLY = 14;
    private static final int ATTACK_COOLDOWN = 10;
    private static final int BOW_HOLD_TICKS = 21;
    private static final float AIM_SMOOTHING = 0.34f;
    private static final float HEADING_SMOOTHING = 0.38f;
    private static final float FAST_SMOOTHING = 0.50f;
    private static final float GOAL_TRACK_SMOOTHING = 0.50f;
    private static final float MAX_YAW_SPEED_AIM = 16.0f;
    private static final float MAX_PITCH_SPEED_AIM = 11.0f;
    private static final float MAX_YAW_SPEED_HEADING = 22.0f;
    private static final float MAX_PITCH_SPEED_HEADING = 14.0f;
    private static final float MAX_YAW_SPEED_FAST = 26.0f;
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
    private static final double POKE_RANGE = 3.0;
    private static final float POKE_CONE_DEG = 65.0f;
    private static final int POKE_AIM_MAX_TICKS = 5;
    private static final int WEAPON_SWAP_GRACE_TICKS = 2;
    private static final int FORCE_ESCAPE_TICKS = 15;

    private int cooldown = 0;
    private LivingEntity currentTarget = null;

    private final CameraSteering camera = new CameraSteering();
    private final Map<Integer, Integer> targetBlacklist = new HashMap<>();

    private boolean usingBow = false;
    private int bowTicks = 0;
    private int bowDrawVerify = 0;
    private boolean shootStance = false;
    private boolean urgentEscape = false;
    private int weaponSwapGrace = 0;
    private int forceEscapeTicks = 0;
    private LivingEntity pokeTarget = null;
    private int pokeTicks = 0;
    private float smoothYawGoal;
    private float smoothPitchGoal;
    private boolean smoothGoalsInitialized = false;

    private Vec3 lastSafePos = null;
    private Vec3 lastProgressPos = null;
    private int stuckTicks = 0;
    private int stuckEvents = 0;
    private int recoveryTicks = 0;
    private Vec3 recoveryDir = null;
    private int jumpPulseTicks = 0;
    private Creeper creeperThreat = null;
    private boolean creeperFleeBlocked = false;

    private Vec3 lockedAvoidDir = null;
    private int avoidLockTicksLeft = 0;
    private boolean needJumpAhead = false;
    private int explorationTicks = 0;
    private Vec3 explorationDir = new Vec3(1, 0, 0);
    private int targetStuckTicks = 0;
    private double lastTargetDistance = Double.MAX_VALUE;
    private int lastTargetId = -1;
    private boolean eating = false;
    private int previousCombatSlot = -1;
    private boolean controlsActive = false;

    // Human-like behavior state
    private int gameTickCount = 0;
    private int hesitationTicks = 0;
    private boolean isHesitating = false;
    private int lookAroundTicks = 0;
    private float lookAroundYawTarget = 0;
    private boolean isLookingAround = false;
    private boolean strafeActive = false;
    private int strafeBurstTicksLeft = 0;
    private int strafeGapTicksLeft = 0;
    private double strafeOffset = 0;

    private final AutonomousAgent agent;
    private AiIntent aiIntent;
    private int aiHoldTicks;
    private int ticksSinceAiDecision;
    private boolean aiChatSent;
    private BlockPos collectTarget;

    public ClientAutoPlayManager() {
        this(null);
    }

    public ClientAutoPlayManager(AutonomousAgent agent) {
        this.agent = agent;
    }

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

        gameTickCount++;
        tickAi(client, player);

        if (isHesitating) {
            hesitationTicks--;
            if (hesitationTicks <= 0) {
                isHesitating = false;
            }
            updateCameraAndApply(player, null);
            return;
        }

        if (isLookingAround) {
            lookAroundTicks--;
            if (lookAroundTicks <= 0) {
                isLookingAround = false;
            }
            updateCameraAndApply(player, null);
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            return;
        }

        if (HumanMovement.shouldHesitate() && !isHesitating && currentTarget == null && creeperThreat == null) {
            isHesitating = true;
            hesitationTicks = HumanMovement.getHesitationDuration();
            updateCameraAndApply(player, null);
            return;
        }

        if (HumanMovement.shouldLookAround() && !isLookingAround && currentTarget == null && creeperThreat == null) {
            isLookingAround = true;
            lookAroundTicks = HumanMovement.getLookAroundDuration();
            lookAroundYawTarget = camera.getYaw() + HumanMovement.getLookAroundYaw();
            updateCameraAndApply(player, null);
            return;
        }

        if (cooldown > 0) cooldown--;
        if (weaponSwapGrace > 0) weaponSwapGrace--;
        if (forceEscapeTicks > 0) forceEscapeTicks--;
        if (recoveryTicks > 0) recoveryTicks--;
        else recoveryDir = null;
        updateBlacklist();

        creeperThreat = Targeting.findNearestCreeper(player, Targeting.CREEPER_FLEE_RADIUS);
        double creeperDist = creeperThreat != null ? player.distanceTo(creeperThreat) : Double.MAX_VALUE;
        creeperFleeBlocked = creeperThreat != null
            && TerrainScanner.chooseFleeDirection(
                player.level(), player.position(), creeperThreat.position(),
                TerrainScanner.MAX_CONTROLLED_DESCENT, TerrainScanner.HAZARD_MARGIN) == null;
        urgentEscape = creeperThreat != null
            && ((!creeperFleeBlocked && creeperDist < CREEPER_DANGER_DIST) || forceEscapeTicks > 0);

        if (creeperThreat != null) {
            currentTarget = creeperThreat;
        } else {
            if (currentTarget != null && !isTargetValid(player, currentTarget)) {
                currentTarget = null;
            }
            if (currentTarget == null) {
                currentTarget = findBestTarget(player);
            }
            LivingEntity nearbyThreat = findNearestThreat(player);
            if (nearbyThreat != null && (currentTarget == null
                    || player.distanceToSqr(currentTarget) > DEFENSE_RADIUS * DEFENSE_RADIUS)) {
                if (usingBow) cancelDraw(client, player);
                currentTarget = nearbyThreat;
            }
            if (aiWantsPeaceful() && currentTarget != null
                    && player.distanceToSqr(currentTarget) > DEFENSE_RADIUS * DEFENSE_RADIUS) {
                currentTarget = null;
            }
        }

        if (aiIntent != null && aiIntent.type() == AiIntent.Type.WAIT
                && creeperThreat == null && currentTarget == null) {
            updateCameraAndApply(player, null);
            releaseMovementKeys(client);
            client.options.keyAttack.setDown(false);
            return;
        }
        if (aiIntent != null && aiIntent.type() == AiIntent.Type.LOOK_AROUND
                && !isLookingAround && creeperThreat == null && currentTarget == null) {
            isLookingAround = true;
            lookAroundTicks = HumanMovement.getLookAroundDuration();
            lookAroundYawTarget = camera.getYaw() + HumanMovement.getLookAroundYaw();
        }

        resetTargetProgressIfNeeded();
        updateLastSafePos(player);

        boolean bowReady = hasBowAndArrows(player);
        if (usingBow && !bowReady) cancelDraw(client, player);
        boolean wantShoot = creeperThreat != null && bowReady && !urgentEscape && cooldown <= 0
                && creeperDist >= SHOOT_MIN_DIST && creeperDist <= BOW_MAX_RANGE
                && hasClearShot(player, creeperThreat);
        boolean emergencyBowShot = creeperThreat != null && bowReady && creeperFleeBlocked
            && hasClearShot(player, creeperThreat);
        if (wantShoot || emergencyBowShot
            || (usingBow && creeperThreat != null && !urgentEscape)) {
            shootStance = true;
        } else {
            shootStance = false;
        }

        if (isFallingDangerously(player)) {
            releaseAllKeys(client);
            updateCameraAndApply(player, null);
            return;
        }

        if (player.isInLava()) {
            escapeFluid(client, player);
            return;
        }

        updatePokeTarget(player);

        if (eatIfNeeded(client, player)) {
            releaseMovementKeys(client);
            return;
        }

        boolean swimming = player.isInWater();

        Vec3 desiredDir = computeDesiredDir(player);
        if (creeperThreat == null && currentTarget == null && !hasNearbyThreat(player)) {
            desiredDir = explorationDirection(player);
        }
        desiredDir = applyAiDesiredDir(player, desiredDir);
        Vec3 moveDir = pickMoveDir(player, desiredDir);
        if (moveDir != null && isNearUnsafeDrop(player, moveDir)) {
            releaseMovementKeys(client);
            moveDir = null;
        }
        applyMovement(client, player, moveDir, swimming);

        if (moveDir != null && !shootStance && pokeTarget == null) {
            trackStuck(player);
        } else if (lastProgressPos != null) {
            lastProgressPos = player.position();
        }
        if (creeperThreat == null) {
            trackTargetReachability(player, moveDir);
        }

        updateCameraAndApply(player, moveDir);
        applyCollectMining(client, player, moveDir);

        if (currentTarget != null) {
            combatTick(client, player, currentTarget);
        } else if (usingBow) {
            cancelDraw(client, player);
        }
    }

    private void tickAi(Minecraft client, LocalPlayer player) {
        if (agent == null || !agent.isEnabled()) {
            client.options.keyAttack.setDown(false);
            return;
        }
        AiIntent next = agent.pollIntent();
        if (next != null) {
            aiIntent = next;
            aiHoldTicks = next.holdTicks();
            ticksSinceAiDecision = 0;
            aiChatSent = false;
            collectTarget = resolveCollectTarget(player, next);
            if (next.type() == AiIntent.Type.CHAT && next.message() != null && !next.message().isBlank()) {
                sendAiChat(client, next.message());
            }
        }
        ticksSinceAiDecision++;
        if (aiHoldTicks > 0) aiHoldTicks--;
        int interval = Math.max(20, agent.config().decisionIntervalTicks);
        if (aiHoldTicks <= 0 && ticksSinceAiDecision >= interval) {
            agent.requestDecision(player);
            ticksSinceAiDecision = 0;
            aiHoldTicks = interval;
        }
        if (gameTickCount % 20 == 0) {
            player.sendOverlayMessage(Component.literal(agent.statusLine()));
        }
    }

    private void sendAiChat(Minecraft client, String message) {
        if (aiChatSent || client.getConnection() == null) return;
        String trimmed = message.length() > 100 ? message.substring(0, 100) : message;
        client.getConnection().sendChat(trimmed);
        aiChatSent = true;
        agent.noteResult("chat enviado");
    }

    private Vec3 applyAiDesiredDir(LocalPlayer player, Vec3 scripted) {
        if (agent == null || !agent.isEnabled() || aiIntent == null) return scripted;
        if (creeperThreat != null || player.isInLava() || isFallingDangerously(player)) return scripted;
        return switch (aiIntent.type()) {
            case FLEE -> fleeDirection(player);
            case GO_TO -> toward(player, aiIntent.x(), aiIntent.z());
            case COLLECT -> collectTarget != null
                    ? toward(player, collectTarget.getX() + 0.5, collectTarget.getZ() + 0.5)
                    : scripted;
            case EXPLORE -> {
                if (aiIntent.yaw() != null) {
                    double rad = Math.toRadians(aiIntent.yaw());
                    yield new Vec3(-Math.sin(rad), 0, Math.cos(rad));
                }
                yield scripted;
            }
            case WAIT, LOOK_AROUND, CHAT -> null;
            case EAT, COMBAT, IDLE -> scripted;
        };
    }

    private Vec3 toward(LocalPlayer player, Double x, Double z) {
        if (x == null || z == null) return null;
        Vec3 to = new Vec3(x - player.getX(), 0, z - player.getZ());
        if (to.horizontalDistanceSqr() < 1.0) {
            if (agent != null) agent.noteResult("chegou ao destino");
            return null;
        }
        return to.normalize();
    }

    private Vec3 fleeDirection(LocalPlayer player) {
        LivingEntity threat = findNearestThreat(player);
        if (threat == null) return explorationDirection(player);
        Vec3 away = player.position().subtract(threat.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1.0E-4) return explorationDirection(player);
        return flat.normalize();
    }

    private BlockPos resolveCollectTarget(LocalPlayer player, AiIntent intent) {
        if (intent.blockPos() != null) return intent.blockPos();
        String want = intent.blockType();
        if (want == null || want.isBlank()) return null;
        String needle = want.toLowerCase();
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -4; dy <= 5; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    String id = BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).getPath();
                    if (!id.contains(needle) && !needle.contains(id)) continue;
                    double dist = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void applyCollectMining(Minecraft client, LocalPlayer player, Vec3 moveDir) {
        boolean collecting = agent != null && agent.isEnabled()
                && aiIntent != null && aiIntent.type() == AiIntent.Type.COLLECT
                && collectTarget != null
                && creeperThreat == null
                && !usingBow
                && !eating;
        if (!collecting) {
            if (currentTarget == null) client.options.keyAttack.setDown(false);
            return;
        }
        if (player.level().getBlockState(collectTarget).isAir()) {
            if (agent != null) agent.noteResult("bloco coletado");
            collectTarget = resolveCollectTarget(player, aiIntent);
            client.options.keyAttack.setDown(false);
            return;
        }
        Vec3 look = Vec3.atCenterOf(collectTarget);
        float yaw = headingYawOf(look.subtract(player.position()));
        float pitch = pitchTo(player, look);
        camera.steer(yaw, pitch, FAST_SMOOTHING, MAX_YAW_SPEED_FAST, MAX_PITCH_SPEED_HEADING);
        player.setYRot(camera.getYaw());
        player.setXRot(camera.getPitch());
        boolean inReach = player.getEyePosition().distanceTo(look) < 4.8;
        client.options.keyAttack.setDown(inReach);
        if (moveDir == null && !inReach && agent != null) {
            agent.noteResult("coleta sem caminho ate o bloco");
        }
    }

    private Vec3 bodyCenter(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.75, e.getZ());
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
        return !TerrainScanner.evaluateStep(player.level(), player.position(), direction).walkable();
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

        if (recoveryDir != null && recoveryTicks > 0) return recoveryDir;

        if (creeperThreat != null) {
            if (!hasBowAndArrows(player)) {
                Vec3 toCreeper = creeperThreat.position().subtract(player.position());
                Vec3 flatToCreeper = new Vec3(toCreeper.x, 0, toCreeper.z);
                if (flatToCreeper.lengthSqr() > ATTACK_RANGE * ATTACK_RANGE * 0.6) {
                    return flatToCreeper.normalize();
                }
                return null;
            }
            if (shootStance || creeperFleeBlocked) return null;
            Vec3 away = player.position().subtract(creeperThreat.position());
            Vec3 flatAway = new Vec3(away.x, 0, away.z);
            if (flatAway.lengthSqr() > 1.0E-4) return flatAway.normalize();
            return null;
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

    private void updatePokeTarget(LocalPlayer player) {
        if (pokeTarget != null && (!pokeTarget.isAlive()
                || player.distanceToSqr(pokeTarget) > (ATTACK_RANGE + 0.4) * (ATTACK_RANGE + 0.4))) {
            pokeTarget = null;
            pokeTicks = 0;
        }
        boolean canPoke = creeperThreat != null && !shootStance && !usingBow
                && !urgentEscape && cooldown <= 0;
        if (!canPoke) {
            pokeTarget = null;
            pokeTicks = 0;
            return;
        }
        if (pokeTarget == null) {
            pokeTarget = findPokeTarget(player);
            if (pokeTarget == null) return;
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.getItem() instanceof BowItem || hand.get(DataComponents.WEAPON) == null) {
            if (selectWeaponItem(player)) weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
            return;
        }
        if (weaponSwapGrace > 0) return;
        pokeTicks++;
    }

    private LivingEntity findPokeTarget(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(POKE_RANGE);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isCreeper(e))) {
            double distance = player.distanceToSqr(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean eatIfNeeded(Minecraft client, LocalPlayer player) {
        if (eating) {
            if ((shootStance || usingBow || urgentEscape) && player.isUsingItem()) {
                player.releaseUsingItem();
                client.options.keyUse.setDown(false);
                eating = false;
                if (previousCombatSlot >= 0) {
                    player.getInventory().setSelectedSlot(previousCombatSlot);
                    previousCombatSlot = -1;
                    weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
                }
                return false;
            }
            if (player.isUsingItem()) {
                client.options.keyUse.setDown(true);
                return true;
            }
            eating = false;
            client.options.keyUse.setDown(false);
            if (previousCombatSlot >= 0) {
                player.getInventory().setSelectedSlot(previousCombatSlot);
                previousCombatSlot = -1;
                weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
            }
            return false;
        }
        int foodLimit = (aiIntent != null && aiIntent.type() == AiIntent.Type.EAT) ? 20 : 18;
        if (player.getFoodData().getFoodLevel() >= foodLimit
            || shootStance || usingBow || urgentEscape
            || (creeperThreat != null
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
                    weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
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
            explorationTicks = EXPLORATION_TURN_TICKS + HumanMovement.getJumpVariation() * 10;

            double curiosity = HumanMovement.getExplorationCuriosity(player.position().distanceTo(
                    lastSafePos != null ? lastSafePos : player.position()));

            double angle = HumanMovement.getExplorationDeviation();

            if (curiosity > 0.7 && lastSafePos != null) {
                Vec3 toHome = lastSafePos.subtract(player.position());
                if (toHome.horizontalDistanceSqr() > 100) {
                    double homeAngle = Math.toDegrees(Math.atan2(toHome.z, toHome.x)) - 90.0;
                    double currentAngle = Math.toDegrees(Math.atan2(explorationDir.z, explorationDir.x)) - 90.0;
                    double angleDiff = Mth.wrapDegrees(homeAngle - currentAngle);
                    angle = angle * 0.6 + angleDiff * 0.4;
                }
            }

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

        boolean descending = creeperThreat == null && currentTarget != null
                && currentTarget.getY() < player.getY() - 1.5;
        int maxDrop = descending
            ? TerrainScanner.MAX_CONTROLLED_DESCENT : TerrainScanner.MAX_SAFE_DROP;
        double margin = TerrainScanner.HAZARD_MARGIN;

        if (creeperThreat != null && hasBowAndArrows(player) && !creeperFleeBlocked) {
            int fleeMaxDrop = Math.max(maxDrop, TerrainScanner.MAX_CONTROLLED_DESCENT);
            Vec3 flee = TerrainScanner.chooseFleeDirection(
                    player.level(), player.position(), creeperThreat.position(), fleeMaxDrop, margin);
            StepResult step = TerrainScanner.evaluateStep(
                    player.level(), player.position(), flee, fleeMaxDrop);
            needJumpAhead = step.needJump();
            lockedAvoidDir = null;
            return flee;
        }

        StepResult straight = TerrainScanner.evaluateStep(player.level(), player.position(), desiredDir, maxDrop);
        boolean straightDirty = straight.walkable()
                && TerrainScanner.hasHazardAhead(player.level(), player.position(), desiredDir, margin);
        if (straight.walkable() && !straightDirty) {
            lockedAvoidDir = null;
            needJumpAhead = straight.needJump();
            return desiredDir;
        }

        if (straight.needJump()) {
            needJumpAhead = true;
            return desiredDir;
        }

        if (lockedAvoidDir != null && avoidLockTicksLeft > 0) {
            double angleDeg = Math.toDegrees(Math.acos(Mth.clamp(
                    lockedAvoidDir.dot(desiredDir), -1.0, 1.0)));
            StepResult lockedStep = TerrainScanner.evaluateStep(
                    player.level(), player.position(), lockedAvoidDir, maxDrop);
            if (lockedStep.walkable() && angleDeg <= AVOID_LOCK_MAX_ANGLE_DEG
                    && !TerrainScanner.hasHazardAhead(
                            player.level(), player.position(), lockedAvoidDir, margin)) {
                avoidLockTicksLeft = AVOID_LOCK_TICKS;
                needJumpAhead = lockedStep.needJump();
                return lockedAvoidDir;
            }
        }

        Vec3 avoided = TerrainScanner.chooseAvoidDirection(
                player.level(), player.position(), desiredDir, 0.0, maxDrop, margin);
        if (avoided == null && straightDirty) {
            lockedAvoidDir = null;
            needJumpAhead = straight.needJump();
            return desiredDir;
        }
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

    private float pitchTo(LocalPlayer player, Vec3 point) {
        Vec3 aim = point.subtract(player.getEyePosition());
        return (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
    }

    private Vec3 bowAimPoint(LocalPlayer player, Creeper creeper) {
        Vec3 center = bodyCenter(creeper);
        Vec3 vel = creeper.getDeltaMovement();
        double dist = player.getEyePosition().distanceTo(center);
        int flightTicks = (int) Math.max(1.0, dist / 3.0);
        Vec3 lead = new Vec3(vel.x * flightTicks, 0, vel.z * flightTicks);
        if (lead.lengthSqr() > 2.5 * 2.5) lead = lead.normalize().scale(2.5);
        double drop = Math.min(dist * dist * 0.003, 1.6);
        return center.add(lead).add(0.0, drop, 0.0);
    }

    private void applyMovement(Minecraft client, LocalPlayer player, Vec3 moveDir, boolean swimming) {
        boolean fwd = false, back = false, left = false, right = false, sprint = false, jump = false;

        updateStrafeState();
        boolean isCombat = currentTarget != null || creeperThreat != null;
        strafeOffset = HumanMovement.getStrafeOffset(gameTickCount, isCombat);
        int strafeAction = HumanMovement.getStrafeAction(strafeOffset, strafeActive);

        if (creeperThreat != null) {
            if (shootStance) {
                fwd = false;
                back = false;
            } else if (usingBow && !urgentEscape) {
                back = moveDir != null;
            } else {
                fwd = moveDir != null;
                sprint = fwd && urgentEscape && player.onGround() && !swimming;
            }
        } else if (moveDir != null) {
            boolean nearTarget = currentTarget != null
                    && player.distanceToSqr(currentTarget) <= BRAKE_RANGE_SQR;
            fwd = !nearTarget;
            boolean farAway = currentTarget != null
                    && player.distanceToSqr(currentTarget) >= SPRINT_DISTANCE_SQR;
            sprint = farAway && player.onGround() && !swimming && !needJumpAhead;
        }

        if (strafeAction > 0) {
            right = true;
        } else if (strafeAction < 0) {
            left = true;
        }

        if (jumpPulseTicks > 0) {
            jumpPulseTicks--;
            jump = true;
        } else if (needJumpAhead && player.onGround() && (fwd || back)) {
            jumpPulseTicks = 5;
            jump = true;
        } else if (HumanMovement.shouldExtraJump() && player.onGround() && currentTarget != null) {
            jumpPulseTicks = 3 + HumanMovement.getJumpVariation();
            jump = true;
        }

        client.options.keyUp.setDown(fwd);
        client.options.keyDown.setDown(back);
        client.options.keyLeft.setDown(left);
        client.options.keyRight.setDown(right);
        client.options.keySprint.setDown(sprint);
        client.options.keyJump.setDown(jump || swimming);
    }

    private void updateStrafeState() {
        if (strafeGapTicksLeft > 0) {
            strafeGapTicksLeft--;
            if (strafeGapTicksLeft <= 0) {
                strafeActive = false;
            }
        } else if (strafeActive) {
            strafeBurstTicksLeft--;
            if (strafeBurstTicksLeft <= 0) {
                strafeActive = false;
                strafeGapTicksLeft = HumanMovement.getStrafeGapDuration();
            }
        } else if (HumanMovement.shouldStartStrafeBurst()) {
            strafeActive = true;
            strafeBurstTicksLeft = HumanMovement.getStrafeBurstDuration();
        }
    }

    private void trackStuck(LocalPlayer player) {
        if (lastProgressPos == null) {
            lastProgressPos = player.position();
            stuckTicks = 0;
            return;
        }
        double dx = player.getX() - lastProgressPos.x;
        double dy = player.getY() - lastProgressPos.y;
        double dz = player.getZ() - lastProgressPos.z;
        double movedSq = dx * dx + dy * dy + dz * dz;
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
            Vec3 recoveryHeading = new Vec3(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
            if (recoveryHeading.lengthSqr() < 1.0E-4) {
                double yaw = Math.toRadians(player.getYRot());
                recoveryHeading = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            }
            recoveryDir = TerrainScanner.chooseAvoidDirection(
                    player.level(), player.position(), recoveryHeading,
                    90.0, TerrainScanner.MAX_CONTROLLED_DESCENT, TerrainScanner.HAZARD_MARGIN);
            if (recoveryDir == null) {
                recoveryDir = TerrainScanner.rotateY(new Vec3(1, 0, 0), player.getYRot() + 90.0).normalize();
            }
            recoveryTicks = 40;
            stuckTicks = 0;
            lastProgressPos = null;
            if (stuckEvents >= STUCK_EVENTS_BEFORE_BLACKLIST && creeperThreat == null) {
                blacklistCurrentTarget();
                if (agent != null) agent.noteResult("preso; alvo ignorado");
                resetNavigationMemory();
            }
        }
    }

    private void updateCameraAndApply(LocalPlayer player, Vec3 moveDir) {
        Float rawYaw = null;
        Float rawPitch = null;
        float responsiveness = HEADING_SMOOTHING;
        float maxYaw = MAX_YAW_SPEED_HEADING;
        float maxPitch = MAX_PITCH_SPEED_HEADING;

        if (isLookingAround) {
            rawYaw = lookAroundYawTarget;
            rawPitch = camera.getPitch() + (float)(Math.sin(gameTickCount * 0.3) * 5.0);
            responsiveness = 0.25f;
            maxYaw = 8.0f;
            maxPitch = 6.0f;
        } else if (pokeTarget != null) {
            rawYaw = headingYawOf(pokeTarget.position().subtract(player.position()));
            rawPitch = pitchTo(player, bodyCenter(pokeTarget));
            responsiveness = FAST_SMOOTHING;
            maxYaw = MAX_YAW_SPEED_FAST;
        } else if (creeperThreat != null) {
            boolean faceAway = !shootStance && !usingBow;
            Vec3 to;
            if (faceAway) {
                to = moveDir != null
                        ? new Vec3(moveDir.x, 0, moveDir.z)
                        : player.position().subtract(creeperThreat.position());
            } else {
                to = creeperThreat.position().subtract(player.position());
            }
            rawYaw = headingYawOf(new Vec3(to.x, 0, to.z));
            if (!faceAway) {
                boolean aiming = shootStance || usingBow;
                Vec3 aim = aiming ? bowAimPoint(player, creeperThreat) : bodyCenter(creeperThreat);
                rawPitch = pitchTo(player, aim);
                responsiveness = AIM_SMOOTHING;
                if (aiming) {
                    maxYaw = MAX_YAW_SPEED_AIM;
                    maxPitch = MAX_PITCH_SPEED_AIM;
                }
            }
        } else if (moveDir != null) {
            rawYaw = headingYawOf(moveDir);
            rawPitch = 0.0f;
        } else if (currentTarget != null) {
            Vec3 toT = currentTarget.position().subtract(player.position());
            if (toT.horizontalDistanceSqr() > 1.0E-4) {
                rawYaw = headingYawOf(new Vec3(toT.x, 0, toT.z));
            }
            rawPitch = pitchTo(player, bodyCenter(currentTarget));
            responsiveness = AIM_SMOOTHING;
            maxYaw = MAX_YAW_SPEED_AIM;
            maxPitch = MAX_PITCH_SPEED_AIM;
        }

        if (rawYaw == null) rawYaw = camera.getYaw();
        if (rawPitch == null) rawPitch = 0.0f;

        if (!smoothGoalsInitialized) {
            smoothYawGoal = rawYaw;
            smoothPitchGoal = rawPitch;
            smoothGoalsInitialized = true;
        } else {
            smoothYawGoal = Mth.wrapDegrees(
                    smoothYawGoal + Mth.degreesDifference(smoothYawGoal, rawYaw) * GOAL_TRACK_SMOOTHING);
            smoothPitchGoal = Mth.clamp(
                    smoothPitchGoal + (rawPitch - smoothPitchGoal) * GOAL_TRACK_SMOOTHING,
                    -89.9f, 89.9f);
        }

        camera.steer(smoothYawGoal, smoothPitchGoal, responsiveness, maxYaw, maxPitch);

        float tremorIntensity = (creeperThreat != null || currentTarget != null) ? 0.3f : 0.6f;
        camera.applyMicroTremor(tremorIntensity);

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

        if (target instanceof Creeper creeper && creeper == creeperThreat) {
            if (pokeTarget != null) {
                strikePokeTarget(client, player);
                return;
            }
            creeperCombat(client, player, creeper, Math.sqrt(distSqr));
            return;
        }

        if (usingBow) cancelDraw(client, player);
        ItemStack handStack = player.getMainHandItem();
        boolean holdingBow = handStack.getItem() instanceof BowItem;
        boolean hasMeleeWeapon = !holdingBow && handStack.get(DataComponents.WEAPON) != null;
        if (!hasMeleeWeapon) {
            if (weaponSwapGrace > 0) return;
            if (selectWeaponItem(player)) weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
            return;
        }
        if (weaponSwapGrace > 0) return;
        if (distSqr <= ATTACK_RANGE * ATTACK_RANGE && cooldown <= 0) {
            boolean shouldAttack = true;
            if (HumanMovement.shouldVaryAttackTiming()) {
                shouldAttack = cooldown <= -HumanMovement.getAttackTimingVariation();
            }

            if (shouldAttack) {
                if (client.gameMode != null) {
                    client.gameMode.attack(player, target);
                }
                player.swing(InteractionHand.MAIN_HAND, true);
                cooldown = ATTACK_COOLDOWN;
            }
        }
    }

    private void strikePokeTarget(Minecraft client, LocalPlayer player) {
        double distSqr = player.distanceToSqr(pokeTarget);
        if (distSqr > (ATTACK_RANGE + 0.2) * (ATTACK_RANGE + 0.2)) {
            pokeTarget = null;
            pokeTicks = 0;
            return;
        }
        Vec3 to = pokeTarget.position().subtract(player.position());
        float targetYaw = headingYawOf(new Vec3(to.x, 0, to.z));
        float angle = Math.abs(Mth.degreesDifference(camera.getYaw(), targetYaw));
        if (angle <= POKE_CONE_DEG || pokeTicks >= POKE_AIM_MAX_TICKS) {
            if (client.gameMode != null) {
                client.gameMode.attack(player, pokeTarget);
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            cooldown = ATTACK_COOLDOWN;
            pokeTarget = null;
            pokeTicks = 0;
        }
    }

    private void creeperCombat(Minecraft client, LocalPlayer player, Creeper creeper, double dist) {
        if (weaponSwapGrace > 0) return;

        if ((shootStance || usingBow) && hasBowAndArrows(player)) {
            ItemStack hand = player.getMainHandItem();
            if (!(hand.getItem() instanceof BowItem)) {
                if (selectHotbarItem(player, BowItem.class)) {
                    weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
                }
                return;
            }
            if (!player.isUsingItem()) {
                if (usingBow && bowDrawVerify > 0) {
                    usingBow = false;
                    bowTicks = 0;
                    bowDrawVerify = 0;
                    weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
                    return;
                }
                if (client.gameMode != null) {
                    client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                } else {
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                }
                usingBow = true;
                bowTicks = 0;
                bowDrawVerify = 2;
                client.options.keyUse.setDown(true);
            } else {
                bowDrawVerify = 0;
                bowTicks++;
                if (dist < DRAW_ABORT_DIST) {
                    if (bowTicks >= MIN_CHARGE_TO_RELEASE_EARLY) {
                        fireBow(client, player);
                        cooldown = ATTACK_COOLDOWN;
                    } else {
                        cancelDraw(client, player);
                    }
                    return;
                }
                client.options.keyUse.setDown(true);
                if (bowTicks >= BOW_HOLD_TICKS) {
                    fireBow(client, player);
                    cooldown = ATTACK_COOLDOWN;
                }
            }
            return;
        }

        if (!hasBowAndArrows(player) && cooldown <= 0 && dist <= POKE_RANGE) {
            ItemStack hand = player.getMainHandItem();
            if (hand.getItem() instanceof BowItem || hand.get(DataComponents.WEAPON) == null) {
                if (selectWeaponItem(player)) weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
                return;
            }
            if (client.gameMode != null) {
                client.gameMode.attack(player, creeper);
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            cooldown = ATTACK_COOLDOWN;
            forceEscapeTicks = FORCE_ESCAPE_TICKS;
        }
    }

    private void fireBow(Minecraft client, LocalPlayer player) {
        if (client.gameMode != null) client.gameMode.releaseUsingItem(player);
        else player.releaseUsingItem();
        client.options.keyUse.setDown(false);
        usingBow = false;
        bowTicks = 0;
        bowDrawVerify = 0;
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

    private void cancelDraw(Minecraft client, LocalPlayer player) {
        client.options.keyUse.setDown(false);
        usingBow = false;
        bowTicks = 0;
        bowDrawVerify = 0;
        if (player.isUsingItem()) {
            player.stopUsingItem();
            int selectedSlot = player.getInventory().getSelectedSlot();
            if (!selectWeaponItem(player)) {
                int size = Math.min(9, player.getInventory().getContainerSize());
                if (size > 1) {
                    player.getInventory().setSelectedSlot((selectedSlot + 1) % size);
                }
            }
            weaponSwapGrace = WEAPON_SWAP_GRACE_TICKS;
        }
    }

    private boolean isTargetValid(LocalPlayer player, LivingEntity target) {
        return target.isAlive()
                && player.level().getEntity(target.getId()) != null
                && Targeting.withinTargetRange(player, target);
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
        AABB box = Targeting.targetSearchBox(player);

        List<LivingEntity> list = player.level().getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isAquatic(e)
                && !targetBlacklist.containsKey(e.getId()));

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
            camera.steer(headingYawOf(dir), camera.getPitch(),
                    HEADING_SMOOTHING, MAX_YAW_SPEED_HEADING, MAX_PITCH_SPEED_HEADING);
            player.setYRot(camera.getYaw());
        } else {
            client.options.keyUp.setDown(false);
        }
        smoothGoalsInitialized = false;
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
        bowTicks = 0;
        bowDrawVerify = 0;
        shootStance = false;
        urgentEscape = false;
        weaponSwapGrace = 0;
        forceEscapeTicks = 0;
        recoveryTicks = 0;
        recoveryDir = null;
        pokeTarget = null;
        pokeTicks = 0;
        smoothGoalsInitialized = false;
        creeperThreat = null;
        lockedAvoidDir = null;
        avoidLockTicksLeft = 0;
        explorationTicks = 0;
        explorationDir = new Vec3(1, 0, 0);
        eating = false;
        previousCombatSlot = -1;
        camera.invalidate();

        isHesitating = false;
        hesitationTicks = 0;
        isLookingAround = false;
        lookAroundTicks = 0;
        strafeActive = false;
        strafeBurstTicksLeft = 0;
        strafeGapTicksLeft = 0;
        strafeOffset = 0;
        collectTarget = null;
        aiIntent = null;
        aiHoldTicks = 0;
        ticksSinceAiDecision = 0;
        if (currentClient != null) currentClient.options.keyAttack.setDown(false);
    }

    private boolean aiWantsPeaceful() {
        if (aiIntent == null || agent == null || !agent.isEnabled()) return false;
        return switch (aiIntent.type()) {
            case EXPLORE, COLLECT, GO_TO, WAIT, LOOK_AROUND, CHAT, EAT -> true;
            case COMBAT, FLEE, IDLE -> false;
        };
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
