package drizmans.distantTicking.command;

import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.config.PluginConfig;
import drizmans.distantTicking.util.ChunkCoord;
import drizmans.distantTicking.util.BlockCoord; // Import the new BlockCoord
import drizmans.distantTicking.util.TickWorthyBlocks; // Needed for scanning blocks
import org.bukkit.*;
import org.bukkit.block.Block; // For block iteration
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable; // For async tasks
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap; // For thread-safe tracking during scan
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger; // For atomic counters in async task


public class DistantTickingCommand implements CommandExecutor {

    private final DistantTicking plugin;
    private final ChunkDataManager chunkDataManager;
    private final ForceLoadManager forceLoadManager;
    private final PluginConfig pluginConfig;

    // Constructor to inject dependencies
    public DistantTickingCommand(DistantTicking plugin, ChunkDataManager chunkDataManager, ForceLoadManager forceLoadManager, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.chunkDataManager = chunkDataManager;
        this.forceLoadManager = forceLoadManager;
        this.pluginConfig = pluginConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Base command check (e.g., just /dt)
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "pdcinfo":
                return handlePdcInfoCommand(sender);
            case "status":
                return handleStatusCommand(sender);
            case "list":
                // Handle pagination if desired for very long lists
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Usage: /dt list [page_number]");
                        return true;
                    }
                }
                return handleListChunksCommand(sender, page);
            case "removehere":
                return handleRemoveHereCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "refresh": // New refresh command
                return handleRefreshCommand(sender, args);
            case "cleanup": // New cleanup command
                return handleCleanupCommand(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand + ". Use /dt help.");
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "--- DistantTicking Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/dt pdcinfo" + ChatColor.GRAY + " - Show tick-worthy block count for your current chunk (PDC data).");
        sender.sendMessage(ChatColor.YELLOW + "/dt status" + ChatColor.GRAY + " - Show current plugin status.");
        sender.sendMessage(ChatColor.YELLOW + "/dt list [page]" + ChatColor.GRAY + " - List all force-loaded chunks by the plugin.");
        sender.sendMessage(ChatColor.YELLOW + "/dt removehere" + ChatColor.GRAY + " - Remove the chunk you are in from the force-load list.");
        if (sender.hasPermission("distantticking.command.refresh")) { // Add refresh help if they have permission
            // Updated refresh command usage
            sender.sendMessage(ChatColor.YELLOW + "/dt refresh <radius> <vertical-up> <vertical-down>" + ChatColor.GRAY + " - Scans a radius of chunks and refreshes their tick-worthy block counts within a vertical range.");
        }
        if (sender.hasPermission("distantticking.command.cleanup")) { // Add cleanup help
            sender.sendMessage(ChatColor.YELLOW + "/dt cleanup" + ChatColor.GRAY + " - Scans loaded chunks and removes the old tick-worthy block count PDC key.");
        }
        if (sender.hasPermission("distantticking.command.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/dt reload" + ChatColor.GRAY + " - Reload the plugin's configuration.");
        }
        sender.sendMessage(ChatColor.AQUA + "-------------------------");
    }

    private boolean handlePdcInfoCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        if (!sender.hasPermission("distantticking.command.pdcinfo")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;
        Chunk chunk = player.getLocation().getChunk();
        int count = chunkDataManager.getTickWorthyBlocksCount(chunk); // Use new count method
        Set<BlockCoord> blocks = chunkDataManager.getTickWorthyBlocks(chunk); // Get the actual block coords

        sender.sendMessage(ChatColor.AQUA + "PDC Info for Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName() + ":");
        sender.sendMessage(ChatColor.YELLOW + "  Tick-worthy blocks tracked by plugin: " + count);
        if (count > 0) {
            sender.sendMessage(ChatColor.YELLOW + "  Locations: " + ChatColor.GRAY + blocks.stream().map(BlockCoord::toString).collect(Collectors.joining(", ")));
        }
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender) {
        if (!sender.hasPermission("distantticking.command.status")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        int totalForceLoaded = forceLoadManager.getTotalForceLoadedChunks();
        sender.sendMessage(ChatColor.AQUA + "--- DistantTicking Status ---");
        sender.sendMessage(ChatColor.YELLOW + "Total Chunks Force-Loaded by Plugin: " + ChatColor.WHITE + totalForceLoaded);
        sender.sendMessage(ChatColor.YELLOW + "Auto-Save Interval: " + ChatColor.WHITE + pluginConfig.getAutoSaveIntervalMinutes() + " minutes");
        sender.sendMessage(ChatColor.YELLOW + "Consistency Check Interval: " + ChatColor.WHITE + pluginConfig.getConsistencyCheckIntervalHours() + " hours");
        sender.sendMessage(ChatColor.YELLOW + "Debug Mode: " + (pluginConfig.isDebugMode() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.YELLOW + "Plugin Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.AQUA + "-------------------------");
        return true;
    }

    private boolean handleListChunksCommand(CommandSender sender, int page) {
        if (!sender.hasPermission("distantticking.command.list")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Map<String, Set<ChunkCoord>> forceLoaded = forceLoadManager.getForceLoadedChunks();
        List<String> chunkStrings = forceLoaded.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(coord -> ChatColor.GOLD + entry.getKey() + ": " + ChatColor.YELLOW + coord.toString()))
                .collect(Collectors.toList());

        if (chunkStrings.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No chunks are currently force-loaded by this plugin.");
            return true;
        }

        // Basic pagination (adjust items per page as needed)
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) chunkStrings.size() / itemsPerPage);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(ChatColor.RED + "Invalid page number. Total pages: " + totalPages + ".");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "--- Force-Loaded Chunks (Page " + page + "/" + totalPages + ") ---");
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, chunkStrings.size());

        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(chunkStrings.get(i));
        }
        sender.sendMessage(ChatColor.AQUA + "-------------------------");
        return true;
    }

    private boolean handleRemoveHereCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        if (!sender.hasPermission("distantticking.command.removehere")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;
        Chunk chunk = player.getLocation().getChunk();

        // Clear all stored tick-worthy block locations and remove from force-load list
        chunkDataManager.setTickWorthyBlocks(chunk, null); // Set to null/empty to clear PDC
        forceLoadManager.removeChunkFromForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());

        sender.sendMessage(ChatColor.GREEN + "Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName() + " has been cleared of tick-worthy block data and removed from force-load list.");
        sender.sendMessage(ChatColor.YELLOW + "It will now unload if no players are nearby and no other force-loaders are active.");
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("distantticking.command.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Reload the configuration
        pluginConfig.loadConfig();

        // Stop and restart tasks with new intervals
        forceLoadManager.stopAutoSaveTask();
        forceLoadManager.stopConsistencyCheckTask();
        forceLoadManager.startAutoSaveTask(pluginConfig.getAutoSaveIntervalMinutes());
        forceLoadManager.startConsistencyCheckTask(pluginConfig.getConsistencyCheckIntervalHours());

        sender.sendMessage(ChatColor.GREEN + "DistantTicking configuration reloaded!");
        return true;
    }

    // --- NEW REFRESH COMMAND LOGIC ---
    private boolean handleRefreshCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        if (!sender.hasPermission("distantticking.command.refresh")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        // Updated argument length check for radius, vertical-up, vertical-down
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /dt refresh <radius> <vertical-up> <vertical-down>");
            return true;
        }

        Player player = (Player) sender;
        int radius;
        int verticalUp;
        int verticalDown;
        try {
            radius = Integer.parseInt(args[1]);
            verticalUp = Integer.parseInt(args[2]);
            verticalDown = Integer.parseInt(args[3]);

            if (radius < 0 || radius > 50) { // Limit max radius to prevent server crash
                sender.sendMessage(ChatColor.RED + "Radius must be between 0 and 50 chunks.");
                return true;
            }
            if (verticalUp < 0 || verticalUp > 256) { // Limit max vertical scan height
                sender.sendMessage(ChatColor.RED + "Vertical-up must be between 0 and 256 blocks.");
                return true;
            }
            if (verticalDown < 0 || verticalDown > 256) { // Limit max vertical scan height
                sender.sendMessage(ChatColor.RED + "Vertical-down must be between 0 and 256 blocks.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number argument. Please enter whole numbers for radius, vertical-up, and vertical-down.");
            return true;
        }

        final int finalRadius = radius;
        final World world = player.getWorld();
        final int playerChunkX = player.getLocation().getChunk().getX();
        final int playerChunkZ = player.getLocation().getChunk().getZ();
        final int playerY = player.getLocation().getBlockY();

        // Calculate actual scan boundaries, clamping to world limits
        final int minY = Math.max(world.getMinHeight(), playerY - verticalDown);
        final int maxY = Math.min(world.getMaxHeight() - 1, playerY + verticalUp); // -1 for 0-indexed Y


        sender.sendMessage(ChatColor.AQUA + "Starting chunk refresh scan for a " + (finalRadius * 2 + 1) + "x" + (finalRadius * 2 + 1) + " chunk area...");
        sender.sendMessage(ChatColor.AQUA + "Scanning vertically from Y=" + minY + " to Y=" + maxY + " (relative to player Y=" + playerY + ").");
        sender.sendMessage(ChatColor.YELLOW + "This may take a moment. Progress will be reported.");

        new BukkitRunnable() {
            int chunksScanned = 0;
            AtomicInteger totalBlocksCounted = new AtomicInteger(0);
            AtomicInteger chunksForceLoaded = new AtomicInteger(0);
            AtomicInteger chunksUnforceLoaded = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                // Calculate chunk boundaries
                int minChunkX = playerChunkX - finalRadius;
                int maxChunkX = playerChunkX + finalRadius;
                int minChunkZ = playerChunkZ - finalRadius;
                int maxChunkZ = playerChunkZ + finalRadius;

                for (int x = minChunkX; x <= maxChunkX; x++) {
                    for (int z = minChunkZ; z <= maxChunkZ; z++) {
                        // Periodically yield to ensure the server doesn't freeze
                        if (this.isCancelled()) {
                            return;
                        }
                        if (chunksScanned % 5 == 0 && chunksScanned > 0) { // Report every 5 chunks scanned
                            sender.sendMessage(ChatColor.GRAY + "Scanned " + chunksScanned + " chunks so far...");
                        }

                        Chunk chunk = world.getChunkAt(x, z);
                        if (!chunk.isLoaded()) {
                            chunk.load(true);
                        }

                        Set<BlockCoord> currentChunkTickWorthyBlocks = ConcurrentHashMap.newKeySet();
                        for (int blockX = 0; blockX < 16; blockX++) {
                            for (int blockZ = 0; blockZ < 16; blockZ++) {
                                for (int blockY = minY; blockY <= maxY; blockY++) {
                                    Block block = chunk.getBlock(blockX, blockY, blockZ);
                                    if (TickWorthyBlocks.isTickWorthy(block.getType())) {
                                        currentChunkTickWorthyBlocks.add(new BlockCoord(blockX, blockY, blockZ));
                                    }
                                }
                            }
                        }

                        Set<BlockCoord> oldPdcBlocks = chunkDataManager.getTickWorthyBlocks(chunk);
                        boolean oldWasEmpty = oldPdcBlocks.isEmpty();

                        chunkDataManager.setTickWorthyBlocks(chunk, currentChunkTickWorthyBlocks);
                        totalBlocksCounted.addAndGet(currentChunkTickWorthyBlocks.size());

                        boolean newIsEmpty = currentChunkTickWorthyBlocks.isEmpty();

                        if (!newIsEmpty && oldWasEmpty) {
                            forceLoadManager.addChunkToForceLoad(world, chunk.getX(), chunk.getZ());
                            chunksForceLoaded.incrementAndGet();
                        } else if (newIsEmpty && !oldWasEmpty) {
                            forceLoadManager.removeChunkFromForceLoad(world, chunk.getX(), chunk.getZ());
                            chunksUnforceLoaded.incrementAndGet();
                        }
                        chunkDataManager.removeOldPdcKey(chunk);

                        chunksScanned++;
                    }
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        sender.sendMessage(ChatColor.AQUA + "--- DistantTicking Scan Complete ---");
                        sender.sendMessage(ChatColor.GREEN + "Scanned " + chunksScanned + " chunks.");
                        sender.sendMessage(ChatColor.GREEN + "Total tick-worthy blocks identified: " + totalBlocksCounted.get());
                        sender.sendMessage(ChatColor.GREEN + "Chunks newly force-loaded: " + chunksForceLoaded.get());
                        sender.sendMessage(ChatColor.GREEN + "Chunks un-force-loaded: " + chunksUnforceLoaded.get());
                        sender.sendMessage(ChatColor.GREEN + "Duration: " + duration + " seconds.");
                        sender.sendMessage(ChatColor.AQUA + "---------------------------------");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private boolean handleCleanupCommand(CommandSender sender) {
        if (!sender.hasPermission("distantticking.command.cleanup")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Starting old PDC key cleanup for loaded chunks...");
        sender.sendMessage(ChatColor.YELLOW + "This will only affect currently loaded chunks.");

        new BukkitRunnable() {
            int cleanedChunks = 0;
            long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Chunk loadedChunk : world.getLoadedChunks()) {
                        if (chunkDataManager.removeOldPdcKey(loadedChunk)) {
                            cleanedChunks++;
                        }
                    }
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        sender.sendMessage(ChatColor.AQUA + "--- Old PDC Cleanup Complete ---");
                        sender.sendMessage(ChatColor.GREEN + "Cleaned old 'tick_worthy_count' key from " + cleanedChunks + " loaded chunks.");
                        sender.sendMessage(ChatColor.GREEN + "Duration: " + duration + " seconds.");
                        sender.sendMessage(ChatColor.AQUA + "-----------------------------");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }
}