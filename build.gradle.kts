/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.0.2" // Adds runServer and runMojangMappedServer tasks for testing
    id("com.gradleup.shadow") version "9.4.2"
    idea
}

// Make sure javadocs are available to IDE
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

group = "kernitus.plugin.OldCombatMechanics"
version = "v2.5.0-2026_06_28" // x-release-please-version
description = "OldCombatMechanics"

allprojects {
    repositories {
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        // CodeMC Repo for bStats
        maven("https://repo.codemc.org/repository/maven-public/")
        // Auth library from Minecraft
        maven("https://libraries.minecraft.net/")
        maven("https://repo.viaversion.com")
    }
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.130.Final")
    // Placeholder API
    compileOnly("me.clip:placeholderapi:2.12.2")
    // For BSON file serialisation
    implementation("org.mongodb:bson:5.8.0")
//    // Spigot
//    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    // JSR-305 annotations (javax.annotation.Nullable)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // ProtocolLib
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    // PacketEvents
    implementation("com.github.retrooper:packetevents-spigot:2.12.1")
    // XSeries
    implementation("com.github.cryptomorin:XSeries:13.7.0")

    //For ingametesting
    // Mojang mappings for NMS

    compileOnly("com.mojang:authlib:6.0.54")
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    // For reflection remapping
    implementation("xyz.jpenilla:reflection-remapper:0.1.3")
    compileOnly("com.viaversion:viaversion-api:5.10.0")
}

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 21 on systems
    // that only have JDK 11 installed for example.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
//    assemble {
//        dependsOn(reobfJar)
//    }
    shadowJar {
        relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats")
        relocate("com.cryptomorin.xseries", "kernitus.plugin.OldCombatMechanics.lib.xseries")
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
            "description" to (project.description ?: ""),
            "apiVersion" to "1.19"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
