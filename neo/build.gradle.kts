plugins {
    id("net.neoforged.moddev") version "2.0.134"
}

neoForge {
    version = rootProject.ext["neoforge_version"].toString()
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching(listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
        expand("version" to project.version)
    }
}

repositories {
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.minecraftforge.net")
}

dependencies {
    api(project(":common"))

    // Introduce Forge stubs
    val mcfVersion = "1.21.11-61.0.3"
    compileOnly("net.minecraftforge:javafmllanguage:$mcfVersion") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:fmlloader:$mcfVersion") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:fmlcore:$mcfVersion") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:mergetool-api:1.0")
}
