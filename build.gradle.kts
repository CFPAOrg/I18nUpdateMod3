plugins {
    id("com.github.johnrengelman.shadow") version ("7.1.2")
    id("java")
}

group = "i18nupdatemod"
version = "3.2.0"

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
            mapOf(
                "TweakClass" to "i18nupdatemod.launchwrapper.LaunchWrapperTweaker",
                "TweakOrder" to -10
            )
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