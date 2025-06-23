package drizmans.distantTicking.util;

import drizmans.distantTicking.config.PluginConfig;
import org.bukkit.Material;
import java.util.Collections;
import java.util.Set;

public class TickWorthyBlocks {
    // This set will now be initialized dynamically from the configuration.
    private static Set<Material> TICK_WORTHY_MATERIALS = Collections.emptySet(); // Initialize as empty

    /**
     * Initializes the set of tick-worthy materials from the provided PluginConfig.
     * This method should be called once during plugin startup.
     * @param config The PluginConfig instance containing the loaded materials.
     */
    public static void initialize(PluginConfig config) {
        // Assign the set directly from the config, which is already loaded.
        // Make it unmodifiable to ensure it's not accidentally altered after loading.
        TICK_WORTHY_MATERIALS = Collections.unmodifiableSet(config.getTickWorthyMaterials());
    }

    /**
     * Checks if a given Material is considered "tick-worthy" by this plugin.
     * @param material The Material to check.
     * @return true if the material is in the tick-worthy list, false otherwise.
     */
    public static boolean isTickWorthy(Material material) {
        return TICK_WORTHY_MATERIALS.contains(material);
    }
}