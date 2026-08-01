plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

application { mainClass.set("de.avm.interoperability.MainKt") }

tasks.test {
    useJUnitPlatform()
    systemProperty("avm.root", rootProject.projectDir.absolutePath)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    args("baseline", rootProject.projectDir.absolutePath)
}
