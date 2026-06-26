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
            World world = players.get(0).getWorld();

            // Calculate the center point of all connected players
            Location center = calculatePlayersCenter(players);
            
            // Calculate the maximum distance from center to any player
            double maxPlayerDistance = calculateMaxPlayerDistance(center, players);
            
            // Ensure minimum distance of 100 blocks, max of 1000 (or player spread if larger)
            double spawnDistance = Math.max(100, Math.min(1000, maxPlayerDistance));

            // Add some randomness to the angle
            double angle = random.nextDouble() * 2 * Math.PI;
            double offsetX = spawnDistance * Math.cos(angle);
            double offsetZ = spawnDistance * Math.sin(angle);

            // Calculate target coordinates and clamp to world bounds
            double targetX = center.getX() + offsetX;
            double targetZ = center.getZ() + offsetZ;
            
            // Clamp to world boundaries (-10000 to 10000)
            targetX = Math.max(-10000, Math.min(10000, targetX));
            targetZ = Math.max(-10000, Math.min(10000, targetZ));

            int finalX = (int) Math.floor(targetX);
            int finalZ = (int) Math.floor(targetZ);

            // Find the highest solid block at this X,Z coordinate and place chest on top
            Location chestLocation = findHighestBlockOnGround(world, finalX, finalZ);

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

    private Location calculatePlayersCenter(List<Player> players) {
        double sumX = 0, sumZ = 0;
        for (Player player : players) {
            sumX += player.getLocation().getX();
            sumZ += player.getLocation().getZ();
        }
        double centerX = sumX / players.size();
        double centerZ = sumZ / players.size();
        
        Location firstPlayerLoc = players.get(0).getLocation();
        return new Location(firstPlayerLoc.getWorld(), centerX, firstPlayerLoc.getY(), centerZ);
    }

    private double calculateMaxPlayerDistance(Location center, List<Player> players) {
        double maxDistance = 0;
        for (Player player : players) {
            double dx = player.getLocation().getX() - center.getX();
            double dz = player.getLocation().getZ() - center.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            maxDistance = Math.max(maxDistance, distance);
        }
        return maxDistance;
    }

    private Location findHighestBlockOnGround(World world, int x, int z) {
        // Search from world height down to find the highest solid block
        int maxHeight = world.getMaxHeight() - 1;
        
        for (int y = maxHeight; y > 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (isSolidGroundBlock(block)) {
                // Return the location on top of this block (chest sits on it, not inside)
                return new Location(world, x, y + 1, z);
            }
        }
        return null;
    }

    private boolean isSolidGroundBlock(Block block) {
        Material type = block.getType();
        // Only allow blocks that can have things placed on top and are natural ground blocks
        return type.isSolid() && 
               type != Material.BEDROCK && 
               type != Material.BARRIER &&
               type != Material.LAVA &&
               type != Material.WATER &&
               type != Material.GLASS &&
               type != Material.GLASS_PANE &&
               !type.name().contains("FENCE") &&
               !type.name().contains("WALL");
    }

    private void fillChestWithRandomItems(Chest chest) {
        int itemCount = 2 + random.nextInt(6); // 2 to 7 items
        List<Integer> usedSlots = new ArrayList<>();
        
        for (int i = 0; i < itemCount; i++) {
            String materialName = validMaterials.get(random.nextInt(validMaterials.size()));
            Material material = Material.valueOf(materialName);
            
            // Find a random empty slot (0-26 for chest inventory)
            int slot;
            do {
                slot = random.nextInt(27); // Single chest has 27 slots
            } while (usedSlots.contains(slot));
            usedSlots.add(slot);
            
            // Place only 1 item in the stack
            chest.getInventory().setItem(slot, new ItemStack(material, 1));
        }
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
