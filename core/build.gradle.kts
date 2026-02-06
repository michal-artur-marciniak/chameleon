plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")
    implementation("io.ktor:ktor-server-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-core-jvm:${property("ktorVersion")}")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")

    testImplementation(kotlin("test"))
}
