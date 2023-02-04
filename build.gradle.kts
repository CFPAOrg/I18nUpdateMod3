plugins {
    id("com.github.johnrengelman.shadow") version ("7.1.2")
    id("java")
    id("com.modrinth.minotaur") version "2.6.0"
    id("io.github.CDAGaming.cursegradle") version "1.6.0"
}

group = "i18nupdatemod"
version = "3.4.1" + if ("false" == System.getenv("IS_SNAPSHOT")) "" else "-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    disableAutoTargetJvm()
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.shadowJar {
    manifest {
        attributes(
            "TweakClass" to "i18nupdatemod.launchwrapper.LaunchWrapperTweaker",
            "TweakOrder" to 33,
            "Automatic-Module-Name" to "i18nupdatemod",
        )
    }
    minimize()
    archiveBaseName.set("I18nUpdateMod")
    relocate("com.google.archivepatcher", "include.com.google.archivepatcher")
    dependencies {
        include(dependency("net.runelite.archive-patcher:archive-patcher-applier:.*"))
    }
    exclude("LICENSE")
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://files.minecraftforge.net/maven")
    maven("https://repo.runelite.net/")
}

configurations.configureEach {
    isTransitive = false
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")

    implementation("net.fabricmc:fabric-loader:0.14.12")
    implementation("cpw.mods:modlauncher:8.1.3")
    implementation("net.minecraft:launchwrapper:1.12")

    implementation("commons-io:commons-io:2.11.0")
    implementation("net.runelite.archive-patcher:archive-patcher-applier:1.2")
    implementation("org.apache.logging.log4j:log4j-api:2.19.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.ow2.asm:asm:9.4")
    implementation("org.jetbrains:annotations:24.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("**") {
        expand(
            "version" to project.version,
        )
    }
}

val supportMinecraftVersions = listOf(
    "1.6.1", "1.6.2", "1.6.4", "1.7.2", "1.7.10", "1.8", "1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11",
    "1.11.2", "1.12", "1.12.1", "1.12.2", "1.13.2", "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4", "1.15", "1.15.1",
    "1.15.2", "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5", "1.17", "1.17.1", "1.18", "1.18.1", "1.18.2",
    "1.19", "1.19.1", "1.19.2", "1.19.3"
)

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("PWERr14M")
    versionNumber.set("${project.version}")
    versionName.set("I18nUpdateMod ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks["shadowJar"])
    gameVersions.set(supportMinecraftVersions)
    loaders.set(listOf("fabric", "forge", "quilt"))
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

val curseForgeSpecialVersions = listOf(
    "Forge", "Fabric", "Quilt", "Client", "Java 8", "Java 9", "Java 10", "Java 11", "Java 12", "Java 13", "Java 14",
    "Java 15", "Java 16", "Java 17", "Java 18", "1.14-Snapshot", "1.15-Snapshot", "1.16-Snapshot", "1.17-Snapshot",
    "1.18-Snapshot", "1.19-Snapshot"
)

curseforge {
    apiKey = if (System.getenv("CURSE_TOKEN") != null) System.getenv("CURSE_TOKEN") else "dummy"
    project {
        id = "297404"
        releaseType = "release"
        mainArtifact(tasks["shadowJar"]) {
            this.displayName = "I18nUpdateMod ${project.version}"
        }
        gameVersionStrings.addAll(supportMinecraftVersions)
        gameVersionStrings.addAll(curseForgeSpecialVersions)
    }
}