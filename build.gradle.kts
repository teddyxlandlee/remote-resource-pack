import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.*

plugins {
    `java-library`
    id("net.fabricmc.fabric-loom") version "1.14-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.3.0" apply false
    id("com.modrinth.minotaur") version "2.+" apply false
}

val javaVersion = 25

allprojects {
    group = rootProject.ext["maven_group"]!!
    version = rootProject.ext["mod_version"]!!

    repositories {
        maven("https://mvn.7c7.icu") {
            name = "7c7 Maven"
        }
        maven("https://maven.fabricmc.net")
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        withSourcesJar()
        toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
}

private fun Configuration.withDependency(c: FileCollection) : Configuration {
    this.dependencies.add(dependencyFactory.create(c))
    return this
}

// Shadow jar
private fun subprojectArchives(taskName: String) : Iterable<Configuration> = subprojects.map { p ->
    val files : FileCollection = p.tasks.getByName<Jar>(taskName).outputs.files
    p.configurations.create("universalShadowCandidate_${taskName}_subproject_${p.name}").withDependency(files)
}

tasks.register("shadowJar", ShadowJar::class) {
    configurations.set(subprojectArchives("jar"))
    archiveClassifier.set("universal")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    doFirst {
        println(configurations.get().map { it.files })
    }

    manifest {
        attributes(
            // MCF compat
            "MixinConfigs" to listOf(
                "remoteresourcepack.mixins.json",
                "neo-migration.mixins.json",
            ).joinToString(separator = ",")
        )
    }
}

tasks.register("shadowSourcesJar", ShadowJar::class) {
    configurations.set(subprojectArchives("sourcesJar"))
    archiveClassifier.set("universal-sources")
}

tasks.build {
    dependsOn("shadowJar")
    dependsOn("shadowSourcesJar")
}

providers.environmentVariable("MR_TOKEN").takeIf { it.isPresent }?.also { mrToken ->
    apply(plugin = "com.modrinth.minotaur")
    extensions.configure<com.modrinth.minotaur.ModrinthExtension>("modrinth") {
        loaders.set(providers.gradleProperty("mr_loaders").map { it.split(',') })
        token.set(mrToken)
        projectId.set(providers.gradleProperty("mr_project_id"))
        versionNumber.set("${project.version}-universal")
        versionName.set(provider {
            providers.gradleProperty("mr_version_name_format").get().format(
                Locale.ENGLISH,
                providers.gradleProperty("mr_version_game_range").get(),
                "Universal",    // loader display
                providers.gradleProperty("mr_version_mod_abbr").get(),
                providers.gradleProperty("mr_version_display").get()
            )
        })
        changelog.set(providers.gradleProperty("mr_version_changelog"))
        versionType.set(providers.gradleProperty("mr_version_type"))
        gameVersions.set(providers.gradleProperty("mr_version_game").map {
            it.split(',')
        })
        detectLoaders.set(false)
        autoAddDependsOn.set(false)

        uploadFile.set(tasks["shadowJar"])
        additionalFiles.add(tasks["shadowSourcesJar"])

        debugMode = providers.environmentVariable("MR_DEBUG_MODE").map { "1" == it }.orElse(false)
    }
}