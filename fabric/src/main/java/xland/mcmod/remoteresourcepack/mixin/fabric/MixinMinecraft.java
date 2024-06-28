package xland.mcmod.remoteresourcepack.mixin.fabric;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import xland.mcmod.remoteresourcepack.fabric.asm.TransformTarget;

@Mixin(Minecraft.class)
@TransformTarget
public abstract class MixinMinecraft {
    // stub
}
