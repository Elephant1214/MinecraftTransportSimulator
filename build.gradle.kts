plugins {
    `java-library`
    id("gg.essential.loom") apply (false)
}

allprojects {
    group = "mts"
    version = "22.17.0"

    repositories {
        maven("https://dvs1.progwml6.com/files/maven/") // JEI repo
        maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") // DevAuth repo
        mavenCentral()
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(8)
            options.compilerArgs.addAll(
                listOf(
                    "-Xlint:deprecation",
                    "-Xlint:unchecked",
                    "-Xlint:-options"
                )
            )
        }
        withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

subprojects {
    apply(plugin = "org.gradle.java-library")

    base {
        archivesName = "${rootProject.name}-${project.name.split("-").last()}"
    }

    java {
        withSourcesJar()

        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks {
        processResources {
            val expansions = mapOf(
                "version" to project.version,
                "mcVersionStr" to project.name.split("-").last(),
                "file" to mapOf("jarVersion" to project.version.toString())
            )

            filesMatching(listOf("mcmod.info", "META-INF/mods.toml")) {
                expand(expansions)
            }
        }
    }
}

