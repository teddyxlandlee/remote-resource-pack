package xland.mcmod.remoteresourcepack.mixin.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xland.mcmod.remoteresourcepack.fabric.RemoteResourcePackImpl;

@Mixin(Minecraft.class)
abstract class MixinMinecraft {
    @Inject(method = "<init>", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
    ))
    private void beforePackRepositoryReload(GameConfig gameConfig, CallbackInfo ci) {
        RemoteResourcePackImpl.addPackSource(((Minecraft)(Object)this).getResourcePackRepository());
    }
}
