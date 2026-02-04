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
    implementation("io.ktor:ktor-client-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-cio-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")

    testImplementation(kotlin("test"))
}
