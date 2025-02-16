package org.notionsmp.backroomsPlugin;

import com.nexomc.nexo.api.NexoBlocks;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class BackroomsPlugin extends JavaPlugin implements CommandExecutor {

    private boolean generating = false;
    private final Set<Location> generatedChunks = new HashSet<>();
    private final Random random = new Random();
    private static final int DEFAULT_BACKROOMS_Y_LEVEL = 232;
    private static final int POOLROOMS_OFFSET = 64;

    @Override
    public void onEnable() {
        Objects.requireNonNull(this.getCommand("backrooms")).setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("start")) {
                Player targetPlayer;
                int backroomsYLevel = DEFAULT_BACKROOMS_Y_LEVEL;

                if (args.length > 1) {
                    targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage("Player not found!");
                        return true;
                    }

                    if (args.length > 2) {
                        try {
                            backroomsYLevel = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("Invalid Y level! Using default value.");
                        }
                    }
                } else {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Only players can use this command without specifying a player!");
                        return true;
                    }
                    targetPlayer = player;
                }

                if (!generating) {
                    generating = true;
                    startGenerating(targetPlayer, backroomsYLevel);
                    sender.sendMessage("Backrooms generation started!");
                } else {
                    sender.sendMessage("Generation is already running!");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("stop")) {
                generating = false;
                sender.sendMessage("Generation stopped!");
                return true;
            }
        }
        sender.sendMessage("Usage: /backrooms <start|stop> [player] [backroomsYLevel]");
        return true;
    }

    private void startGenerating(Player player, int backroomsYLevel) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!generating) {
                    this.cancel();
                    return;
                }

                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    checkAndTeleportIfSuffocating(player, backroomsYLevel);

                Location location = player.getLocation().clone();
                int radius = 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int chunkX = (location.getBlockX() >> 4) + dx;
                        int chunkZ = (location.getBlockZ() >> 4) + dz;
                        Location chunkLoc = new Location(location.getWorld(), chunkX, 0, chunkZ);

                        if (!generatedChunks.contains(chunkLoc)) {
                            generatedChunks.add(chunkLoc);
                            generateBackrooms(location.getWorld(), chunkX * 16, chunkZ * 16, backroomsYLevel);
                            //(location.getWorld(), chunkX * 16, chunkZ * 16, backroomsYLevel);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void checkAndTeleportIfSuffocating(Player player, int backroomsYLevel) {
        if (!generating) return;

        Block eyeBlock = player.getEyeLocation().getBlock();

        if (!isBlockNonSolid(eyeBlock.getType())) {
            this.getLogger().info("Player is suffocating, searching for a safe location...");

            Location safeLocation = findSafeBackroomsLocation(player.getLocation(), backroomsYLevel);
            if (safeLocation != null) {
                player.teleport(safeLocation);
                player.setGameMode(GameMode.ADVENTURE);
                player.playSound(player.getLocation(), "notion:music.level_0.enter", SoundCategory.MASTER, 1000000, 1);
                player.getInventory().clear();
                this.getLogger().info("Player teleported to a safe location.");
            } else {
                this.getLogger().warning("No safe location found for teleportation!");
            }
        }
    }

    private Location findSafeBackroomsLocation(Location location, int backroomsYLevel) {
        Location backroomsLocation = new Location(location.getWorld(), location.getX(), backroomsYLevel + 1, location.getZ());

        if (isLocationSafe(backroomsLocation)) {
            return backroomsLocation;
        }

        int radius = 1;
        while (radius <= 16) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        Location checkLocation = backroomsLocation.clone().add(dx, 0, dz);
                        if (isLocationSafe(checkLocation)) {
                            return checkLocation;
                        }
                    }
                }
            }
            radius++;
        }
        return null;
    }

    private boolean isLocationSafe(Location location) {
        Block feetBlock = location.getBlock();
        Block headBlock = feetBlock.getRelative(0, 1, 0);
        Block groundBlock = feetBlock.getRelative(0, -1, 0);

        return isBlockNonSolid(feetBlock.getType()) &&
                isBlockNonSolid(headBlock.getType()) &&
                groundBlock.getType().isSolid();
    }

    private boolean isBlockNonSolid(Material material) {
        return material == Material.AIR || material == Material.WATER || material == Material.LAVA;
    }

    private void generateBackrooms(World world, int startX, int startZ, int backroomsYLevel) {
        int wallHeight = 4;
        int roomSize = 6;

        if (random.nextDouble() < 0.025) {
            generateOfficeSpace(world, startX, startZ, backroomsYLevel, wallHeight);
            return;
        }

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                NexoBlocks.place("level_0_carpet", new Location(world, x, backroomsYLevel, z));
                NexoBlocks.place("level_0_ceiling", new Location(world, x, backroomsYLevel + wallHeight, z));

                for (int y = backroomsYLevel + 1; y < backroomsYLevel + wallHeight; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        for (int x = startX; x < startX + 16; x += roomSize) {
            for (int z = startZ; z < startZ + 16; z += roomSize) {
                if (random.nextBoolean()) {
                    for (int i = 0; i < roomSize; i++) {
                        for (int y = backroomsYLevel + 1; y < backroomsYLevel + wallHeight; y++) {
                            Location loc = new Location(world, x + i, y, z);
                            if (y == backroomsYLevel + 1) {
                                NexoBlocks.place("level_0_wall_trim", loc);
                            } else {
                                NexoBlocks.place("level_0_wall", loc);
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < roomSize; i++) {
                        for (int y = backroomsYLevel + 1; y < backroomsYLevel + wallHeight; y++) {
                            Location loc = new Location(world, x, y, z + i);
                            if (y == backroomsYLevel + 1) {
                                NexoBlocks.place("level_0_wall_trim", loc);
                            } else {
                                NexoBlocks.place("level_0_wall", loc);
                            }
                        }
                    }
                }

                if (random.nextDouble() < 0.1) {
                    addDecoration(world, x, z, backroomsYLevel);
                }
            }
        }

        for (int x = startX; x < startX + 16; x += 4) {
            for (int z = startZ; z < startZ + 16; z += 4) {
                Location loc = new Location(world, x, backroomsYLevel + wallHeight, z);
                NexoBlocks.place("level_0_ceiling", loc);
                world.getBlockAt(loc).setType(Material.SEA_LANTERN);
            }
        }
    }

    private void generatePoolrooms(World world, int startX, int startZ, int backroomsYLevel) {
        int poolroomsYLevel = backroomsYLevel - POOLROOMS_OFFSET;
        if (poolroomsYLevel < 0) {
            poolroomsYLevel = backroomsYLevel + POOLROOMS_OFFSET;
        }

        int poolSize = 32;
        int wallHeight = 10;

        for (int x = startX; x < startX + poolSize; x++) {
            for (int z = startZ; z < startZ + poolSize; z++) {
                NexoBlocks.place("level_0_carpet", new Location(world, x, poolroomsYLevel, z));
                NexoBlocks.place("level_0_ceiling", new Location(world, x, poolroomsYLevel + wallHeight, z));

                for (int y = poolroomsYLevel + 1; y < poolroomsYLevel + wallHeight; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }

                if (random.nextDouble() < 0.5) {
                    world.getBlockAt(x, poolroomsYLevel + 1, z).setType(Material.WATER);
                }
            }
        }

        for (int x = startX; x < startX + poolSize; x += 8) {
            for (int z = startZ; z < startZ + poolSize; z += 8) {
                if (random.nextBoolean()) {
                    for (int i = 0; i < 4; i++) {
                        for (int y = poolroomsYLevel + 1; y < poolroomsYLevel + wallHeight; y++) {
                            Location loc = new Location(world, x + i, y, z);
                            NexoBlocks.place("level_0_wall", loc);
                        }
                    }
                } else {
                    for (int i = 0; i < 4; i++) {
                        for (int y = poolroomsYLevel + 1; y < poolroomsYLevel + wallHeight; y++) {
                            Location loc = new Location(world, x, y, z + i);
                            NexoBlocks.place("level_0_wall", loc);
                        }
                    }
                }
            }
        }

        for (int x = startX; x < startX + poolSize; x += 4) {
            for (int z = startZ; z < startZ + poolSize; z += 4) {
                Location loc = new Location(world, x, poolroomsYLevel + wallHeight, z);
                NexoBlocks.place("level_0_ceiling", loc);
                world.getBlockAt(loc).setType(Material.SEA_LANTERN);
            }
        }
    }

    private void generateOfficeSpace(World world, int startX, int startZ, int height, int wallHeight) {
        int officeSize = 32;

        for (int x = startX; x < startX + officeSize; x++) {
            for (int z = startZ; z < startZ + officeSize; z++) {
                NexoBlocks.place("level_0_carpet", new Location(world, x, height, z));
                NexoBlocks.place("level_0_ceiling", new Location(world, x, height + wallHeight, z));

                for (int y = height + 1; y < height + wallHeight; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        for (int x = startX; x < startX + officeSize; x += 8) {
            for (int z = startZ; z < startZ + officeSize; z += 8) {
                for (int i = 0; i < 4; i++) {
                    for (int y = height + 1; y < height + wallHeight; y++) {
                        Location loc = new Location(world, x + i, y, z);
                        NexoBlocks.place("level_0_wall", loc);
                    }
                }

                for (int i = 0; i < 4; i++) {
                    for (int y = height + 1; y < height + wallHeight; y++) {
                        Location loc = new Location(world, x, y, z + i);
                        NexoBlocks.place("level_0_wall", loc);
                    }
                }
            }
        }

        for (int x = startX + 2; x < startX + officeSize; x += 8) {
            for (int z = startZ + 2; z < startZ + officeSize; z += 8) {
                BlockData upperSlabData = Bukkit.createBlockData(Material.OAK_SLAB);
                ((Slab) upperSlabData).setType(Slab.Type.TOP);
                BlockData beeHiveDrawerData = Bukkit.createBlockData(Material.BEEHIVE);
                ((Directional) beeHiveDrawerData).setFacing(BlockFace.NORTH);

                world.getBlockAt(x, height + 1, z + 1).setBlockData(upperSlabData);
                world.getBlockAt(x + 1, height + 1, z + 1).setBlockData(beeHiveDrawerData);
                world.getBlockAt(x - 1, height + 1, z + 1).setBlockData(upperSlabData);

                world.getBlockAt(x, height + 1, z - 1).setType(Material.CHERRY_STAIRS);
            }
        }

        for (int x = startX; x < startX + officeSize; x += 4) {
            for (int z = startZ; z < startZ + officeSize; z += 4) {
                Location loc = new Location(world, x, height + wallHeight, z);
                NexoBlocks.place("level_0_ceiling", loc);
                world.getBlockAt(loc).setType(Material.SEA_LANTERN);
            }
        }
    }

    private void addDecoration(World world, int roomX, int roomZ, int height) {
        if (random.nextDouble() > 0.1) return;

        int decorationCount = random.nextInt(3) + 1;

        for (int i = 0; i < decorationCount; i++) {
            int offsetX = random.nextInt(6);
            int offsetZ = random.nextInt(6);
            Location decorationLoc = new Location(world, roomX + offsetX, height + 1, roomZ + offsetZ);

            if (world.getBlockAt(decorationLoc).getType() == Material.AIR) {
                int decorationType = random.nextInt(3);

                switch (decorationType) {
                    case 0:
                        world.getBlockAt(decorationLoc).setType(Material.FLOWER_POT);
                        break;
                    case 1:
                        world.getBlockAt(decorationLoc).setType(random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM);
                        break;
                    case 2:
                        world.getBlockAt(decorationLoc).setType(Material.REDSTONE_WIRE);
                        break;
                }
            }
        }
    }
}