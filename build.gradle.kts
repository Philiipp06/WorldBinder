import groovy.json.JsonSlurper

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set("${project.property("archives_base_name")}-fabric")
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
}

val compatibilityJar = providers.gradleProperty("compatibilityJar").orNull

sourceSets.main { java.srcDir("src/fabric/java") }

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
    val languages = listOf("de_de", "en_us").map { file("src/main/resources/assets/worldbinder/lang/$it.json") }
    inputs.files(languages)
    filesMatching("fabric.mod.json") {
        val description = languages.joinToString("\n\n") {
            (JsonSlurper().parse(it) as Map<*, *>)["modmenu.descriptionTranslation.worldbinder"] as String
        }
        expand("version" to project.version, "description" to description.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"))
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set(
        "${project.property("archives_base_name")}-${project.property("mod_version")}+mc26.1-26.3-fabric.jar"
    )
    from("LICENSE") {
        rename { "LICENSE_${project.property("archives_base_name")}" }
    }
}

tasks.register("buildNeoForge") {
    group = "build"
    dependsOn(":neoforge:build")
}

tasks.register("buildFabric") {
    group = "build"
    description = "Builds the Fabric WorldBinder jar."
    dependsOn("build")
}

val clientTest = sourceSets.create("clientTest") {
    java.srcDir("src/sharedTest/java")
    resources.srcDir("src/sharedTest/resources")
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

val regressionTest = sourceSets.create("regressionTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

if(providers.gradleProperty("schematicImports").orNull=="true"){
    clientTest.runtimeClasspath+=files(fileTree("build/qa-import/mods"){include("*.jar")})
}
providers.gradleProperty("replayModJar").orNull?.let { clientTest.runtimeClasspath+=files(it) }
providers.gradleProperty("networkModJar").orNull?.let { clientTest.runtimeClasspath+=files(it) }
tasks.register<JavaExec>("regressionTest") {
    group = "verification"
    description = "Runs archive, update, export read-back and failure regression checks using Minecraft classes (no in-game claim)."
    classpath = regressionTest.runtimeClasspath
    mainClass.set("net.worldbinder.capture.ProjectRegressionTest")
    workingDir(layout.buildDirectory.dir("regression-runtime"))
    doFirst { workingDir.mkdirs() }
    jvmArgs("-Xmx2G", "-Djava.awt.headless=true")
}

tasks.register<JavaExec>("legacyVisualFixtures") {
    group = "verification"
    description = "Prepares older target worlds from an actual recorded protocol fixture, using the frozen production JAR."
    classpath = regressionTest.output + files(compatibilityJar ?: emptyList<String>()) + sourceSets.main.get().compileClasspath
    mainClass.set("net.worldbinder.capture.LegacyVisualFixtures")
    workingDir(layout.buildDirectory.dir("legacy-visual-exports"))
    doFirst { check(compatibilityJar != null) { "Use -PcompatibilityJar with a frozen production artifact" }; workingDir.mkdirs() }
    args(providers.gradleProperty("protocolFixture").map { file(it).absolutePath }.orElse(layout.buildDirectory.file("proxy-client/protocol-resume-target.json").map { it.asFile.absolutePath }).get())
    args(providers.gradleProperty("protocolNeoFixture").map { file(it).absolutePath }.orElse(layout.buildDirectory.file("neoforge-proxy-client/protocol-resume-target.json").map { it.asFile.absolutePath }).get())
    if(providers.gradleProperty("legacyVisualDisplay").orNull=="true")args("--night-survival")
    jvmArgs("-Xmx3G", "-Djava.awt.headless=true")
}

for (phase in listOf("capture", "resume")) tasks.register<JavaExec>(if (phase == "capture") "largeProjectTest" else "largeProjectRestartTest") {
    group = "verification"
    classpath = regressionTest.runtimeClasspath
    mainClass.set("net.worldbinder.capture.LargeProjectRegressionTest")
    workingDir(layout.buildDirectory.dir("large-project-runtime"))
    doFirst { workingDir.mkdirs() }
    args(phase)
    jvmArgs("-Xmx4G", "-Djava.awt.headless=true")
}

tasks.register<JavaExec>("exportPerformanceTest") {
    group = "verification"
    description = "Measures two full synthetic archive/world/read-back exports; no in-game timing claim."
    classpath = regressionTest.runtimeClasspath
    mainClass.set("net.worldbinder.capture.ExportPerformanceTest")
    workingDir(layout.buildDirectory.dir("export-performance-runtime"))
    doFirst { workingDir.mkdirs() }
    args(providers.gradleProperty("benchmarkChunks").orElse("512").get())
    jvmArgs("-Xmx4G", "-Djava.awt.headless=true")
    providers.gradleProperty("exportJfr").orNull?.let {
        jvmArgs("-XX:StartFlightRecording=filename=${file(it).absolutePath},settings=profile,dumponexit=true")
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
        for(phase in listOf("record","resume"))register(if(phase=="record")"proxyNetworkTest" else "proxyNetworkRestartTest"){
            client();source(clientTest);runDir(providers.gradleProperty("proxyGameDir").orElse("build/proxy-client").get())
            vmArg("-Dworldbinder.proxyPhase=$phase");vmArg("-Dworldbinder.qaProxyControl=${layout.buildDirectory.file("proxy-network-runtime/control.json").get().asFile.absolutePath}")
            if(compatibilityJar!=null)vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
            programArg("--username");programArg("WBProxyQA");programArg("--width");programArg("1920");programArg("--height");programArg("1080")
        }
        for(phase in listOf("record","resume"))register(if(phase=="record")"replayCaptureTest" else "replayCaptureRestartTest"){
            client();source(clientTest);runDir("build/replay-client-${providers.gradleProperty("replayProvider").orElse("flashback").get()}-${project.property("minecraft_version")}")
            vmArg("-Dworldbinder.replayProvider=${providers.gradleProperty("replayProvider").orElse("flashback").get()}");vmArg("-Dworldbinder.replayPhase=$phase")
            programArg("--width");programArg("1920");programArg("--height");programArg("1080")
            if(compatibilityJar!=null)vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
        }
        register("schematicImportTest"){
            client();source(clientTest);runDir("build/import-client")
            vmArg("-Dfabric.client.gametest");vmArg("-Dworldbinder.schematicImports=true")
            vmArg("-Dworldbinder.importFixture=${layout.buildDirectory.file("expansion-client/expansion-fixture.json").get().asFile.absolutePath}")
            if(compatibilityJar!=null)vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
        }
        for (phase in listOf("record", "resume")) register(if(phase=="record") "expansionTest" else "expansionRestartTest") {
            client();source(clientTest);runDir("build/expansion-client")
            vmArg("-Dfabric.client.gametest");vmArg("-Dworldbinder.expansionPhase=$phase")
            programArg("--width");programArg("1920");programArg("--height");programArg("1080")
            if(compatibilityJar!=null)vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
        }
        for (phase in listOf("record", "resume")) register(if (phase == "record") "serverRecordTest" else "serverResumeTest") {
            client(); source(clientTest); runDir("build/dedicated-client")
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dworldbinder.serverTestPhase=$phase")
            if(compatibilityJar!=null)vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
            vmArg("-Dworldbinder.qaControl=${layout.buildDirectory.file("dedicated-server-runtime/control.json").get().asFile.absolutePath}")
            programArg("--username"); programArg("WorldBinderQA")
        }
        register("projectRestartTest") {
            client()
            source(clientTest)
            runDir("build/project-client")
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dworldbinder.projectRestartOnly=true")
            if (compatibilityJar != null) vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
        }
        register("projectTest") {
            client()
            source(clientTest)
            runDir("build/project-client")
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dworldbinder.projectOnly=true")
            if (compatibilityJar != null) vmArg("-Dworldbinder.compatibilityJar=${file(compatibilityJar).name}")
        }
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

tasks.named("runExpansionRestartTest"){mustRunAfter("runExpansionTest")}
tasks.named("runReplayCaptureRestartTest"){mustRunAfter("runReplayCaptureTest")}
tasks.named("runProxyNetworkRestartTest"){mustRunAfter("runProxyNetworkTest")}
tasks.register<Exec>("checkLanguages"){
    group="verification";commandLine("node","scripts/qa/check-language.mjs")
    dependsOn("testLanguageAudit")
}

tasks.register<Exec>("testLanguageAudit") {
    group="verification";commandLine("node","--test","scripts/qa/language-audit.test.mjs")
}
tasks.named("check"){dependsOn("checkLanguages")}
tasks.register("writeQaCompileClasspath"){
    group="verification"
    doLast{val output=layout.buildDirectory.file("qa-import/minecraft-classpath.txt").get().asFile;output.parentFile.mkdirs();output.writeText(sourceSets.main.get().compileClasspath.asPath)}
}
