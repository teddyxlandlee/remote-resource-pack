package xland.mcmod.rrp.v3.mixin;

import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xland.mcmod.rrp.v3.RemoteResourcePack;

@Mixin(Options.class)
public abstract class MixinOptions {
    @Inject(method = "loadSelectedResourcePacks", at = @At("RETURN"))
    private void afterLoadingPacks(PackRepository repository, CallbackInfo ci) {
        RemoteResourcePack.insertEnabledPacks(repository);
    }
}
