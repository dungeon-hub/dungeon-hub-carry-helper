import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("dh-carry-helper") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    mavenLocal()

    maven ("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")

    maven("https://maven.teamresourceful.com/repository/maven-public/") {
        name = "Team Resourceful Maven"
    }

    maven("https://releases-repo.kordex.dev") {
        name = "KordEx Releases Repository"
        content {
            includeGroup("com.kotlindiscord")
            includeGroup("dev.kordex")
        }
    }

    maven("https://snapshots-repo.kordex.dev") {
        name = "KordEx Snapshot Repository"
        content {
            includeGroup("com.kotlindiscord")
            includeGroup("dev.kordex")
        }
    }

    maven("https://repo.kordex.dev/mirror") {
        name = "Kord Mirror Repository"
        content {
            includeGroup("dev.kord")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    implementation("com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-26.1:4.0.1")

    api("net.dungeon-hub.api:client:0.7.0")
    include("net.dungeon-hub.api:client:0.7.0")
    api("io.ktor:ktor-client-java:3.0.0")
    include("io.ktor:ktor-client-java:3.0.0")
    api("dev.kord:kord-core:0.19.0-SNAPSHOT")
    include("dev.kord:kord-core:0.19.0-SNAPSHOT")
    api("dev.kordex:kord-extensions:2.4.1-SNAPSHOT")
    include("dev.kordex:kord-extensions:2.4.1-SNAPSHOT")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    include("com.squareup.okhttp3:okhttp:4.12.0")

    api("com.auth0:java-jwt:4.5.2")
    include("com.auth0:java-jwt:4.5.2")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version")!!,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
