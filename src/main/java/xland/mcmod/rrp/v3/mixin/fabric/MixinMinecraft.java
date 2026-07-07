/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3.mixin.fabric;

//? if fabric {
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xland.mcmod.rrp.v3.fabric.RemoteResourcePackFabric;

@Mixin(Minecraft.class)
abstract public class MixinMinecraft {
    @Inject(method = "<init>", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"
    ))
    private void beforePackRepositoryReload(GameConfig gameConfig, CallbackInfo ci) {
        RemoteResourcePackFabric.addPackSource(((Minecraft)(Object)this).getResourcePackRepository());
    }
}
//?}