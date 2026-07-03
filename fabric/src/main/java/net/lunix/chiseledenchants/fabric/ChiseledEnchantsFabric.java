package net.lunix.chiseledenchants.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.lunix.chiseledenchants.ChiseledCommands;
import net.lunix.chiseledenchants.ChiseledEnchantsCommon;

public class ChiseledEnchantsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ChiseledEnchantsCommon.init(FabricLoader.getInstance().getConfigDir());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ChiseledCommands.register(dispatcher));
    }
}
