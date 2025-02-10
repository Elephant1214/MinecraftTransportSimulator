plugins {
    `java-library`
    id("gg.essential.loom") apply (false)
}

allprojects {
    group = "mts"
    version = "22.17.0"
    
    repositories {
        maven("https://dvs1.progwml6.com/files/maven/") // JEI repo
        mavenCentral()
    }
    
    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(8)
            options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-Xlint:-options")) // looks like shit, replace with more kotlin-like
        }
        withType<Jar> {
            archiveBaseName.set("${rootProject.name}-${project.name.split("-").last()}")
        }
        withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

subprojects {
    apply(plugin = "org.gradle.java-library")
    
    java {
        withSourcesJar()
        
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}
