plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":avm-specification"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
