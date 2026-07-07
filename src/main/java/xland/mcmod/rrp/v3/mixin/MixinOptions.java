/*
 * Remote Resource Pack: load resource packs to which contents are downloaded from the internet.
 * Copyright (c) 2023-2026 teddyxlandlee & contributors.
 * SPDX-License-Identifier: MIT
 */
package xland.mcmod.rrp.v3.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xland.mcmod.rrp.v3.RemoteResourcePack;

import java.util.Collection;

@Mixin(Options.class)
public abstract class MixinOptions {
    @ModifyArg(method = "loadSelectedResourcePacks", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;setSelected(Ljava/util/Collection;)V"
    ))
    private Collection<String> afterLoadingPacks(Collection<String> packs) {
        return RemoteResourcePack.insertEnabledPacks(packs);
    }
}
