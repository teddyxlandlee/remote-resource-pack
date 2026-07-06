import java.util.Locale

plugins {
    java
    id("com.modrinth.minotaur")
}

val destArtifacts = extensions.create<DestArtifacts>("destArtifacts")

repositories {
    maven("https://maven.hixland.com") {
        name = "Teddy's Maven"
    }

    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

java {
    withSourcesJar()

    toolchain {
        vendor = JvmVendorSpec.MICROSOFT
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

enum class ModLoader(
    val primary: String,
    val abbreviation: String,
    vararg compatible: String,
) {
    FABRIC("fabric", "Fabric", "quilt"),
    FORGE("forge", "Forge"),
    NEO("neoforge", "Neo")
    ;
    val supported = listOf(primary, *compatible)

    companion object {
        operator fun invoke(name: String): ModLoader = entries.first {
            it.primary == name
        }
    }
}

modrinth {
    detectLoaders = false
    autoAddDependsOn = false
    val loader = ModLoader(project.name.substringAfterLast('-'))

    token = providers.environmentVariable("MR_TOKEN").orElse("0")
    projectId = providers.gradleProperty("mr_project_id")
    versionNumber = provider { "${project.version}-${loader.primary}" }
    versionName = provider {
        val template = providers.gradleProperty("mr_version_name_format")
        // 1: MC version range, 2: loader name, 3: mod abbreviation 4: version displayed
        String.format(
            Locale.ENGLISH, template.get(),
            property("mod.mc_releases_display"),
            loader.abbreviation,
            findProperty("mr_version_mod_abbr"),
            project.version,
        )
    }
    changelog = providers.gradleProperty("mr_version_changelog")
    file = destArtifacts.binaryJar
    additionalFiles = listOf(destArtifacts.sourcesJar)
    versionType = providers.gradleProperty("mr_version_type")
//    gameVersions = (property("mod.mc_releases") as List<*>).map(Any?::toString)
    loaders = loader.supported

    debugMode = providers.environmentVariable("MR_DEBUG").orElse("").map(String::isNotBlank)
}
