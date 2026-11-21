package io.github.davevermeulen.boatqol;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.relauncher.Side;
import io.github.davevermeulen.boatqol.net.ConfigMessage;
import io.github.davevermeulen.boatqol.net.MoveMessage;
import net.minecraft.entity.EntityList;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Modern (1.9 to 1.12) boats for Minecraft 1.7.10. See docs/SPEC.md. */
@Mod(modid = BoatQOL.MODID, name = "BoatQOL", version = "1.0.0")
public class BoatQOL {
    public static final String MODID = "boatqol";
    public static final Logger LOGGER = LogManager.getLogger("BoatQOL");

    @Mod.Instance(MODID)
    public static BoatQOL instance;

    @SidedProxy(clientSide = "io.github.davevermeulen.boatqol.client.ClientProxy",
            serverSide = "io.github.davevermeulen.boatqol.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BoatQOLConfig.load(event.getSuggestedConfigurationFile());
        LOGGER.info("BoatQOL loaded (use modern ice slide: {})", BoatQOLConfig.useModernIceSlide);

        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(MoveMessage.Handler.class, MoveMessage.class, 0, Side.SERVER);
        network.registerMessage(ConfigMessage.Handler.class, ConfigMessage.class, 1, Side.CLIENT);
    }

    @Mod.EventHandler
    @SuppressWarnings("unchecked")
    public void init(FMLInitializationEvent event) {
        // Same tracking settings as the vanilla boat.
        EntityRegistry.registerModEntity(ModernBoat.class, "Boat", 0, this, 80, 3, true);
        // Save and load the modern boat under the vanilla "Boat" id, so worlds keep
        // their boats (as vanilla boats) if the mod is removed, and old boats load as modern ones.
        EntityList.stringToClassMapping.put("Boat", ModernBoat.class);
        EntityList.classToStringMapping.put(ModernBoat.class, "Boat");

        BoatEvents events = new BoatEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        proxy.init();
    }
}
