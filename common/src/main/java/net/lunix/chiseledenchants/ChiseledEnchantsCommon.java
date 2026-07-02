package net.lunix.chiseledenchants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ChiseledEnchantsCommon {

    public static final String MOD_ID = "chiseledenchants";
    public static final Logger LOGGER = LoggerFactory.getLogger("chiseledEnchants");

    /** Loader config directory, supplied by the platform entrypoint at mod construction. */
    public static Path CONFIG_DIR;

    /** Called once by each platform entrypoint with the loader's config directory. */
    public static void init(Path configDir) {
        CONFIG_DIR = configDir;
        ModConfig.load();
        LOGGER.info("chiseledEnchants initialized");
    }
}
