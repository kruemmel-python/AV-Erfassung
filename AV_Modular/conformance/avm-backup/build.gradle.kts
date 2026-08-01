plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":avm-specification"))
    api(project(":avm-error-codes"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
