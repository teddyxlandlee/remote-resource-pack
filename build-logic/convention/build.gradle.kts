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
    maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
}

dependencies {
    implementation("com.modrinth.minotaur:Minotaur:2.+")
    implementation("dev.kikugie:Stonecutter:latest.release")
}
