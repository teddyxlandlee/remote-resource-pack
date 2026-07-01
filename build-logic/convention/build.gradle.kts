plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.architectury.dev")
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.minecraftforge.net")
    maven("https://maven.fabricmc.net")
}

dependencies {
    implementation("com.modrinth.minotaur:Minotaur:2.+")
}
