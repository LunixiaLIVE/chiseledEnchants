package net.lunix.chiseledenchants.neoforge;

import net.lunix.chiseledenchants.ChiseledCommands;
import net.lunix.chiseledenchants.ChiseledEnchantsCommon;
import net.lunix.chiseledenchants.ParticleScheduler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(ChiseledEnchantsCommon.MOD_ID)
public class ChiseledEnchantsNeoForge {

    public ChiseledEnchantsNeoForge() {
        ChiseledEnchantsCommon.init(FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChiseledCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ParticleScheduler.tick(event.getServer());
    }
}
