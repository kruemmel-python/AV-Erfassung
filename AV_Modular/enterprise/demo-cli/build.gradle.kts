plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies { implementation(project(":platform-core")) }

application { mainClass.set("de.av.modular.demo.MainKt") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    systemProperty("av.modular.root", rootProject.projectDir.absolutePath)
}
