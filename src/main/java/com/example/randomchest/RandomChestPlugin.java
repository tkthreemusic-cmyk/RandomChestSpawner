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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class RandomChestPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private BukkitTask spawnTask = null;
    private BukkitTask despawnTask = null;
    private final AtomicBoolean isSpawning = new AtomicBoolean(false);
    private final List<String> validMaterials = new ArrayList<>();
    
    // Track spawned chests and if they've been looted
    private final Map<Location, Boolean> spawnedChests = new HashMap<>();
    
    // Track if first chest has been spawned (resets when no players)
    private boolean firstChestSpawned = false;

    @Override
    public void onEnable() {
        initializeValidMaterials();
        getServer().getPluginManager().registerEvents(this, this);
        // Don't start spawn task here - it starts when a player joins
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
        // Cancel existing task if any
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!onlinePlayers.isEmpty()) {
            if (!firstChestSpawned) {
                // First chest after 30 minutes
                long firstDelay = 20L * 60 * 30; // 30 minutes in ticks
                spawnTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                    if (!players.isEmpty()) {
                        firstChestSpawned = true;
                        spawnRandomChest(players);
                    }
                }, firstDelay);
                
                // Start periodic 30-minute spawning after first chest
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (firstChestSpawned) {
                        schedulePeriodicSpawning();
                    }
                }, firstDelay);
            } else {
                // Already spawned first chest, start periodic spawning
                schedulePeriodicSpawning();
            }
        }
    }
    
    private void schedulePeriodicSpawning() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        
        // Schedule recurring 30-minute spawns
        spawnTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!players.isEmpty()) {
                spawnRandomChest(players);
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
            
            // Ensure minimum distance of 200 blocks, max of 2000 (or player spread if larger)
            double spawnDistance = Math.max(200, Math.min(2000, maxPlayerDistance));

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
                
                // Track this chest as not yet looted
                spawnedChests.put(chestLocation, false);
                
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
        
        String message = "§6[Coffre Aleatoire] §aUn coffre aleatoire est apparait en §e" + coords + " §a dans le monde §b" 
            + location.getWorld().getName() + "§a!";
        
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.size() == 1) {
            // Start the spawn timer (first spawn after 15 min, then every 30 min)
            startSpawnTask();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            if (spawnTask != null) {
                spawnTask.cancel();
            }
            // Reset first spawn flag so next player gets 15-min delay
            firstChestSpawned = false;
        }
    }
    
    @EventHandler
    public void onChestClose(InventoryCloseEvent event) {
        // Check if this is a chest inventory
        if (event.getInventory().getHolder() instanceof Chest chest) {
            Location chestLocation = chest.getLocation();
            
            // Check if this is one of our spawned chests and not already marked as looted
            if (spawnedChests.containsKey(chestLocation) && !spawnedChests.get(chestLocation)) {
                // Check if the chest is empty
                boolean isEmpty = true;
                for (ItemStack item : event.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        isEmpty = false;
                        break;
                    }
                }
                
                if (isEmpty) {
                    // Mark as looted
                    spawnedChests.put(chestLocation, true);
                    
                    // Remove the chest after a short delay (1 second)
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        Block block = chestLocation.getBlock();
                        if (block.getType() == Material.CHEST) {
                            block.setType(Material.AIR);
                            spawnedChests.remove(chestLocation);
                            
                            // Notify nearby players
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.getWorld().equals(chestLocation.getWorld())) {
                                    double distance = player.getLocation().distance(chestLocation);
                                    if (distance < 100) {
                                        player.sendMessage("§6[Coffre Aleatoire] §7Un coffre a ete vide et a disparu !");
                                    }
                                }
                            }
                        }
                    }, 20L); // 20 ticks = 1 second
                }
            }
        }
    }
}
