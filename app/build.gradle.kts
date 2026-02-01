plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infra"))
    implementation(project(":sdk"))
    implementation(project(":plugins:telegram"))

    implementation("io.ktor:ktor-server-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-netty-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-websockets-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${property("ktorVersion")}")

    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("io.insert-koin:koin-ktor:3.5.6")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("agent.platform.ApplicationKt")
}
