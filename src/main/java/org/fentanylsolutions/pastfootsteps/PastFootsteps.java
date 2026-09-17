package org.fentanylsolutions.pastfootsteps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fentanylsolutions.pastfootsteps.config.Config;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = PastFootsteps.MODID,
    name = "PastFootsteps",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:unimixins;after:dsurround",
    guiFactory = "org.fentanylsolutions.pastfootsteps.config.ConfigGuiFactory",
    customProperties = { @Mod.CustomProperty(k = "license", v = "MIT"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/JackOfNoneTrades/PastFootsteps/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/pastfootsteps/icon.png"),
        @Mod.CustomProperty(k = "backgroundFile", v = "assets/pastfootsteps/background.png") })
public class PastFootsteps {

    public static final String MODID = "pastfootsteps";
    public static final Logger LOG = LogManager.getLogger("PastFootsteps");
    @SidedProxy(
        clientSide = "org.fentanylsolutions.pastfootsteps.client.ClientProxy",
        serverSide = "org.fentanylsolutions.pastfootsteps.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        org.fentanylsolutions.pastfootsteps.network.ContactSync.initialize();
        proxy.init();
    }
}
