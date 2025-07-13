package xland.mcmod.remoteresourcepack.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackNeo {
    public RemoteResourcePackNeo(Dist dist, ModContainer modContainer) {
        if (!dist.isClient())
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        modVersion = modContainer.getModInfo().getVersion().toString();

        RemoteResourcePack.init();
    }

    static String modVersion;
}
