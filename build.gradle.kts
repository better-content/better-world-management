plugins {
    idea
    `maven-publish`
    jacoco
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.spongepowered.mixin") version "0.7.38"
}

mixin {
    add(sourceSets.main.get(), "better_world_management.refmap.json")
    config("better_world_management.mixins.json")
    config("better_world_management.recipes.mixins.json")
}

group = property("mod_group_id") as String
version = property("mod_version") as String

base { archivesName.set("better-world-management") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }

val visualHarness by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output
}
configurations[visualHarness.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[visualHarness.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

// Non-shipping feasibility fixtures; never included in the production JAR.
val feasibilityHarness by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output
}
configurations[feasibilityHarness.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[feasibilityHarness.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "debug")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            mods { create(property("mod_id") as String) { source(sourceSets.main.get()) } }
        }
        val baseClient = create("client")
        val baseServer = create("server") { arg("--nogui") }
        create("gameTestServer") {
            workingDirectory(project.file("run-gametest"))
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            arg("--nogui")
        }
        create("feasibilityServer") {
            workingDirectory(project.file("run-feasibility"))
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", "better_world_management,better_world_management_feasibility")
            property("world_axioms.campaign", providers.gradleProperty("axiomCampaign").getOrElse("capacity"))
            arg("--nogui")
            mods {
                create("better_world_management_feasibility") { source(feasibilityHarness) }
            }
        }
        create("visualServer") {
            parent(baseServer)
            workingDirectory(project.file("run-visual-server"))
            arg("--nogui")
            mods {
                create("better_world_management_visual_harness") { source(visualHarness) }
            }
        }
        create("visualClient") {
            parent(baseClient)
            workingDirectory(project.file("run-visual-client"))
            if (providers.gradleProperty("axiomVisualStandalone").map(String::toBoolean).getOrElse(false)) {
                args("--width", "1920", "--height", "1080")
            } else {
                args("--quickPlayMultiplayer", "127.0.0.1:25565", "--width", "1280", "--height", "720")
            }
            mods {
                create("better_world_management_visual_harness") { source(visualHarness) }
            }
        }
    }
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://libraries.minecraft.net")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://maven.tterrag.com/")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
    mavenCentral()
    ivy {
        name = "worldAxiomIsolatedApis"
        url = uri("../../provenance/api")
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("worldcondenser.lab") }
    }
}

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(deobf("curse.maven:hexerei-548599:6314111"))
    compileOnly(deobf("curse.maven:genetic-animals-313213:6134040"))
    compileOnly(deobf("curse.maven:complicated-bees-1100137:8277762"))
    compileOnly(deobf("curse.maven:serene-seasons-291874:6398227"))
    compileOnly(deobf("curse.maven:blood-magic-224791:${property("bloodmagic_file_id")}"))
    compileOnly(deobf("worldcondenser.lab:better-dimension-fonts:1.0.0"))
    compileOnly(deobf("worldcondenser.lab:lodestone:1.20.1-1.6.4.1"))
    compileOnly(deobf("worldcondenser.lab:better-ore-geology:0.2.0"))
    compileOnly(deobf("worldcondenser.lab:better-bumblezone-crops:0.1.0"))
    compileOnly(deobf("worldcondenser.lab:better-celestial-events:0.1.0"))
    compileOnly(deobf("worldcondenser.lab:better-spirit-commerce:1.0.1"))
    implementation(deobf("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim"))
    implementation(deobf("net.createmod.ponder:Ponder-Forge-${property("minecraft_version")}:${property("ponder_version")}"))
    compileOnly(deobf("dev.engine-room.flywheel:flywheel-forge-api-${property("minecraft_version")}:${property("flywheel_version")}"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-${property("minecraft_version")}:${property("flywheel_version")}"))
    implementation(deobf("com.tterrag.registrate:Registrate:${property("registrate_version")}"))
    implementation(deobf("io.github.llamalad7:mixinextras-forge:0.3.6"))
    runtimeOnly(deobf("curse.maven:blood-magic-224791:${property("bloodmagic_file_id")}"))
    runtimeOnly(deobf("curse.maven:patchouli-306770:${property("patchouli_file_id")}"))
    add(feasibilityHarness.implementationConfigurationName,
        deobf("curse.maven:hexerei-548599:6314111"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.named<Jar>("jar") {
    manifest.attributes("MixinConfigs" to "better_world_management.mixins.json,better_world_management.recipes.mixins.json")
    finalizedBy("reobfJar")
}

// Separate artifact for explicit native campaigns; never included in the shipping JAR.
val feasibilityHarnessJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("feasibilityHarnessClasses"))
    archiveClassifier.set("native-fixtures")
    from(feasibilityHarness.output)
}
extensions.getByName("reobf").withGroovyBuilder { "create"("feasibilityHarnessJar") }
val stageFeasibilityJar by tasks.registering {
    dependsOn(tasks.named("reobfFeasibilityHarnessJar"))
    val producer = layout.buildDirectory.file("reobfFeasibilityHarnessJar/output.jar")
    val artifact = layout.buildDirectory.file("libs/better-world-management-$version-native-fixtures.jar")
    inputs.file(producer)
    outputs.file(artifact)
    doLast { producer.get().asFile.copyTo(artifact.get().asFile, overwrite = true) }
}

val stageRuntimeJar by tasks.registering {
    dependsOn(tasks.named("reobfJar"))
    val producer = layout.buildDirectory.file("reobfJar/output.jar")
    val artifact = layout.buildDirectory.file("libs/${base.archivesName.get()}-$version.jar")
    inputs.file(producer)
    outputs.file(artifact)
    doLast { producer.get().asFile.copyTo(artifact.get().asFile, overwrite = true) }
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.named("compileVisualHarnessJava") { dependsOn(tasks.named("classes")) }
tasks.named("compileFeasibilityHarnessJava") { dependsOn(tasks.named("classes")) }
tasks.withType<JavaExec>().configureEach {
    if (name == "runVisualServer") standardInput = System.`in`
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.test {
    dependsOn(tasks.named("jar"))
    doFirst { systemProperty("world_axioms.test.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath) }
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}
tasks.register("headlessGameTest") { dependsOn(tasks.named("runGameTestServer")) }
tasks.register("verifyFast") { dependsOn(tasks.named("check"), tasks.named("jacocoTestReport")) }
tasks.register("verifyFull") { dependsOn(tasks.named("verifyFast"), tasks.named("headlessGameTest")) }

val syncGameTestStructures = tasks.register<Sync>("syncGameTestStructures") {
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(layout.projectDirectory.dir("run-gametest/gameteststructures"))
}

tasks.matching { it.name.startsWith("prepareRunGameTestServer") }.configureEach {
    dependsOn(syncGameTestStructures)
}

val syncFeasibilityStructures = tasks.register<Sync>("syncFeasibilityStructures") {
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(layout.projectDirectory.dir("run-feasibility/gameteststructures"))
}
tasks.matching { it.name.startsWith("prepareRunFeasibilityServer") }.configureEach {
    dependsOn(syncFeasibilityStructures)
}

val syncVisualHarnessOptions = tasks.register<Copy>("syncVisualHarnessOptions") {
    from(layout.projectDirectory.file("src/visualHarness/options.txt"))
    into(layout.projectDirectory.dir("run-visual-client"))
}
tasks.matching { it.name.startsWith("prepareRunVisualClient") }.configureEach {
    dependsOn(syncVisualHarnessOptions)
}

tasks.processResources {
    dependsOn(tasks.named("compileJava"))
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}
