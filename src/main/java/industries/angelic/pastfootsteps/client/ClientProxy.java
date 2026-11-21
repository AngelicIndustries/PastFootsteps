package industries.angelic.pastfootsteps.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import industries.angelic.pastfootsteps.CommonProxy;
import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.config.Config;

public class ClientProxy extends CommonProxy {

    private final KeyBinding toggle = new KeyBinding(
        "pastfootsteps.toggle",
        Keyboard.KEY_NONE,
        "category.pastfootsteps");

    @Override
    public void init() {
        industries.angelic.pastfootsteps.client.compat.CarpentersBlocksCompat.init();
        ClientRegistry.registerKeyBinding(toggle);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new WalkingCommand());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(FootstepController.INSTANCE);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        while (toggle.isPressed()) {
            Config.toggle();
            FootstepController.INSTANCE.reset();
            if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
                new ChatComponentTranslation(Config.enabled ? "pastfootsteps.enabled" : "pastfootsteps.disabled"));
        }
        FootstepController.INSTANCE.tick();
    }

    @SubscribeEvent
    public void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (PastFootsteps.MODID.equals(event.modID)) {
            Config.sync();
            FootstepController.INSTANCE.reset();
        }
    }

    @SubscribeEvent
    public void renderDebug(RenderGameOverlayEvent.Text event) {
        if (Config.debug && Config.enabled) event.left.add("PastFootsteps: " + FootstepController.INSTANCE.debugText());
    }

    @SubscribeEvent
    public void renderContacts(RenderWorldLastEvent event) {
        FootstepController.INSTANCE.renderContacts(event.partialTicks);
    }
}
