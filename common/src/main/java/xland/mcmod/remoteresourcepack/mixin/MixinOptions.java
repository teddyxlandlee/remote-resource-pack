package xland.mcmod.remoteresourcepack.mixin;

import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xland.mcmod.remoteresourcepack.RemoteResourcePack;

@Mixin(Options.class)
public abstract class MixinOptions {
    @Inject(method = "loadSelectedResourcePacks", at = @At("RETURN"))
    private void afterLoadingPacks(PackRepository packRepository, CallbackInfo ci) {
        RemoteResourcePack.insertEnabledPacks(packRepository);
    }
}
