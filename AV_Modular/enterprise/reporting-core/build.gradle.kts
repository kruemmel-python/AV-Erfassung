plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":platform-core"))
    api(project(":avm-canonical"))
    api(project(":avm-error-codes"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test { useJUnitPlatform() }
