plugins {
    id("net.neoforged.moddev") version "2.0.134"
}

neoForge {
    neoFormVersion = rootProject.ext["neoform_version"].toString()
}

dependencies {
    compileOnly("xland.mcmodbridge:fabric-distmarker:0.1.0")

    implementation("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-common:0.5.0")
}
