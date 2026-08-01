plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":avm-specification"))
    implementation(project(":avm-canonical"))
    implementation(project(":avm-error-codes"))
    implementation(project(":avm-backup"))
    implementation(project(":avm-diagnostics"))
    implementation(project(":avm-compatibility"))
    implementation(project(":platform-core"))
    implementation(project(":reporting-core"))
    testImplementation(kotlin("test"))
}

application { mainClass.set("de.avm.conformance.MainKt") }

tasks.test {
    useJUnitPlatform()
    systemProperty("avm.root", rootProject.projectDir.absolutePath)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    args("test", "all", rootProject.projectDir.absolutePath)
}

val releaseEvidence = tasks.register<JavaExec>("releaseEvidence") {
    group = "distribution"
    description = "Creates the cryptographically bound AVM RC2 release evidence."
    dependsOn(":avmConformance")
    dependsOn(":avmSpecificationArchive")
    dependsOn(":avm-conformance:distZip")
    dependsOn(":avm-interoperability:distZip")
    dependsOn(":capture-android:assembleDebug")
    dependsOn(":capture-android:assembleRelease")
    dependsOn(":demo-cli:distZip")
    dependsOn(":reporter-cli:distZip")
    dependsOn(":designer-desktop:distZip")
    dependsOn(":profile-tool:distZip")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("de.avm.conformance.MainKt")
    workingDir = rootProject.projectDir
    args("release-evidence", rootProject.projectDir.absolutePath)
}
