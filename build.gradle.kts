plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

loom {
    mods {
        register("cesium") {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // Native compression — shaded into final jar
    include(implementation("com.github.luben:zstd-jni:${project.property("zstd_version")}")!!)
    include(implementation("org.lz4:lz4-java:${project.property("lz4_version")}")!!)

    // Native Netty transports — platform-specific classifiers
    include(implementation("io.netty:netty-transport-native-epoll:${project.property("netty_version")}:linux-x86_64")!!)
    include(implementation("io.netty:netty-transport-native-epoll:${project.property("netty_version")}:linux-aarch_64")!!)
    include(implementation("io.netty:netty-transport-native-kqueue:${project.property("netty_version")}:osx-x86_64")!!)
    include(implementation("io.netty:netty-transport-native-kqueue:${project.property("netty_version")}:osx-aarch_64")!!)

    // Optional: Mod Menu config screen
    modCompileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))

    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version")
        )
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
    repositories {
        // Define remote repositories here if needed
    }
}
