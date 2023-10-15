package xland.mcmod.remoteresourcepack.fabric.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class RRPMixinPlugin implements IMixinConfigPlugin {
    private MethodInfo
            target_PackRepository_reload,
            hook_MutablePackRepository_hookAddPackSource;
    private static final Logger LOGGER = LoggerFactory.getLogger(RRPMixinPlugin.class);

    @Override
    public void onLoad(String mixinPackage) {
        target_PackRepository_reload = MethodInfo.ofFabric(
                "net/minecraft/class_3283",
                "method_14445",
                "()V"
        );
        hook_MutablePackRepository_hookAddPackSource = MethodInfo.ofHook(
                "xland/mcmod/remoteresourcepack/fabric/MutablePackRepository",
                "hookAddPackSource",
                "(Lnet/minecraft/class_3283;)V"
        );
//        hook_RemoteResourcePack_insertEnabledPacks = MethodInfo.ofHook(
//                "xland/mcmod/remoteresourcepack/RemoteResourcePack",
//                "insertEnabledPacks",
//                "(Lnet/minecraft/class_3283;)V"
//        );
    }

    void parseMixinMinecraft(ClassNode node) {
        LOGGER.debug("Parsing MixinMinecraft, targeting {}", node.name);
        boolean[] magicDat = new boolean[1];
        node.methods.stream().filter(m -> "<init>".equals(m.name)).forEach(m -> {
            magicDat[0] = true;
            LOGGER.debug("Found `Minecraft` constructor {}", m.desc);
            final InsnList instructions = m.instructions;
            target_PackRepository_reload.findFirstInvocation(
                    instructions, Opcodes.INVOKEVIRTUAL).ifPresentOrElse(invokeReload -> {
                        InsnList before = new InsnList();
                        before.add(new InsnNode(Opcodes.DUP));
                        before.add(new InsnNode(Opcodes.DUP));
                        before.add(hook_MutablePackRepository_hookAddPackSource.toInstruction(Opcodes.INVOKESTATIC, true));

                        InsnList after = new InsnList();
//                        after.add(hook_RemoteResourcePack_insertEnabledPacks.toInstruction(Opcodes.INVOKESTATIC, false));
                        after.add(new InsnNode(Opcodes.POP));   // since 1.19.3 we don't need to insert it by mixin

                        instructions.insertBefore(invokeReload, before);
                        instructions.insert(invokeReload, after);
                    }, () -> {
                        LOGGER.error("Couldn't find method {} in class {}", target_PackRepository_reload, node.name);
                        tryDump(node);
                    });
        });
        if (!magicDat[0]) {
            LOGGER.error("Couldn't find a constructor??? Your class {} sucks", node.name);
            tryDump(node);
        }
    }

    private static void tryDump(ClassNode cn) {
        if (!Boolean.getBoolean("rrp.fabric.asm.dumpOnError")) return;
        var cw = new org.objectweb.asm.ClassWriter(0);
        cn.accept(cw);
        var path = java.nio.file.Path.of(".rrp", cn.name)
                .resolveSibling(cn.name.substring(cn.name.lastIndexOf('/') + 1).concat(".class"));
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, cw.toByteArray());
            LOGGER.info("The class {} is dumped into {}", cn.name, path);
        } catch (Exception e) {
            LOGGER.error("Failed to dump class {} on error", cn.name);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("MixinMinecraft")) return;
        if (targetClass.invisibleAnnotations.stream().noneMatch(a -> Type.getDescriptor(TransformTarget.class).equals(a.desc)))
            return;

        parseMixinMinecraft(targetClass);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
