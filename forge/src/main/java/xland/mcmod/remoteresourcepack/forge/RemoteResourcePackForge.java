package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForge {
    public RemoteResourcePackForge() {
        if (!FMLEnvironment.dist.isClient())
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        RemoteResourcePack.init();
    }
}