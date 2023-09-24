package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.common.Mod;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForge {
    public RemoteResourcePackForge() {
        RemoteResourcePack.init();
    }
}