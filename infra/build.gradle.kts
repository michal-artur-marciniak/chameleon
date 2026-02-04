plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sdk"))
    implementation(project(":plugins:telegram"))
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")

    testImplementation(kotlin("test"))
}
