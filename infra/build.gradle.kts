plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sdk"))
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("io.ktor:ktor-client-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-cio-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    implementation("io.ktor:ktor-client-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-cio-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")


    testImplementation(kotlin("test"))
}
