import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.modrinth.minotaur") version "2.8.4"
    id("io.github.CDAGaming.cursegradle") version "1.6.1"
}

group = "i18nupdatemod"
version = project.properties["version"].toString() + if ("false" == System.getenv("IS_SNAPSHOT")) "" else "-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}
fun ShadowJar.configureI18nPackaging() {
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
    relocate("org.tukaani.xz", "include.org.tukaani.xz")
    dependencies {
        include(dependency("net.runelite.archive-patcher:archive-patcher-applier:.*"))
        include(dependency("org.tukaani:xz:.*"))
    }
    exclude("LICENSE")
}

tasks.shadowJar {
    configureI18nPackaging()
}

tasks.register<ShadowJar>("shadowJarDebug") {
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    archiveClassifier.set("debug")
    configureI18nPackaging()
}

mapOf(
    "Release" to "http://downloader1.meitangdehulu.com:22943/",
    "Debug" to "https://i18dl.imc.wiki/",
).forEach { (variant, baseUrl) ->
    val bmclBaseUrl = if (variant == "Release") {
        "https://bmclapi2.bangbang93.com/mirrors/i18n-update-mod/"
    } else ""
    val configFile = layout.buildDirectory.file("generated/buildConfig/$variant/i18n-build.properties")
    val generateConfig = tasks.register("generate${variant}Config") {
        inputs.property("assetBaseUrl", baseUrl)
        inputs.property("bmclBaseUrl", bmclBaseUrl)
        outputs.file(configFile)
        doLast {
            configFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText("assetBaseUrl=$baseUrl\nbmclBaseUrl=$bmclBaseUrl\n")
            }
        }
    }
    val archive = tasks.named<ShadowJar>(if (variant == "Release") "shadowJar" else "shadowJarDebug") {
        from(generateConfig)
    }
    tasks.register("build$variant") {
        group = "build"
        description = "Build the ${variant.lowercase()} artifact."
        dependsOn(archive)
    }
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://files.minecraftforge.net/maven")
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.runelite.net/")
}

configurations.configureEach {
    isTransitive = false
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    // Transitive resolution is disabled, including for the test configurations.
    testImplementation("org.apiguardian:apiguardian-api:1.1.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("org.junit.platform:junit-platform-commons:1.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-engine:1.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
    implementation("net.runelite.archive-patcher:archive-patcher-applier:1.2")
    implementation("org.tukaani:xz:1.10")
    // Forge 1.13.x provides NightConfig 3.6.0; keep that API baseline without bundling it.
    compileOnly("com.electronwill.night-config:core:3.6.0")
    compileOnly("com.electronwill.night-config:toml:3.6.0")
    testRuntimeOnly("com.electronwill.night-config:core:3.6.0")
    testRuntimeOnly("com.electronwill.night-config:toml:3.6.0")
    compileOnly("org.jetbrains:annotations:24.1.0")
    // Only the early-service interface is linked; never bundle loader implementation classes.
    compileOnly("net.neoforged.fancymodloader:loader:10.0.34") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }

    implementation("net.fabricmc:fabric-loader:0.15.9")
    implementation("cpw.mods:modlauncher:8.1.3")
    implementation("net.minecraft:launchwrapper:1.12")

    implementation("commons-io:commons-io:2.16.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    // Minecraft supplies Gson at runtime; compile against the 1.7.10 API baseline.
    implementation("com.google.code.gson:gson:2.2.4")

}
tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    exclude("i18n-build.properties")
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