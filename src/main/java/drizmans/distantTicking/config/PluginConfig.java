package drizmans.distantTicking.config;

import drizmans.distantTicking.DistantTicking;
import org.bukkit.Material;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class PluginConfig {

    private final DistantTicking plugin;

    private int autoSaveIntervalMinutes;
    private int consistencyCheckIntervalHours;
    private boolean debugMode;
    private Set<Material> tickWorthyMaterials;

    /**
     * Constructor for PluginConfig. Loads configuration values from config.yml.
     * @param plugin The main plugin instance.
     */
    public PluginConfig(DistantTicking plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig(); // Creates config.yml if it doesn't exist
        loadConfig();
    }

    /**
     * Loads/reloads all configuration values from the plugin's config.yml.
     */
    public void loadConfig() {
        plugin.reloadConfig(); // Reloads from disk

        this.autoSaveIntervalMinutes = plugin.getConfig().getInt("settings.auto-save-interval-minutes", 5);
        this.consistencyCheckIntervalHours = plugin.getConfig().getInt("settings.consistency-check-interval-hours", 6);
        this.debugMode = plugin.getConfig().getBoolean("settings.debug-mode", false);

        // Load tick-worthy materials
        List<String> materialNames = plugin.getConfig().getStringList("tick-worthy-blocks");
        this.tickWorthyMaterials = new HashSet<>();
        for (String name : materialNames) {
            try {
                Material material = Material.valueOf(name.toUpperCase());
                this.tickWorthyMaterials.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + name + "' found in config.yml tick-worthy-blocks list. Skipping.");
            }
        }

        plugin.getLogger().info("Configuration loaded: Auto-Save Interval: " + autoSaveIntervalMinutes + " min, Consistency Check: " + consistencyCheckIntervalHours + " hours, Debug Mode: " + debugMode);
        // The detailed list of materials will now only show if debug mode is active due to logger level
    }

    /**
     * Gets the configured auto-save interval in minutes.
     * @return Auto-save interval.
     */
    public int getAutoSaveIntervalMinutes() {
        return autoSaveIntervalMinutes;
    }

    /**
     * Gets the configured consistency check interval in hours.
     * @return Consistency check interval.
     */
    public int getConsistencyCheckIntervalHours() {
        return consistencyCheckIntervalHours;
    }

    /**
     * Checks if debug mode is enabled.
     * @return true if debug mode is enabled, false otherwise.
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Gets the set of materials configured as tick-worthy.
     * @return A set of tick-worthy Materials.
     */
    public Set<Material> getTickWorthyMaterials() {
        return tickWorthyMaterials;
    }

    /**
     * Gets the appropriate logging level based on the debug mode setting.
     * @return Level.FINE if debug mode is enabled, otherwise Level.INFO.
     */
    public Level getLoggingLevel() {
        return debugMode ? Level.FINE : Level.INFO;
    }
}