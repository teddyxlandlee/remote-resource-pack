package xland.mcmod.remoteresourcepack.fabric.asm;

import com.google.common.base.Suppliers;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

record MethodInfo(String owner, String name, String desc) {
    private static final Supplier<MappingResolver> MAPPING_RESOLVER =
            Suppliers.memoize(() -> FabricLoader.getInstance().getMappingResolver());
    private static final String INTERMEDIARY = "intermediary";

    public static MethodInfo ofFabric(String ownerInt, String nameInt, String descInt) {
        final MappingResolver resolver = MAPPING_RESOLVER.get();
        final String owner = mapObjectType(ownerInt);
        final String name = resolver.mapMethodName(INTERMEDIARY, ownerInt, nameInt, descInt);
        final String desc = mapType(Type.getMethodType(descInt)).getDescriptor();
        return new MethodInfo(owner, name, desc);
    }

    public static MethodInfo ofHook(String owner, String name, String descInt) {
        return new MethodInfo(owner, name, mapType(Type.getMethodType(descInt)).getDescriptor());
    }

    public Optional<MethodInsnNode> findFirstInvocation(InsnList list, int opcode) {
        for (AbstractInsnNode node : list) {
            if (!(node instanceof MethodInsnNode methodNode)) continue;
            if (methodNode.getOpcode() != opcode) continue;
            if (isInstruction(methodNode)) return Optional.of(methodNode);
        }
        return Optional.empty();
    }

    boolean isInstruction(MethodInsnNode node) {
        return Objects.equals(node.owner, owner) && Objects.equals(node.name, name) && Objects.equals(node.desc, desc);
    }

    public MethodInsnNode toInstruction(int opcode, boolean isItf) {
        return new MethodInsnNode(opcode, owner, name, desc, isItf);
    }
    private static Type mapType(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT -> Type.getObjectType(MAPPING_RESOLVER.get().mapClassName(INTERMEDIARY, type.getClassName()).replace('.', '/'));
            case Type.ARRAY -> Type.getType(StringUtils.repeat('[', type.getDimensions()).concat(mapType(type).getDescriptor()));
            case Type.METHOD -> {
                final Type[] argumentTypes = type.getArgumentTypes();
                for (int i = argumentTypes.length - 1; i >= 0; i--)
                    argumentTypes[i] = mapType(argumentTypes[i]);
                yield Type.getMethodType(mapType(type.getReturnType()), argumentTypes);
            }
            default -> type;    // contains int, void, etc.
        };
    }

    private static String mapObjectType(String s) {
        return mapType(Type.getObjectType(s)).getInternalName();
    }

    @Override
    public String toString() {
        return owner + '.' + name + ':' + desc;
    }
}
