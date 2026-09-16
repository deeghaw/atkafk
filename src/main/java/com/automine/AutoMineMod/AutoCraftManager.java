package com.automine.AutoMineMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AutoCraftManager {
    private static final int INTERVAL = 60;
    private static final int TORCH_TARGET = 10;
    private static final int PLANKS_TARGET = 24;
    private static final int STICK_TARGET = 16;

    private static final Item[] LOGS = {
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
            Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
            Items.PALE_OAK_LOG
    };
    private static final Item[] PLANKS = {
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
            Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
            Items.PALE_OAK_PLANKS, Items.BAMBOO_PLANKS
    };
    private static final Item[][] TOOLS = {
            {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.COPPER_PICKAXE,
                    Items.GOLDEN_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE},
            {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD,
                    Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD},
            {Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE,
                    Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE},
            {Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.COPPER_SHOVEL,
                    Items.GOLDEN_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL}
    };

    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private MinecraftServer lastServer;

    public void tick(MinecraftServer server) {
        this.lastServer = server;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            UUID pid = player.getUUID();
            int cd = cooldowns.getOrDefault(pid, 0);
            if (cd > 0) {
                cooldowns.put(pid, cd - 1);
                continue;
            }
            cooldowns.put(pid, INTERVAL);
            tryCraft(player);
        }
    }

    private void tryCraft(ServerPlayer player) {
        int torches = count(player, Items.TORCH);
        int sticks = count(player, Items.STICK);
        int planks = count(player, PLANKS);
        int logs = count(player, LOGS);
        int fuel = count(player, Items.COAL, Items.CHARCOAL);
        int tables = count(player, Items.CRAFTING_TABLE);
        boolean underground = player.getY() < 58.0;
        int torchTarget = underground ? TORCH_TARGET + 6 : TORCH_TARGET;

        if (planks < PLANKS_TARGET && logs >= 1) {
            if (tryPlanks(player)) return;
        }
        if (sticks < STICK_TARGET && planks >= 2) {
            if (trySticks(player)) return;
        }
        if (torches < torchTarget && fuel >= 1 && sticks >= 1) {
            if (tryTorches(player)) return;
        }
        if (tables == 0 && planks >= 4 && (count(player, Items.COBBLESTONE) >= 3 || planks >= 16)) {
            if (tryCraftingTable(player)) return;
        }
        for (Item[] toolFamily : TOOLS) {
            if (!hasAny(player, toolFamily)) {
                if (tryCraftTool(player, toolFamily)) return;
            }
        }
    }

    private boolean tryPlanks(ServerPlayer player) {
        for (Item log : LOGS) {
            if (count(player, log) < 1) continue;
            if (craft2x2(player, new ItemStack(log), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)) {
                return true;
            }
        }
        return false;
    }

    private boolean trySticks(ServerPlayer player) {
        for (Item plank : PLANKS) {
            ItemStack p = new ItemStack(plank);
            if (craft2x2(player, p, ItemStack.EMPTY, p, ItemStack.EMPTY)) return true;
            if (craft2x2(player, ItemStack.EMPTY, p, ItemStack.EMPTY, p)) return true;
        }
        return false;
    }

    private boolean tryTorches(ServerPlayer player) {
        Item fuel = count(player, Items.COAL) > 0 ? Items.COAL
                : (count(player, Items.CHARCOAL) > 0 ? Items.CHARCOAL : null);
        if (fuel == null) return false;
        ItemStack fuelStack = new ItemStack(fuel);
        ItemStack stickStack = new ItemStack(Items.STICK);
        return craft2x2(player, fuelStack, ItemStack.EMPTY, stickStack, ItemStack.EMPTY)
                || craft2x2(player, ItemStack.EMPTY, fuelStack, ItemStack.EMPTY, stickStack);
    }

    private boolean tryCraftingTable(ServerPlayer player) {
        ItemStack plank = firstPlank(player);
        if (plank == null) return false;
        return craft2x2(player, plank, plank, plank, plank);
    }

    private boolean tryCraftTool(ServerPlayer player, Item[] family) {
        Item best = bestAvailableTool(player, family);
        if (best == null) return false;
        Item head = headFor(best);
        if (head == null) return false;
        ItemStack h = new ItemStack(head);
        ItemStack s = new ItemStack(Items.STICK);
        String path = itemPath(best);
        ItemStack[] grid;
        if (path.contains("sword")) {
            grid = new ItemStack[]{h, null, null, h, null, null, s, null, null};
        } else if (path.contains("shovel")) {
            grid = new ItemStack[]{h, null, null, s, null, null, s, null, null};
        } else if (path.contains("axe")) {
            grid = new ItemStack[]{h, h, null, h, s, null, null, s, null};
        } else {
            grid = new ItemStack[]{h, h, h, null, s, null, null, s, null};
        }
        return craft3x3(player, grid);
    }

    private Item bestAvailableTool(ServerPlayer player, Item[] family) {
        for (int tier = family.length - 1; tier >= 0; tier--) {
            Item tool = family[tier];
            Item head = headFor(tool);
            if (head == null) continue;
            int need = head.equals(Items.OAK_LOG) || head.equals(Items.SPRUCE_LOG)
                    || head.equals(Items.BIRCH_LOG) || head.equals(Items.JUNGLE_LOG)
                    || head.equals(Items.ACACIA_LOG) || head.equals(Items.DARK_OAK_LOG)
                    || head.equals(Items.MANGROVE_LOG) || head.equals(Items.CHERRY_LOG)
                    || head.equals(Items.PALE_OAK_LOG) || head.equals(Items.BAMBOO_PLANKS)
                    ? 4 : 3;
            if (woodHead(head)) {
                need = 3;
            }
            if (tier == 0) {
                if (count(player, head) >= 3) return tool;
            } else if (count(player, head) >= need && count(player, Items.STICK) >= 2) {
                return tool;
            }
        }
        return null;
    }

    private boolean woodHead(Item head) {
        for (Item plank : PLANKS) if (plank == head) return true;
        return false;
    }

    private Item headFor(Item tool) {
        String path = itemPath(tool);
        if (path.contains("wooden")) return Items.OAK_PLANKS;
        if (path.contains("stone")) return Items.COBBLESTONE;
        if (path.contains("copper")) return Items.COPPER_INGOT;
        if (path.contains("golden")) return Items.GOLD_INGOT;
        if (path.contains("iron")) return Items.IRON_INGOT;
        if (path.contains("diamond")) return Items.DIAMOND;
        if (path.contains("netherite")) return Items.NETHERITE_INGOT;
        return null;
    }

    private String itemPath(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private ItemStack firstPlank(ServerPlayer player) {
        for (Item plank : PLANKS) {
            int slot = findSlot(player, plank);
            if (slot >= 0) return player.getInventory().getItem(slot).copy();
        }
        return null;
    }

    private boolean craft2x2(ServerPlayer player, ItemStack a, ItemStack b, ItemStack c, ItemStack d) {
        List<ItemStack> items = new ArrayList<>();
        items.add(a == null ? ItemStack.EMPTY : a);
        items.add(b == null ? ItemStack.EMPTY : b);
        items.add(c == null ? ItemStack.EMPTY : c);
        items.add(d == null ? ItemStack.EMPTY : d);
        return craft(player, 2, 2, items);
    }

    private boolean craft3x3(ServerPlayer player, ItemStack[] grid) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : grid) items.add(s == null ? ItemStack.EMPTY : s);
        return craft(player, 3, 3, items);
    }

    private boolean craft(ServerPlayer player, int width, int height, List<ItemStack> input) {
        if (lastServer == null) return false;
        net.minecraft.world.level.Level level = player.level();
        CraftingInput craftingInput = CraftingInput.of(width, height, input);
        Optional<RecipeHolder<CraftingRecipe>> opt = lastServer.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
        if (opt.isEmpty()) return false;
        ItemStack result = opt.get().value().assemble(craftingInput);
        if (result.isEmpty()) return false;
        if (!consumeAll(player, input)) return false;
        give(player, result);
        return true;
    }

    private boolean consumeAll(ServerPlayer player, List<ItemStack> input) {
        List<Integer> slots = new ArrayList<>();
        for (ItemStack want : input) {
            if (want.isEmpty()) {
                slots.add(-1);
                continue;
            }
            int slot = findSlot(player, want.getItem());
            if (slot < 0) return false;
            slots.add(slot);
        }
        for (int slot : slots) {
            if (slot >= 0) player.getInventory().removeItem(slot, 1);
        }
        return true;
    }

    private int findSlot(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item) && s.getCount() > 0) return i;
        }
        return -1;
    }

    private int count(ServerPlayer player, Item... items) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            for (Item item : items) {
                if (s.is(item)) total += s.getCount();
            }
        }
        return total;
    }

    private boolean hasAny(ServerPlayer player, Item[] items) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            for (Item item : items) if (s.is(item)) return true;
        }
        return false;
    }

    private void give(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().add(stack)) return;
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
        player.level().addFreshEntity(item);
    }
}