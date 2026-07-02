package net.lunix.chiseledenchants.neoforge;

import net.lunix.chiseledenchants.ChiseledEnchantsCommon;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(ChiseledEnchantsCommon.MOD_ID)
public class ChiseledEnchantsNeoForge {

    public ChiseledEnchantsNeoForge() {
        ChiseledEnchantsCommon.init(FMLPaths.CONFIGDIR.get());
    }
}
