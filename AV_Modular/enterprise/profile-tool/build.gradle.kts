plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }
dependencies { implementation(project(":platform-core")) }
application { mainClass.set("de.av.modular.profiletool.MainKt") }
