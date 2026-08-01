plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }
dependencies { implementation(project(":reporting-core")) }
application { mainClass.set("de.av.modular.reporter.MainKt") }
