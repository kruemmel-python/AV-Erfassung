plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":platform-core"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
application { mainClass.set("de.av.modular.designer.MainKt") }
tasks.test {
    useJUnitPlatform()
    systemProperty("avm.root", rootProject.projectDir.absolutePath)
}
