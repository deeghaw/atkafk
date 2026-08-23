package com.automine.AutoMineMod;

import com.automine.AutoMineMod.navigation.Targeting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AutoPlayManager {
    private static final double SEARCH_RADIUS = 32.0;
    private static final int SPAWN_INTERVAL = 100;
    private static final double SPAWN_DISTANCE = 12.0;
    private static final double RARE_LOOT_CHANCE = 0.08;

    private final Map<UUID, LivingEntity> currentTargets = new HashMap<>();
    private final Map<UUID, Integer> spawnCooldowns = new HashMap<>();

    public void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
                processPlayer(player);
            }
        }
    }

    private void processPlayer(ServerPlayer player) {
        UUID pid = player.getUUID();
        int cooldown = spawnCooldowns.getOrDefault(pid, 0);
        if (cooldown > 0) spawnCooldowns.put(pid, cooldown - 1);

        LivingEntity currentTarget = currentTargets.get(pid);
        if (currentTarget != null && !currentTarget.isAlive()) {
            grantLoot(player, currentTarget);
            currentTargets.remove(pid);
            currentTarget = null;
        } else if (currentTarget != null && !isTargetValid(player, currentTarget)) {
            currentTargets.remove(pid);
            currentTarget = null;
        }

        if (currentTarget == null) {
            currentTarget = findBestTarget(player);
            if (currentTarget != null) {
                currentTargets.put(pid, currentTarget);
            }
        }

        if (currentTarget == null && !hasActiveEnemy(player)
                && spawnCooldowns.getOrDefault(pid, 0) <= 0) {
            spawnNearbyMonster(player);
            spawnCooldowns.put(pid, SPAWN_INTERVAL);
        }

        try {
            int tid = currentTarget != null ? currentTarget.getId() : -1;
            player.setScore(tid);
        } catch (Throwable t) {
            AutoMineMod.LOGGER.debug("Failed to sync target id for {}: {}", player.getName().getString(), t.getMessage());
        }
    }

    private boolean isTargetValid(ServerPlayer player, LivingEntity target) {
        return target.isAlive()
                && player.level().getEntity(target.getId()) != null
                && player.distanceToSqr(target) <= SEARCH_RADIUS * SEARCH_RADIUS;
    }

    private LivingEntity findBestTarget(ServerPlayer player) {
        Vec3 pos = player.position();
        AABB box = new AABB(
                pos.x - SEARCH_RADIUS, pos.y - SEARCH_RADIUS, pos.z - SEARCH_RADIUS,
                pos.x + SEARCH_RADIUS, pos.y + SEARCH_RADIUS, pos.z + SEARCH_RADIUS
        );

        List<LivingEntity> list = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isAquatic(e));

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = player.distanceToSqr(e);
            double score = d;
            if (!player.hasLineOfSight(e)) score += 4.0;
            if (Targeting.isCreeper(e)) score += Targeting.CREEPER_TARGET_PENALTY;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean hasActiveEnemy(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        return !player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy && !Targeting.isAquatic(e)).isEmpty();
    }

    private void spawnNearbyMonster(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
            || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;

        double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * SPAWN_DISTANCE);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * SPAWN_DISTANCE);
        for (int y = player.blockPosition().getY() + 2; y >= player.blockPosition().getY() - 3; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!isSafeSpawn(level, feet)) continue;

                    if (!(BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "zombie"))
                        .create(level, EntitySpawnReason.COMMAND) instanceof LivingEntity monster)) return;
            monster.setPos(x + 0.5, y, z + 0.5);
            if (level.noCollision(monster)) {
                level.addFreshEntity(monster);
            }
            return;
        }
    }

    private boolean isSafeSpawn(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isSolid()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir();
    }

    private void grantLoot(ServerPlayer player, LivingEntity target) {
        if (!(target instanceof Enemy)) return;

        if (target instanceof Zombie) {
            giveLoot(player, new ItemStack(Items.ROTTEN_FLESH, 1 + player.getRandom().nextInt(3)));
        } else if (target instanceof Skeleton) {
            giveLoot(player, new ItemStack(Items.BONE, 1 + player.getRandom().nextInt(2)));
            giveLoot(player, new ItemStack(Items.ARROW, 1 + player.getRandom().nextInt(3)));
        } else if (target instanceof Spider) {
            giveLoot(player, new ItemStack(Items.STRING, 1 + player.getRandom().nextInt(2)));
        } else if (target instanceof Creeper) {
            giveLoot(player, new ItemStack(Items.GUNPOWDER, 1 + player.getRandom().nextInt(2)));
        }

        if (player.getRandom().nextDouble() < RARE_LOOT_CHANCE) {
            net.minecraft.world.item.Item rareItem = player.getRandom().nextBoolean() ? Items.IRON_INGOT : Items.GOLD_INGOT;
            if (player.getRandom().nextDouble() < 0.12) rareItem = Items.DIAMOND;
            giveLoot(player, new ItemStack(rareItem));
        }
    }

    private void giveLoot(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack)) return;
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
        player.level().addFreshEntity(item);
    }

    public void reset() {
        currentTargets.clear();
        spawnCooldowns.clear();
    }
}
