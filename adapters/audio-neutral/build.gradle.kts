// SPDX-License-Identifier: AGPL-3.0-or-later
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    application
}
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
layout.buildDirectory.set(layout.projectDirectory.dir("../../local/audio-neutral-gradle"))

// Compile only the pinned immutable value, never the supplier build or capture code.
val supplierRoot = providers.gradleProperty("synedelicaSource")
    .orElse(providers.environmentVariable("SYNEDELICA_SOURCE"))
val snapshot = supplierRoot.map {
    file(it).resolve("core/src/main/kotlin/io/github/mesmerprism/synedelica/core/AudioSnapshot.kt")
}
val verifySupplier by tasks.registering {
    inputs.file(snapshot)
    doLast {
        val source = snapshot.get()
        val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == "73b6e71e665764144f8e151ed8cb0f8bc20c2669f6032c3892f4081be0e87139") {
            "AudioSnapshot source differs from the pinned public Synedelica revision"
        }
    }
}
tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(verifySupplier)
    source(snapshot)
    source(file("../../examples/audio-reactive/AudioNeutralExample.kt"))
}
tasks.test { useJUnit() }
application { mainClass.set("io.github.mesmerprism.questcompositor.audioneutral.example.AudioNeutralExample") }
