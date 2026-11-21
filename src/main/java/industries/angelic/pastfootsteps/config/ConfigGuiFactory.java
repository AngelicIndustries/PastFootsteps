package industries.angelic.pastfootsteps.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import industries.angelic.pastfootsteps.PastFootsteps;

public class ConfigGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return Screen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static class Screen extends GuiConfig {

        public Screen(GuiScreen parent) {
            super(parent, categories(), PastFootsteps.MODID, false, false, "PastFootsteps");
        }

        private static List<IConfigElement> categories() {
            List<IConfigElement> list = new ArrayList<>();
            for (String category : new String[] { "general", "volume", "effects", "movement", "equipment", "rain",
                "footprints", "serversupport" }) {
                list.add(new ConfigElement(Config.configuration.getCategory(category)));
            }
            return list;
        }
    }
}
