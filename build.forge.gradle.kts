import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.apache.tools.ant.filters.BaseFilterReader
import java.io.Reader
import java.io.StringReader
import java.time.Instant

plugins {
    id("net.minecraftforge.gradle") version "7.+"
    id("net.minecraftforge.renamer") version "1.+"
    id("forge-mutex")
    id("platform-convention")
}

base.archivesName = "${property("mod.id") as String}-forge"
version = "${property("mod.version")}+${sc.current.version}"

repositories {
    minecraft.mavenizer(this)
    mavenCentral()
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
}

minecraft {
    mappings("official", sc.current.version)

    runs {
        val modId = sc.properties["mod.id"] as String
        configureEach {
            workingDir = rootProject.file("run")
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            mods.create(modId) {
                source(sourceSets["main"])
            }
        }

        listOf("client", "server", "gameTestServer").forEach { runName ->
            register(runName) {
                systemProperty("forge.enabledGameTestNamespaces", modId)
                if ("server" == runName) {
                    args("--nogui")
                }
            }
        }
    }
}

val is1205OrLater = sc.current.parsed >= "1.20.5"

dependencies {
    val mavenizer = minecraft.dependency("net.minecraftforge:forge:${property("deps.forge")}")
    implementation(mavenizer)
    runCatching { mavenizer.toSrgFile.get() }.map { f ->
        renamer.setMappings(files(f))
    }

    if (!is1205OrLater) annotationProcessor("org.spongepowered:mixin:0.8.7:processor")
}

val mixinConfig = "${sc.properties["mod.id"] as String}.mixins.json"

minecraft.runs.configureEach {
    args("--mixin.config", mixinConfig)
}

if (!is1205OrLater) {
    val refMapName = "${sc.properties["mod.id"] as String}.refmap.json"
    renamer.enableMixinRefmaps {
        config(mixinConfig)
        source(sourceSets["main"]) {
            refMap = refMapName
        }
        jar(tasks.jar)
    }

    tasks.processResources {
        filesMatching(mixinConfig) {
            open class RefMapAdder(reader: Reader): BaseFilterReader(reader) {
                private lateinit var transformedContent: StringReader
                var refMapName: String = "UNKNOWN"

                private fun ensureInitialized() {
                    if (!initialized) {
                        val legacyContent = this.readFully().orEmpty()
                        transformedContent = GsonBuilder().setPrettyPrinting().create().let { gson ->
                            val obj = gson.fromJson(legacyContent, JsonObject::class.java)
                            obj.addProperty("refmap", this.refMapName)

                            StringReader(gson.toJson(obj))
                        }
                        initialized = true
                    }
                }

                override fun read(): Int {
                    ensureInitialized()
                    return transformedContent.read()
                }
            }
            filter<RefMapAdder>("refMapName" to refMapName)
        }
    }
}

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
//    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
//    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
//    else -> JavaVersion.VERSION_1_8
    else -> JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.ordinal + 1
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
        }

        filesMatching("META-INF/mods.toml") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "*.ct", "*.classtweaker")
    }

    jar {
        manifest {
            attributes(
                "Specification-Title" to project.property("mod.id") as String,
                "Specification-Vendor" to "teddyxlandlee",
                "Specification-Version" to "1",
                "Implementation-Title" to rootProject.name,
                "Implementation-Version" to sc.properties["mod.version"] as String,
                "Implementation-Vendor" to "teddyxlandlee",
                "Implementation-Timestamp" to Instant.now(),
            )

            if (is1205OrLater) {
                // manually add mixin config
                attributes("MixinConfigs" to mixinConfig)
            }
        }
        if (!is1205OrLater) {
            archiveClassifier = "dev"
        }
    }

    val modJar = if (is1205OrLater) {
        jar.flatMap { it.archiveFile }
    } else {
        renamer.classes(jar) {
            archiveClassifier = null as String?
            mappings(renamer.mixin.generatedMappings)
        }.flatMap { it.output }
    }

    // Everybody uses official mappings so the source mapping does not matter
    val modSourcesJar = getByName<AbstractArchiveTask>("sourcesJar").archiveFile

    destArtifacts {
        binaryJar = modJar
        sourcesJar = modSourcesJar

        versionInfo.display.set(sc.properties["mod.mc_releases_display"] as String)
        versionInfo.range.set(sc.properties.raw("mod", "mc_releases").asList().map(Any?::toString))
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies result to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(modJar, modSourcesJar)
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}

