package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

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
    protected @NotNull Layout addFooterButtons() {
        var layout = LinearLayout.horizontal().spacing(8);
        layout.addChild(Button.builder(TAKE_ME_TO_NEO, (arg) -> {
            // open the website
            net.minecraft.util.Util.getPlatform().openUri(NEO_SITE);
        }).build());
        layout.addChild(Button.builder(
                CommonComponents.GUI_PROCEED, (arg) -> onClose()).build()
        );
        return layout;
    }

    @Override
    public void onClose() {
        onClose.run();
    }
}