package com.automine.AutoMineMod.client.ai;

import com.automine.AutoMineMod.navigation.Targeting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldStateCollector {
    private WorldStateCollector() {
    }

    public static String collect(LocalPlayer player, String lastActionResult, List<String> recentChats) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Estado\n");
        sb.append(String.format(java.util.Locale.ROOT,
                "- Posicao: (%.1f, %.1f, %.1f)\n", player.getX(), player.getY(), player.getZ()));
        sb.append(String.format(java.util.Locale.ROOT,
                "- Vida: %.1f/20  Fome: %d/20  No chao: %s\n",
                player.getHealth(), player.getFoodData().getFoodLevel(), player.onGround()));
        sb.append("- Dimensao: ").append(player.level().dimension().identifier()).append('\n');
        sb.append("- Periodo: ").append(timeOfDay(player.level())).append('\n');
        if (player.isInWater()) sb.append("- Status: na agua\n");
        if (player.isInLava()) sb.append("- Status: na lava\n");

        sb.append("\n## Inventario (hotbar)\n");
        boolean any = false;
        for (int slot = 0; slot < Math.min(9, player.getInventory().getContainerSize()); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            any = true;
            sb.append("- ").append(itemId(stack)).append(" x").append(stack.getCount()).append('\n');
        }
        if (!any) sb.append("(vazia)\n");

        sb.append("\n## Entidades proximas\n");
        AABB box = player.getBoundingBox().inflate(24.0, 6.0, 24.0);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());
        entities.sort(Comparator.comparingDouble(player::distanceToSqr));
        int shown = 0;
        for (LivingEntity entity : entities) {
            if (shown++ >= 10) break;
            String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            String hostile = entity instanceof Enemy ? " hostil" : "";
            sb.append(String.format(java.util.Locale.ROOT, "- %s dist=%.1f%s\n",
                    name, player.distanceTo(entity), hostile));
        }
        if (shown == 0) sb.append("(nenhuma)\n");

        sb.append("\n## Blocos de interesse (raio 8)\n");
        Map<String, Integer> counts = new HashMap<>();
        Map<String, BlockPos> nearest = new HashMap<>();
        Map<String, Double> nearestDist = new HashMap<>();
        BlockPos origin = player.blockPosition();
        Level level = player.level();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (!isInteresting(state)) continue;
                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    counts.merge(name, 1, Integer::sum);
                    double dist = player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    Double prev = nearestDist.get(name);
                    if (prev == null || dist < prev) {
                        nearestDist.put(name, dist);
                        nearest.put(name, pos);
                    }
                }
            }
        }
        if (counts.isEmpty()) {
            sb.append("(nada especial)\n");
        } else {
            counts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(12)
                    .forEach(entry -> {
                        BlockPos pos = nearest.get(entry.getKey());
                        sb.append(String.format(java.util.Locale.ROOT,
                                "- %s x%d mais perto=(%d,%d,%d)\n",
                                entry.getKey(), entry.getValue(), pos.getX(), pos.getY(), pos.getZ()));
                    });
        }

        boolean threat = Targeting.findNearestCreeper(player, Targeting.CREEPER_FLEE_RADIUS) != null;
        sb.append("\n## Ameacas\n");
        sb.append("- Creeper proximo: ").append(threat).append('\n');

        if (lastActionResult != null && !lastActionResult.isBlank()) {
            sb.append("\n## Ultimo resultado\n").append(lastActionResult).append('\n');
        }
        if (recentChats != null && !recentChats.isEmpty()) {
            sb.append("\n## Chat recente\n");
            for (String chat : recentChats) sb.append("- ").append(chat).append('\n');
        }
        return sb.toString();
    }

    private static boolean isInteresting(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.BEDS)
                || path.contains("chest")
                || path.contains("crafting")
                || path.contains("furnace")
                || path.contains("ore");
    }

    private static String timeOfDay(Level level) {
        long t = level.getOverworldClockTime() % 24000L;
        if (t < 12542) return "dia";
        if (t < 23460) return "noite";
        return "amanhecer";
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
