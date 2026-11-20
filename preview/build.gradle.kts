plugins {
    kotlin("jvm") version "2.4.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("../libs/repo/cn/enaium/fabric-gui-imgui/1.21.11-1.2.0-windows3/fabric-gui-imgui-1.21.11-1.2.0-windows3.jar"))
    // The shared layout / UI-state classes read their JSON through Gson.
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(files("libs/lwjgl-3.4.1.jar"))
    implementation(files("libs/lwjgl-3.4.1-unsafe.jar"))
    implementation(files("libs/lwjgl-glfw-3.4.1.jar"))
    implementation(files("libs/lwjgl-opengl-3.4.1.jar"))
    runtimeOnly(files("libs/lwjgl-3.4.1-natives-windows.jar"))
    runtimeOnly(files("libs/lwjgl-glfw-3.4.1-natives-windows.jar"))
    runtimeOnly(files("libs/lwjgl-opengl-3.4.1-natives-windows.jar"))
}

kotlin {
    jvmToolchain(21)
}

// The preview draws the real surface: these are the Minecraft-free UI sources compiled straight
// from the main module, so the window can never drift away from the in-game layout again.
sourceSets["main"].kotlin.srcDir("../src/main/kotlin")
sourceSets["main"].kotlin.include(
    // the preview's own sources (the include filter applies to every source dir)
    "com/crashs/crashslegacy/preview/**",
    // the Minecraft-free UI surface, compiled straight from the main module
    "com/crashs/crashslegacy/ui/CrashsImGuiFields.kt",
    "com/crashs/crashslegacy/ui/CrashsUiPaths.kt",
    "com/crashs/crashslegacy/ui/CrashsImGuiPrefs.kt",
    "com/crashs/crashslegacy/ui/CrashsImGuiSurface.kt",
    "com/crashs/crashslegacy/ui/CrashsImGuiLayout.kt",
    "com/crashs/crashslegacy/ui/CrashsImGuiUiState.kt",
    "com/crashs/crashslegacy/ui/CrashsMotion.kt",
    "com/crashs/crashslegacy/ui/CrashsUiMetrics.kt",
    "com/crashs/crashslegacy/ui/CrashsWindowGeometry.kt",
    "com/crashs/crashslegacy/ui/theme/CrashsImGuiTheme.kt",
    "com/crashs/crashslegacy/ui/theme/CrashsThemePalette.kt",
)

application {
    mainClass.set("com.crashs.crashslegacy.preview.PreviewMainKt")
    applicationDefaultJvmArgs = listOf("-Dorg.lwjgl.system.stackSize=64")
}

/**
 * Headless CPU benchmark of the real surface: `./gradlew :preview:perf`.
 *
 * Extra options: `-PperfAll=true` (expand every card), `-PperfFrames=2000`,
 * `-PperfMaxUs=200` (fail when the mean frame time exceeds it), `-PperfJson=true`.
 */
tasks.register<JavaExec>("perf") {
    group = "verification"
    description = "Benchmark CrashsImGuiSurface frame cost without a window or GPU"
    mainClass.set("com.crashs.crashslegacy.preview.PerfMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = listOf("-Dorg.lwjgl.system.stackSize=64", "--enable-native-access=ALL-UNNAMED")
    listOf("perfAll" to "perf.all", "perfFrames" to "perf.frames", "perfMaxUs" to "perf.maxUs", "perfJson" to "perf.json")
        .forEach { (gradleName, systemName) ->
            val value = providers.gradleProperty(gradleName).orNull
            if (value != null) systemProperty(systemName, value)
        }
    standardOutput = System.out
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}
