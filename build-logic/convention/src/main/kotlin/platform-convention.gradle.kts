plugins {
    java
    id("com.modrinth.minotaur") apply false
}

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
