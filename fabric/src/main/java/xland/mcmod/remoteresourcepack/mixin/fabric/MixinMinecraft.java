package xland.mcmod.remoteresourcepack.mixin.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xland.mcmod.remoteresourcepack.RRPCacheRepoSource;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;
import xland.mcmod.remoteresourcepack.fabric.MutablePackRepository;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Invoker("getResourcePackRepository")
    public abstract PackRepository remoteResourcePack$getResourcePackRepository();

    @Inject(method = "<init>", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
    ))
    private void injectResourcePacks(GameConfig gameConfig, CallbackInfo ci) {
        @SuppressWarnings("all")
        final PackRepository packRepository = this.remoteResourcePack$getResourcePackRepository();
        ((MutablePackRepository) packRepository).remoteResourcePack$addRepoSource(
                new RRPCacheRepoSource(RemoteResourcePack.getCacheFiles()));
    }
}
