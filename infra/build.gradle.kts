plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sdk"))
    implementation(project(":plugins:telegram"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")

    testImplementation(kotlin("test"))
}
