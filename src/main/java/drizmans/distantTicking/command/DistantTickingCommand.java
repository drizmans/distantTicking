package drizmans.distantTicking.command;

import drizmans.distantTicking.DistantTicking;
import drizmans.distantTicking.manager.ChunkDataManager;
import drizmans.distantTicking.manager.ForceLoadManager;
import drizmans.distantTicking.config.PluginConfig; // Import PluginConfig
import drizmans.distantTicking.util.ChunkCoord;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DistantTickingCommand implements CommandExecutor {

    private final DistantTicking plugin;
    private final ChunkDataManager chunkDataManager;
    private final ForceLoadManager forceLoadManager;
    private final PluginConfig pluginConfig; // Added PluginConfig reference

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
        forceLoadManager.stopAutoSaveTask(); // Will need to add this method in ForceLoadManager
        forceLoadManager.stopConsistencyCheckTask();
        forceLoadManager.startAutoSaveTask(pluginConfig.getAutoSaveIntervalMinutes());
        forceLoadManager.startConsistencyCheckTask(pluginConfig.getConsistencyCheckIntervalHours());

        sender.sendMessage(ChatColor.GREEN + "DistantTicking configuration reloaded!");
        return true;
    }
}