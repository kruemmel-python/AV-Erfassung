import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    base
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}

subprojects {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
}

val nativeConformance by tasks.registering(Exec::class) {
    group = "verification"
    description = "Builds and runs the independent warning-free C++ AVM contract implementation."
    workingDir = rootProject.projectDir
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "conformance/run-native-conformance.ps1")
}

val avmSpecificationArchive by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates the frozen AVM Specification 1.0.0-RC1 archive."
    from(layout.projectDirectory.dir("specification"))
    exclude("**/build/**", "**/.gradle/**")
    archiveFileName.set("AVM-Specification-1.0.0-RC1.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.register("avmConformance") {
    group = "verification"
    description = "Runs the normative AVM conformance gate across Kotlin, Android and C++ contracts."
    dependsOn(":avm-conformance:run")
    dependsOn(":avm-conformance:test")
    dependsOn(":avm-specification:test")
    dependsOn(":platform-core:test")
    dependsOn(":reporting-core:test")
    dependsOn(":designer-desktop:test")
    dependsOn(":capture-android:lintDebug")
    dependsOn(nativeConformance)
}

tasks.register("avmReleaseCandidate") {
    group = "distribution"
    description = "Builds AVM 1.0.0-RC1 and creates signed manifest, report, SBOM and release envelope."
    dependsOn(":avm-conformance:releaseEvidence")
}

allprojects {
    group = "de.av.modular"
    version = "1.0.0-RC1"
}
