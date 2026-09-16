package com.automine.AutoMineMod.navigation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolSelector {
    private static final String[] TIER_PREFIXES = {
            "wooden_", "stone_", "copper_", "iron_", "golden_", "diamond_", "netherite_"
    };
    private static final java.util.function.Predicate<BlockBehaviour.BlockStateBase> ANY = state -> true;

    private ToolSelector() {
    }

    public static int tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (int i = TIER_PREFIXES.length - 1; i >= 0; i--) {
            if (path.startsWith(TIER_PREFIXES[i])) return i;
        }
        return -1;
    }

    public static String kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("_pickaxe")) return "pickaxe";
        if (path.contains("_axe")) return "axe";
        if (path.contains("_shovel")) return "shovel";
        if (path.contains("_hoe")) return "hoe";
        return null;
    }

    private static String requiredKind(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE, ANY)) return "pickaxe";
        if (state.is(BlockTags.MINEABLE_WITH_AXE, ANY)) return "axe";
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL, ANY)) return "shovel";
        if (state.is(BlockTags.MINEABLE_WITH_HOE, ANY)) return "hoe";
        return null;
    }

    /**
     * @return 1 se a ferramenta correta foi selecionada,
     *         0 se o bloco pode ser minerado á mão,
     *         -1 se a ferramenta necessária está em falta.
     */
    public static int selectFor(Inventory inventory, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return 0;
        String kind = requiredKind(state);
        if (kind == null) return 1;
        int bestSlot = -1;
        int bestTier = -1;
        int size = Math.min(inventory.getContainerSize(), 9);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !kind.equals(kindOf(stack))) continue;
            int tier = tierOf(stack);
            if (tier > bestTier) {
                bestTier = tier;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return -1;
        Tool tool = inventory.getItem(bestSlot).get(DataComponents.TOOL);
        if (tool == null || !tool.isCorrectForDrops(state)) return -1;
        inventory.setSelectedSlot(bestSlot);
        return 1;
    }

    public static boolean hasKind(Inventory inventory, String kind) {
        int size = Math.min(inventory.getContainerSize(), 9);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && kind.equals(kindOf(stack))) return true;
        }
        return false;
    }
}