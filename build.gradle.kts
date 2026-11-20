plugins {
    id("fabric-loom") version "1.19.4"
    kotlin("jvm") version "2.4.20"
}

val modVersion = providers.gradleProperty("mod_version").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()

version = "$modVersion+$minecraftVersion"
group = providers.gradleProperty("maven_group").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

loom {
    runs {
        named("client") {
            programArgs("--username", "xFenx")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven { url = uri(layout.projectDirectory.dir("libs/repo")) }
}

val sqliteRuntime by configurations.creating { isTransitive = false }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

    // Vendored Windows-only build of fabric-gui-imgui, served from a local Maven repo
    // so Loom can treat it as a normal JIJ module.
    modImplementation("cn.enaium:fabric-gui-imgui:1.21.11-1.2.0-windows3")
    include("cn.enaium:fabric-gui-imgui:1.21.11-1.2.0-windows3")
    // The ImGui Java API is nested inside the library; expose it for Kotlin compilation.
    compileOnly(files("libs/imgui-java-binding-1.92.7.1.jar"))

    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    sqliteRuntime("org.xerial:sqlite-jdbc:3.46.1.0")

    // Unit tests. The geometry / glyph assertions run headless against the same ImGui build the
    // client uses, so they need the binding jar and the native library but no Minecraft.
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation(files("libs/imgui-java-binding-1.92.7.1.jar"))
    testRuntimeOnly(
        files("libs/repo/cn/enaium/fabric-gui-imgui/1.21.11-1.2.0-windows3/fabric-gui-imgui-1.21.11-1.2.0-windows3.jar"),
    )
}

tasks.test {
    useJUnitPlatform()
    // Tests that scan the sources / load the bundled font need a stable project root.
    systemProperty("crashs.projectDir", projectDir.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    from({ sqliteRuntime.files.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        // Keep only the Windows x64 SQLite native; the full driver is ~18 MB.
        exclude(
            "org/sqlite/native/FreeBSD/**",
            "org/sqlite/native/Linux-Android/**",
            "org/sqlite/native/Linux-Musl/**",
            "org/sqlite/native/Linux/**",
            "org/sqlite/native/Mac/**",
            "org/sqlite/native/Windows/aarch64/**",
            "org/sqlite/native/Windows/armv7/**",
            "org/sqlite/native/Windows/x86/**",
        )
    }

    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
