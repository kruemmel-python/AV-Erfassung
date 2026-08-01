plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":avm-error-codes"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(project(":avm-backup"))
    testImplementation(project(":avm-diagnostics"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("av.modular.root", rootProject.projectDir.absolutePath)
}
