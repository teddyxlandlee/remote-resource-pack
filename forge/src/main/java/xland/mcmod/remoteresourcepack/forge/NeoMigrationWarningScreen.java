package xland.mcmod.remoteresourcepack.forge;

import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

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
        assert this.minecraft != null;
        var layout = LinearLayout.horizontal().spacing(8);
        layout.addChild(Button.builder(TAKE_ME_TO_NEO, buttonCallback()).build());
        layout.addChild(Button.builder(
                CommonComponents.GUI_PROCEED, (arg) -> onClose()).build()
        );
        return layout;
    }

    private static Button.OnPress buttonCallback;

    private static @NotNull Button.OnPress buttonCallback() {
        if (buttonCallback == null) {
            final Collection<String> newVersions = List.of("1.21.11", "1.21.11 Unobfuscated", "1.21.11_unobfuscated");
            if (!newVersions.contains(((RemoteResourcePackImpl) RemoteResourcePackImpl.platform()).minecraftVersion())) {
                // is old version, e.g. 1.21.9
                buttonCallback = (Button arg) -> Util.getPlatform().openUri(NEO_SITE);
            } else {
                // is new version, binary incompatible with dev environment
                MethodHandle mh;
                final Object platform;
                try {
                    Class<?> utilClass = Class.forName("net.minecraft.util.Util");
                    platform = utilClass.getMethod("getPlatform").invoke(null);
                    final Method openUri = platform.getClass().getMethod("openUri", String.class);
                    mh = java.lang.invoke.MethodHandles.lookup().unreflect(openUri);
                } catch (ClassNotFoundException | InvocationTargetException |
                         IllegalAccessException | NoSuchMethodException e) {
                    org.slf4j.LoggerFactory.getLogger(NeoMigrationWarningScreen.class).error(
                            "Your version is too new and thus binary incompatible with {}",
                            NeoMigrationWarningScreen.class, e
                    );

                    var error = new IncompatibleClassChangeError();
                    error.initCause(e);
                    throw error;
                }
                mh = java.lang.invoke.MethodHandles.insertArguments(mh, 0, platform, NEO_SITE);
                mh = java.lang.invoke.MethodHandles.dropArguments(mh, 0, Button.class);
                buttonCallback = java.lang.invoke.MethodHandleProxies.asInterfaceInstance(Button.OnPress.class, mh);
            }
        }
        return buttonCallback;
    }

    @Override
    public void onClose() {
        onClose.run();
    }
}