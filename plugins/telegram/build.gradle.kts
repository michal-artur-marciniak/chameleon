plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")

    // Telegram bot library placeholder; actual implementation wired in M1 step 2
    // implementation("com.github.kotlin-telegram-bot:kotlin-telegram-bot:6.2.0")

    testImplementation(kotlin("test"))
}
