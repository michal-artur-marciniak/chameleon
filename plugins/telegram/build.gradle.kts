plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")

    implementation("io.ktor:ktor-client-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-cio-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")

    // Telegram bot library placeholder; actual implementation wired in M1 step 2
    // implementation("com.github.kotlin-telegram-bot:kotlin-telegram-bot:6.2.0")

    testImplementation(kotlin("test"))
}
