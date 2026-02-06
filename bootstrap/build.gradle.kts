plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infra"))
    implementation(project(":sdk"))
    implementation(project(":application"))
    implementation(project(":plugins:telegram"))

    implementation("io.ktor:ktor-server-core-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-netty-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-websockets-jvm:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${property("ktorVersion")}")

    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
    implementation("net.logstash.logback:logstash-logback-encoder:${property("logstashLogbackEncoderVersion")}")

    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("io.insert-koin:koin-ktor:3.5.6")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("kotlinxSerializationVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinxCoroutinesVersion")}")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.chameleon.ApplicationKt")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    archiveBaseName.set("chameleon")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.chameleon.ApplicationKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}
