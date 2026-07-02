package net.lunix.chiseledenchants.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.lunix.chiseledenchants.ChiseledEnchantsCommon;

public class ChiseledEnchantsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ChiseledEnchantsCommon.init(FabricLoader.getInstance().getConfigDir());
    }
}
