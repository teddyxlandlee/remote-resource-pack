package xland.mcmod.remoteresourcepack.mixin.forge;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import xland.mcmod.remoteresourcepack.forge.NeoMigrationCandidate;

@Mixin(Minecraft.class)
@NeoMigrationCandidate
abstract class Stub {
}
