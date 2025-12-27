package xland.mcmod.remoteresourcepack;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ServiceLoader;

public enum Platform {
    FABRIC("net.fabricmc.loader.api.FabricLoader"),
    FORGE("net.minecraftforge.versions.forge.ForgeVersion"),
    NEO("net.neoforged.fml.ModLoader"),
    ;
    private final String declaredClass;

    Platform(String declaredClass) {
        this.declaredClass = declaredClass;
    }

    private static @NotNull Platform tryDetect() {
        var lookup = MethodHandles.lookup();

        for (Platform platform : Platform.values()) {
            try {
                lookup.findClass(platform.declaredClass);
                return platform;
            } catch (ClassNotFoundException | IllegalAccessException e) {
                if (e instanceof IllegalAccessException) {
                    org.slf4j.LoggerFactory.getLogger(Platform.class).error(
                            "Cannot access class {} of platform {} because of access denial",
                            platform.declaredClass, platform, e
                    );
                }
            }
        }
        throw new IllegalStateException("This environment does not match any known platform");
    }

    private static Platform PLATFORM;

    public static @NotNull Platform detect() {
        if (PLATFORM == null) {
            PLATFORM = tryDetect();
        }
        return PLATFORM;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Implementation {
        Platform value();
    }

    /// Worth caching:
    /// ```java
    /// public static XxxApi getInstance() {
    ///     class Holder {
    ///         static final XxxApi INSTANCE = Platform.findImplementation(XxxApi.class);
    ///     }
    ///     return Holder.INSTANCE;
    /// }
    /// ```
    public static <T> @NotNull T findImplementation(Class<T> baseClass) {
        final Platform detectedPlatform = detect();
        List<ServiceLoader.Provider<T>> providers = ServiceLoader.load(baseClass).stream()
                .filter(provider -> {
                    Class<? extends T> implClass = provider.type();
                    Implementation annotation = implClass.getAnnotation(Implementation.class);
                    return annotation != null && detectedPlatform == annotation.value();
                })
                .toList();
        switch (providers.size()) {
            case 0 -> throw new IllegalStateException("Implementation for " + baseClass + " not found on platform " + detectedPlatform);
            case 1 -> {}
            default -> throw new IllegalStateException(
                    "Found multiple implementation for " + baseClass
                    + " on platform " + detectedPlatform
                    + ": " + providers.stream().map(ServiceLoader.Provider::type).toList()
            );
        }
        return providers.getFirst().get();
    }
}
