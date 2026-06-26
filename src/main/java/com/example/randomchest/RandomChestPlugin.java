package com.example.randomchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class RandomChestPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private BukkitTask spawnTask = null;
    private final AtomicBoolean isSpawning = new AtomicBoolean(false);
    private final List<String> validMaterials = new ArrayList<>();

    @Override
    public void onEnable() {
        initializeValidMaterials();
        getServer().getPluginManager().registerEvents(this, this);
        startSpawnTask();
        getLogger().info("RandomChestPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        getLogger().info("RandomChestPlugin has been disabled!");
    }

    private void initializeValidMaterials() {
        for (Material material : Material.values()) {
            if (material.isItem() && !isNonObtainable(material)) {
                validMaterials.add(material.name());
            }
        }
    }

    private boolean isNonObtainable(Material material) {
        String name = material.name();
        return name.contains("COMMAND") ||
               name.contains("MISALIGNED") ||
               name.contains("PROGRESS") ||
               name.contains("STRUCTURE_VOID") ||
               name.contains("UNKNOWN") ||
               name.endsWith("_CURSOR") ||
               name.endsWith("_GHOST") ||
               name.startsWith("LEGACY_") ||
               name.contains("_DEBUG") ||
               material == Material.AIR ||
               material == Material.CAVE_AIR ||
               material == Material.VOID_AIR ||
               material == Material.LIGHT ||
               material == Material.STRUCTURE_VOID;
    }

    private void startSpawnTask() {
        spawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!onlinePlayers.isEmpty()) {
                spawnRandomChest(onlinePlayers);
            }
        }, 20L * 60 * 30, 20L * 60 * 30); // 30 minutes = 20 ticks * 60 seconds * 30 minutes
    }

    private void spawnRandomChest(List<Player> players) {
        if (!isSpawning.compareAndSet(false, true)) {
            return;
        }

        try {
            Player targetPlayer = players.get(random.nextInt(players.size()));
            World world = targetPlayer.getWorld();

            double minDistance = 100;
            double maxDistance = 1000;

            Location playerLocation = targetPlayer.getLocation();
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);

            int offsetX = (int) (distance * Math.cos(angle));
            int offsetZ = (int) (distance * Math.sin(angle));

            int baseX = playerLocation.getBlockX() + offsetX;
            int baseZ = playerLocation.getBlockZ();
            int baseY = playerLocation.getBlockY();

            Location chestLocation = findTopBlock(world, baseX, baseY, baseZ);

            if (chestLocation == null) {
                getLogger().warning("Could not find a valid location for chest spawn.");
                return;
            }

            Block chestBlock = chestLocation.getBlock();
            chestBlock.setType(Material.CHEST);

            if (chestBlock.getState() instanceof Chest chest) {
                fillChestWithRandomItems(chest);
                announceChestSpawn(chestLocation, players);
            }
        } finally {
            isSpawning.set(false);
        }
    }

    private Location findTopBlock(World world, int x, int z, int playerY) {
        int searchStart = Math.max(1, playerY - 50);
        int searchEnd = Math.min(world.getMaxHeight() - 1, playerY + 50);

        for (int y = searchEnd; y >= searchStart; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (isSolidBlock(block) && !isLiquidBlock(block)) {
                Block above = world.getBlockAt(x, y + 1, z);
                if (above.getType() == Material.AIR || above.getType() == Material.CAVE_AIR) {
                    return new Location(world, x, y + 1, z);
                }
            }
        }
        return new Location(world, x, searchEnd, z);
    }

    private boolean isSolidBlock(Block block) {
        Material type = block.getType();
        return type.isSolid() && type != Material.BEDROCK && type != Material.BARRIER;
    }

    private boolean isLiquidBlock(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.LAVA;
    }

    private void fillChestWithRandomItems(Chest chest) {
        int itemCount = 5 + random.nextInt(14); // 5 to 18 items
        List<ItemStack> items = new ArrayList<>();

        for (int i = 0; i < itemCount; i++) {
            String materialName = validMaterials.get(random.nextInt(validMaterials.size()));
            Material material = Material.valueOf(materialName);
            int amount = Math.min(material.getMaxStackSize(), 1 + random.nextInt(64));
            items.add(new ItemStack(material, amount));
        }

        ItemStack[] inventory = items.toArray(new ItemStack[0]);
        chest.getInventory().setContents(inventory);
    }

    private void announceChestSpawn(Location location, List<Player> players) {
        String coords = String.format("(%.0f, %d, %.0f)",
            location.getX(), location.getBlockY(), location.getZ());
        
        String message = "§6[Random Chest] §aA random chest has spawned at §e" + coords + " §ain the world §b" 
            + location.getWorld().getName() + "§a!";
        
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.size() == 1) {
            Player player = event.getPlayer();
            player.sendMessage("§6[Random Chest] §aWelcome! Random chests will spawn every 30 minutes!");
            spawnRandomChest(onlinePlayers);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (Bukkit.getOnlinePlayers().isEmpty() && spawnTask != null) {
            spawnTask.cancel();
        }
    }
}
