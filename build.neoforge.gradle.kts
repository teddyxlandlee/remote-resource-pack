plugins {
    id("net.neoforged.moddev") version "2.0.140"
    id("forge-mutex")
    id("platform-convention")
}

base.archivesName = "${property("mod.id") as String}-neoforge"
version = "${property("mod.version")}+${sc.current.version}"

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register("${property("mod.id")}") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = file("../../run/")
            client()
        }

        register("server") {
            gameDirectory = file("../../run/")
            server()
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

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json", "META-INF/mods.toml", "*.ct", "*.classtweaker", "pack.mcmeta")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    val modJar = jar.flatMap { it.archiveFile }
    val modSourcesJar = named<Jar>("sourcesJar").flatMap { it.archiveFile }

    destArtifacts {
        binaryJar = modJar
        sourcesJar = modSourcesJar

        versionInfo.display.set(sc.properties["mod.mc_releases_display"] as String)
        versionInfo.range.set(sc.properties.raw("mod", "mc_releases").asList().map(Any?::toString))
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(modJar, modSourcesJar)
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
