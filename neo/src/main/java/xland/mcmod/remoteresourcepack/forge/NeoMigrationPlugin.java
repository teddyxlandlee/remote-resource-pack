package xland.mcmod.remoteresourcepack.forge;

import com.google.common.base.Suppliers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class NeoMigrationPlugin implements IMixinConfigPlugin {
    private static final Collection<String> patchedMethodCandidate = Collections.singleton("addInitialScreens");

    private static InsnList injectedList() {
        var list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "xland/mcmod/remoteresourcepack/forge/NeoMigrationHooks",
                "addWarningScreen",
                "(Ljava/util/List;)V",
                true
        ));
        return list;
    }

    private static void applyMigration(ClassNode classNode) {
        classNode.methods.stream()
                // 1.21.6+: use boolean
                .filter(m -> patchedMethodCandidate.contains(m.name))
                .forEach(method -> {
                    for (AbstractInsnNode ins : method.instructions) {
                        switch (ins.getOpcode()) {
                            case Opcodes.IRETURN, Opcodes.RETURN -> method.instructions.insertBefore(ins, injectedList());
                        }
                    }
                });
    }

    /* MixinConfigPlugin skeletons */

    @Override
    public void onLoad(String s) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoMigrationPlugin.class);
    private static final Supplier<Boolean> DISABLES_MIXIN = Suppliers.memoize(() -> {
        Path path = null;
        try {
            Path parentFolder = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
            path = parentFolder.resolve("rrp-neo-migration-screen.disable");
        } catch (Throwable _) {
        }

        // If file state undetermined, return `false`, i.e. do not disable mixin
        var disable = path != null && Files.exists(path);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} NeoMigrationPlugin", disable ? "Disabled" : "Enabled");
        }
        return disable;
    });

    @Override
    public boolean shouldApplyMixin(String s, String s1) {
        return !DISABLES_MIXIN.get();
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
        if (classNode.invisibleAnnotations == null) return;
        if (classNode.invisibleAnnotations.stream().anyMatch(a -> Type.getDescriptor(NeoMigrationCandidate.class).equals(a.desc))) {
            applyMigration(classNode);
        }
    }
}
