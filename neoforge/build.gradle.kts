import java.time.Duration
import groovy.json.JsonSlurper

plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
}

version = rootProject.property("mod_version") as String
group = rootProject.property("maven_group") as String
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)); withSourcesJar() }
base { archivesName.set("worldbinder-neoforge") }

sourceSets.main {
    java.srcDir("../src/main/java")
    java.exclude("net/worldbinder/integration/**")
    resources.srcDir("../src/main/resources")
    resources.exclude("fabric.mod.json")
}

val compatibilityNeoJar=providers.gradleProperty("compatibilityNeoJar").orNull?.let { rootProject.file(it).absolutePath }
val runtimeTest = sourceSets.create("runtimeTest") {
    java.srcDir("../src/sharedTest/java")
    resources.srcDir("../src/sharedTest/resources")
    if(compatibilityNeoJar==null){
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }else{
        compileClasspath+=files(compatibilityNeoJar)
        runtimeClasspath+=files(compatibilityNeoJar)
    }
}
if(compatibilityNeoJar!=null)dependencies{add(runtimeTest.runtimeOnlyConfigurationName,files(compatibilityNeoJar))}
val prepareSmoke = tasks.register("prepareSmokeFixture") {
    dependsOn(rootProject.tasks.named("regressionTest"))
    doLast {
        val runs = rootProject.layout.buildDirectory.dir("regression-runtime").get().asFile.listFiles()!!.filter { it.isDirectory && it.resolve("RESULT.txt").isFile && it.resolve("RESULT.txt").readText().startsWith("PASS") && it.resolve("export-26.3/level.dat").isFile }
        val source = runs.maxBy { it.lastModified() }.resolve("export-26.3")
        val fixtureName = "loader_fixture_" + System.currentTimeMillis()
        val target = rootProject.layout.buildDirectory.dir("neoforge-test/saves/$fixtureName").get().asFile
        copy { from(source); into(target) }
        rootProject.layout.buildDirectory.file("neoforge-test/fixture-name.txt").get().asFile.writeText(fixtureName)
        rootProject.layout.buildDirectory.file("neoforge-test/RESULT.txt").get().asFile.delete()
    }
}

val prepareExpansion = tasks.register("prepareExpansionFixture") {
    dependsOn(rootProject.tasks.named("regressionTest"))
    doLast {
        val runs=rootProject.layout.buildDirectory.dir("regression-runtime").get().asFile.listFiles()!!.filter { it.isDirectory && it.resolve("RESULT.txt").isFile && it.resolve("RESULT.txt").readText().startsWith("PASS") && it.resolve("export-26.3/level.dat").isFile }
        val source=runs.maxBy { it.lastModified() }.resolve("export-26.3")
        val fixtureName="expansion_fixture_"+System.currentTimeMillis()
        val target=rootProject.layout.buildDirectory.dir("neoforge-expansion/saves/$fixtureName").get().asFile
        copy { from(source);into(target) }
        rootProject.layout.buildDirectory.file("neoforge-expansion/fixture-name.txt").get().asFile.writeText(fixtureName)
    }
}

neoForge {
    version = "26.3.0.95"
    addModdingDependenciesTo(runtimeTest)
    mods {
        if(compatibilityNeoJar==null)register("worldbinder") { sourceSet(sourceSets.main.get()) }
        register("worldbinder_qa") { sourceSet(runtimeTest) }
    }
    runs {
        for(phase in listOf("record","resume"))register(if(phase=="record")"proxyNetworkTest" else "proxyNetworkRestartTest"){
            client();sourceSet=runtimeTest;gameDirectory.set(rootProject.file(providers.gradleProperty("proxyNeoGameDir").orElse("build/neoforge-proxy-client").get()))
            systemProperty("worldbinder.proxyPhase",phase)
            if(compatibilityNeoJar!=null)systemProperty("worldbinder.compatibilityNeoJar",compatibilityNeoJar)
            systemProperty("worldbinder.qaProxyControl",rootProject.layout.buildDirectory.file("proxy-network-runtime/control.json").get().asFile.absolutePath)
            programArguments.addAll("--username","WBNeoProxyQA","--width","1920","--height","1080")
        }
        for(phase in listOf("record","resume")) register(if(phase=="record") "expansionTest" else "expansionRestartTest"){
            client();sourceSet=runtimeTest;gameDirectory.set(file("../build/neoforge-expansion"))
            systemProperty("worldbinder.expansionPhase",phase)
            if(compatibilityNeoJar!=null)systemProperty("worldbinder.compatibilityNeoJar",compatibilityNeoJar)
            programArguments.addAll("--width","1920","--height","1080")
            if(phase=="record")taskBefore(prepareExpansion)
        }
        register("multiplayerSmokeTest") {
            client(); sourceSet = runtimeTest
            gameDirectory.set(file("../build/neoforge-server-test"))
            systemProperty("worldbinder.multiplayerOnly", "true")
            systemProperty("worldbinder.qaControl", rootProject.layout.buildDirectory.file("dedicated-server-runtime/control.json").get().asFile.absolutePath)
        }
        register("client") {
            client(); gameDirectory.set(file("../build/neoforge-client"))
            if(compatibilityNeoJar==null)loadedMods.set(setOf(mods.getByName("worldbinder")))
        }
        register("smokeTest") {
            client()
            sourceSet = runtimeTest
            gameDirectory.set(file("../build/neoforge-test"))
            taskBefore(prepareSmoke)
        }
    }
}

tasks.named("runProxyNetworkRestartTest"){mustRunAfter("runProxyNetworkTest")}

tasks.named("runMultiplayerSmokeTest") {
    timeout.set(Duration.ofMinutes(4))
    doFirst { rootProject.layout.buildDirectory.file("neoforge-server-test/RESULT.txt").get().asFile.delete() }
    doLast {
        val result=rootProject.layout.buildDirectory.file("neoforge-server-test/RESULT.txt").get().asFile
        check(result.exists()&&result.readText().startsWith("PASS")){"NeoForge multiplayer test failed"}
    }
}

tasks.named("runSmokeTest") {
    timeout.set(Duration.ofMinutes(5))
    doFirst { rootProject.layout.buildDirectory.file("neoforge-test/RESULT.txt").get().asFile.delete() }
    doLast {
        val result = rootProject.layout.buildDirectory.file("neoforge-test/RESULT.txt").get().asFile
        check(result.exists() && result.readText().startsWith("PASS")) { "NeoForge smoke test did not pass; see build/neoforge-test/logs/latest.log" }
    }
}

for(phase in listOf("record","resume")) tasks.named(if(phase=="record")"runExpansionTest" else "runExpansionRestartTest"){
    timeout.set(Duration.ofMinutes(5))
    if(phase=="resume")mustRunAfter("runExpansionTest")
    doFirst{rootProject.layout.buildDirectory.file("neoforge-expansion/expansion-$phase-RESULT.txt").get().asFile.delete()}
    doLast{
        val result=rootProject.layout.buildDirectory.file("neoforge-expansion/expansion-$phase-RESULT.txt").get().asFile
        check(result.exists()&&result.readText().startsWith("PASS")){"NeoForge expansion $phase did not pass; inspect the native log"}
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25) }
tasks.processResources {
    inputs.property("version", project.version)
    val languages = listOf("de_de", "en_us").map { rootProject.file("src/main/resources/assets/worldbinder/lang/$it.json") }
    inputs.files(languages)
    filesMatching("META-INF/neoforge.mods.toml") {
        val description = languages.joinToString("\n\n") {
            (JsonSlurper().parse(it) as Map<*, *>)["modmenu.descriptionTranslation.worldbinder"] as String
        }
        expand("version" to project.version, "description" to description)
    }
}

tasks.named("check") { dependsOn(rootProject.tasks.named("checkLanguages")) }
tasks.jar {
    archiveFileName.set("worldbinder-${project.version}+mc26.3-neoforge.jar")
    from("../LICENSE")
}
