pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AVModular"

fun includeAt(name: String, path: String) {
    include(":$name")
    project(":$name").projectDir = file(path)
}

includeAt("avm-specification", "specification/avm-specification")
includeAt("avm-canonical", "conformance/avm-canonical")
includeAt("avm-error-codes", "conformance/avm-error-codes")
includeAt("avm-backup", "conformance/avm-backup")
includeAt("avm-diagnostics", "conformance/avm-diagnostics")
includeAt("avm-compatibility", "conformance/avm-compatibility")
includeAt("avm-conformance", "conformance/avm-conformance-cli")
includeAt("avm-interoperability", "conformance/avm-interoperability")

includeAt("platform-core", "enterprise/platform-core")
includeAt("demo-cli", "enterprise/demo-cli")
includeAt("capture-android", "enterprise/capture-android")
includeAt("reporting-core", "enterprise/reporting-core")
includeAt("reporter-cli", "enterprise/reporter-cli")
includeAt("profile-tool", "enterprise/profile-tool")
includeAt("designer-desktop", "enterprise/designer-desktop")
