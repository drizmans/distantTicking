package drizmans.distantTicking.command;

import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.config.PluginConfig;
import drizmans.distantTicking.util.ChunkCoord;
import drizmans.distantTicking.util.TickWorthyBlocks; // Needed for scanning blocks
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material; // For block iteration
import org.bukkit.block.Block; // For block iteration
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable; // For async tasks
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList; // For chunk coordinates to scan
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
            sender.sendMessage(ChatColor.YELLOW + "/dt refresh <radius>" + ChatColor.GRAY + " - Scans a radius of chunks and refreshes their tick-worthy block counts.");
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
        int count = chunkDataManager.getTickWorthyCount(chunk);

        sender.sendMessage(ChatColor.AQUA + "PDC Info for Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName() + ":");
        sender.sendMessage(ChatColor.YELLOW + "  Tick-worthy blocks tracked by plugin: " + count);
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

        // Set PDC count to 0 and remove from force-load list
        chunkDataManager.setTickWorthyCount(chunk, 0); // Explicitly set to 0
        forceLoadManager.removeChunkFromForceLoad(chunk.getWorld(), chunk.getX(), chunk.getZ());

        sender.sendMessage(ChatColor.GREEN + "Chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName() + " has been removed from force-load list.");
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
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dt refresh <radius>");
            return true;
        }

        Player player = (Player) sender;
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
            if (radius < 0 || radius > 50) { // Limit max radius to prevent server crash
                sender.sendMessage(ChatColor.RED + "Radius must be between 0 and 50 chunks.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid radius. Please enter a whole number.");
            return true;
        }

        final int finalRadius = radius;
        final World world = player.getWorld();
        final int playerChunkX = player.getLocation().getChunk().getX();
        final int playerChunkZ = player.getLocation().getChunk().getZ();
        final int playerY = player.getLocation().getBlockY();
        final int verticalLayers = 10; // 10 blocks above and 10 blocks below player Y

        sender.sendMessage(ChatColor.AQUA + "Starting chunk refresh scan for a " + (finalRadius * 2 + 1) + "x" + (finalRadius * 2 + 1) + " chunk area around you...");
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

                // Calculate vertical boundaries, clamp to world limits
                int minY = Math.max(world.getMinHeight(), playerY - verticalLayers);
                int maxY = Math.min(world.getMaxHeight() - 1, playerY + verticalLayers); // -1 to account for 0-indexed Y

                // Use a map to store current chunk counts before update, if needed for comparison
                // Or just update directly as per requirement

                for (int x = minChunkX; x <= maxChunkX; x++) {
                    for (int z = minChunkZ; z <= maxChunkZ; z++) {
                        // Periodically yield to ensure the server doesn't freeze
                        if (this.isCancelled()) { // Check if task was cancelled mid-run
                            return;
                        }
                        if (chunksScanned % 5 == 0 && chunksScanned > 0) { // Report every 5 chunks scanned
                            sender.sendMessage(ChatColor.GRAY + "Scanned " + chunksScanned + " chunks so far...");
                        }

                        Chunk chunk = world.getChunkAt(x, z);
                        // Ensure the chunk is loaded. getChunkAt usually loads it, but this is explicit.
                        // For a refresh, we NEED the chunk to be loaded to read its blocks.
                        // Calling chunk.load() here is required if the chunk is not already loaded by players.
                        if (!chunk.isLoaded()) {
                            // Load synchronously from async thread (this is safe as it's not the main thread)
                            chunk.load(true);
                        }

                        int currentChunkTickWorthyBlocks = 0;
                        for (int blockX = 0; blockX < 16; blockX++) {
                            for (int blockZ = 0; blockZ < 16; blockZ++) {
                                for (int blockY = minY; blockY <= maxY; blockY++) {
                                    Block block = chunk.getBlock(blockX, blockY, blockZ);
                                    if (TickWorthyBlocks.isTickWorthy(block.getType())) {
                                        currentChunkTickWorthyBlocks++;
                                    }
                                }
                            }
                        }

                        // Update PDC for this chunk
                        int oldPdcCount = chunkDataManager.getTickWorthyCount(chunk); // Get current PDC count before setting
                        chunkDataManager.setTickWorthyCount(chunk, currentChunkTickWorthyBlocks);
                        totalBlocksCounted.addAndGet(currentChunkTickWorthyBlocks);

                        // Update force-load status
                        if (currentChunkTickWorthyBlocks > 0 && oldPdcCount == 0) {
                            forceLoadManager.addChunkToForceLoad(world, chunk.getX(), chunk.getZ());
                            chunksForceLoaded.incrementAndGet();
                        } else if (currentChunkTickWorthyBlocks == 0 && oldPdcCount > 0) {
                            forceLoadManager.removeChunkFromForceLoad(world, chunk.getX(), chunk.getZ());
                            chunksUnforceLoaded.incrementAndGet();
                        }
                        // If count is >0 and was already >0, no change needed for force-load
                        // If count is 0 and was already 0, no change needed for force-load

                        chunksScanned++;
                    }
                }

                // Final report (on main thread, after loop completes)
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
                }.runTask(plugin); // Run on main thread
            }
        }.runTaskAsynchronously(plugin); // Start the task asynchronously

        return true;
    }
}