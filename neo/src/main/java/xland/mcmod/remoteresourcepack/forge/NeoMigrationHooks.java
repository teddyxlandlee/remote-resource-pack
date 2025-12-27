package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Function;

@SuppressWarnings("unused")
public interface NeoMigrationHooks {
    // used by the CoreMod
    static void addWarningScreen(List<Function<Runnable, Screen>> list) {
        try {
            list.add(NeoMigrationWarningScreen::new);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to add Migration warning screen", t);
        }
    }
}
