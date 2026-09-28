plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
}

val compatibilityJar = providers.gradleProperty("compatibilityJar").orNull

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
    if (compatibilityJar != null) {
        runtimeOnly(files(compatibilityJar))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

if (compatibilityJar != null) {
    tasks.named<JavaCompile>("compileJava") {
        enabled = false
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set(
        "${project.property("archives_base_name")}-${project.property("mod_version")}+mc26.1-26.3.jar"
    )
    from("LICENSE") {
        rename { "LICENSE_${project.property("archives_base_name")}" }
    }
}

tasks.register("buildFabric") {
    group = "build"
    description = "Builds the official Fabric WorldBinder jar. Fabric is the only supported loader for this release."
    dependsOn("build")
}

val clientTest = sourceSets.create("clientTest") {
    if (compatibilityJar == null) {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    } else {
        compileClasspath += files(compatibilityJar) + sourceSets.main.get().compileClasspath
        runtimeClasspath += files(compatibilityJar) + files(sourceSets.main.get().runtimeClasspath.filter {
            it !in sourceSets.main.get().output.files
        })
    }
}

loom {
    mods {
        if (compatibilityJar == null) {
            register("worldbinder") { sourceSet(sourceSets.main.get()) }
        }
        register("worldbinder_test") { sourceSet(clientTest) }
    }
    runs {
        register("clientTest") {
            client()
            source(clientTest)
            runDir("build/client-test")
            vmArg("-Dfabric.client.gametest")
            if (compatibilityJar != null) {
                vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
            }
        }
    }
}
