package xland.mcmod.remoteresourcepack.forge.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import xland.mcmod.remoteresourcepack.forge.NeoMigrationCandidate;

@Mixin(Minecraft.class)
@NeoMigrationCandidate
abstract class Stub {
}
