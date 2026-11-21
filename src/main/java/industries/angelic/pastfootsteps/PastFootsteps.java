package industries.angelic.pastfootsteps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import industries.angelic.pastfootsteps.config.Config;

@Mod(
    modid = PastFootsteps.MODID,
    name = "PastFootsteps",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:unimixins;after:dsurround",
    guiFactory = "industries.angelic.pastfootsteps.config.ConfigGuiFactory",
    customProperties = { @Mod.CustomProperty(k = "license", v = "MIT"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/AngelicIndustries/PastFootsteps/issues"),
        @Mod.CustomProperty(k = "iconFile", v = "assets/pastfootsteps/icon.png"),
        @Mod.CustomProperty(k = "backgroundFile", v = "assets/pastfootsteps/background.png") })
public class PastFootsteps {

    public static final String MODID = "pastfootsteps";
    public static final String MODGROUP = "industries.angelic";
    public static final Logger LOG = LogManager.getLogger("PastFootsteps");
    @SidedProxy(
        clientSide = "industries.angelic.pastfootsteps.client.ClientProxy",
        serverSide = "industries.angelic.pastfootsteps.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        industries.angelic.pastfootsteps.network.ContactSync.initialize();
        proxy.init();
    }
}
