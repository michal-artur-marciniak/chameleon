rootProject.name = "chameleon"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "bootstrap",
    "core",
    "infra",
    "sdk",
    "application",
    "plugins:telegram"
)
