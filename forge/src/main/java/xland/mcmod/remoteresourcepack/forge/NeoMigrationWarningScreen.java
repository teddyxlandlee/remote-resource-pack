package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;

import java.util.List;
import java.util.function.Function;

@SuppressWarnings("unused")
public class NeoMigrationWarningScreen extends WarningScreen {
    private final Runnable onClose;
    private static final Component TITLE, CONTENT, NARRATION, TAKE_ME_TO_NEO;
    private static final String NEO_SITE = "https://neoforged.net";

    protected NeoMigrationWarningScreen(Runnable onClose) {
        super(TITLE, CONTENT, NARRATION);
        this.onClose = onClose;
    }

    static {
        TITLE = Component.translatable("rrp.migrate.neo.title");
        CONTENT = Component.translatable("rrp.migrate.neo.content", NEO_SITE);
        NARRATION = Component.empty().append(TITLE).append("\n").append(CONTENT);
        TAKE_ME_TO_NEO = Component.translatable("rrp.migrate.neo.take_me_to_neo");
    }

    @Override
    protected void initButtons(int i) {
        assert this.minecraft != null;
        this.addRenderableWidget(Button.builder(TAKE_ME_TO_NEO, (arg) -> {
            // open the website
            net.minecraft.Util.getPlatform().openUri(NEO_SITE);
        }).bounds(this.width / 2 - 155, 100 + i, 150, 20).build());
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_BACK, (arg) -> onClose()
                ).bounds(this.width / 2 - 155 + 160, 100 + i, 150, 20).build()
        );
    }

    @Override
    public void onClose() {
        onClose.run();
    }

    @SuppressWarnings("unused")
    @DontObfuscate  // used by the CoreMod
    public static void addWarningScreen(List<Function<Runnable, Screen>> list) {
        try {
            list.add(NeoMigrationWarningScreen::new);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to add Migration warning screen", t);
        }
    }
}
