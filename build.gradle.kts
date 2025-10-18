plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.modrinth.minotaur") version "2.8.4"
    id("io.github.CDAGaming.cursegradle") version "1.6.1"
}

group = "i18nupdatemod"
version = project.properties["version"].toString() + if ("false" == System.getenv("IS_SNAPSHOT")) "" else "-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    implementation("net.runelite.archive-patcher:archive-patcher-applier:1.2")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("net.fabricmc:fabric-loader:0.15.9")
    implementation("cpw.mods:modlauncher:8.1.3")
    implementation("net.minecraft:launchwrapper:1.12")

    implementation("commons-io:commons-io:2.16.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("com.google.code.gson:gson:2.11.0")

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

val supportMinecraftVersions = project.properties["minecraft"].toString().split(",")

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("PWERr14M")
    versionNumber.set("${project.version}")
    versionName.set("I18nUpdateMod ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks["shadowJar"])
    gameVersions.set(supportMinecraftVersions)
    loaders.set(listOf("fabric", "forge", "neoforge", "quilt"))
    syncBodyFrom.set(rootProject.file("README.md").readText())
    changelog.set(System.getenv("CHANGE_LOG"))
}

val curseForgeSpecialVersions = project.properties["curseforge"].toString().split(",")

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
        changelog = if (System.getenv("CHANGE_LOG") != null) System.getenv("CHANGE_LOG") else "No change log"
    }
}