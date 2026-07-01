import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// This whole thing prevents Forge/NeoForge from frying your computer by recompiling Minecraft on multiple versions
interface ForgeMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", ForgeMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
