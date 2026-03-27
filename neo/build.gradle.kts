plugins {
    id("net.neoforged.moddev") version "2.0.140"
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
    val mcfVersion = "26.1-62.0.3"
    compileOnly("net.minecraftforge:javafmllanguage:$mcfVersion") {
        isTransitive = true
    }
    compileOnly("net.minecraftforge:fmlloader:$mcfVersion") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:fmlcore:$mcfVersion") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:eventbus:7.0-beta.12") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:forgespi:7.1.5") {
        isTransitive = false
    }
    compileOnly("net.minecraftforge:mergetool-api:1.0")
}
