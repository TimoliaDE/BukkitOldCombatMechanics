/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing

    // Ein Gradle-Shade-Plugin wird eingefügt, damit die Dependencies mit "implementation" in die Jar-Datei
    // eingebunden wird
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "kernitus.plugin.OldCombatMechanics"
version = "v2.1.0-adapted" // x-release-please-version
description = "OldCombatMechanics"

allprojects {
    repositories {
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        // CodeMC Repo for bStats
        maven("https://repo.codemc.org/repository/maven-public/")
        // Auth library from Minecraft
        maven("https://libraries.minecraft.net/")
    }
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.0.2")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.106.Final")
    // Placeholder API
    compileOnly("me.clip:placeholderapi:2.11.5")
    // For BSON file serialisation
    implementation("org.mongodb:bson:5.0.1")
    // ProtocolLib
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    // XSeries
    implementation("com.github.cryptomorin:XSeries:13.3.3")

    //For ingametesting
    // Mojang mappings for NMS

    compileOnly("com.mojang:authlib:4.0.43")
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
    // For reflection remapping
    implementation("xyz.jpenilla:reflection-remapper:0.1.1")
}

tasks {
    // Configure reobfJar to run when invoking the build task
    assemble {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything

        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(21)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
        val props = mapOf(
            "name" to project.name,
            "version" to project.version,
            "description" to project.description,
            "apiVersion" to "1.19"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}