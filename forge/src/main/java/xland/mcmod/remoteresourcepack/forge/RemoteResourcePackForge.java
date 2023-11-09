package xland.mcmod.remoteresourcepack.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

import java.util.function.Predicate;

@Mod(RemoteResourcePack.MOD_ID)
public class RemoteResourcePackForge {
    public RemoteResourcePackForge() {
        // LexForge does not support constructor arg injection, so it sucks
        if (!FMLEnvironment.dist.isClient())
            throw new IllegalStateException("Mod " + RemoteResourcePack.MOD_ID + " is client-only!");
        {
            var logger = com.mojang.logging.LogUtils.getLogger();
            """
            Thank you for using our Forge mod!
            If you're looking for more features and performance improvements, we recommend trying NeoForge, a powerful MinecraftForge fork.
            Learn more about NeoForge and get it at https://neoforged.net.
            """.lines().filter(Predicate.not(String::isBlank)).forEach(logger::warn);
        }
        RemoteResourcePack.init();
    }
}