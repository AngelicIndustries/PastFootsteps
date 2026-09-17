package org.fentanylsolutions.pastfootsteps.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@SuppressWarnings("unused")
@LateMixin
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.pastfootsteps.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        List<String> mixins = new ArrayList<>();
        if (FMLLaunchHandler.side()
            .isClient() && Loader.isModLoaded("dsurround")) {
            mixins.add("dsurround.MixinFootsteps");
        }
        if (FMLLaunchHandler.side()
            .isClient() && Loader.isModLoaded("anextratouch")) {
            mixins.add("anextratouch.MixinLegacyAccents");
            mixins.add("anextratouch.MixinLegacyFootprints");
        }
        return mixins;
    }
}
