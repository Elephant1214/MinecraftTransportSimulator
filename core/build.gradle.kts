plugins {
    id("com.gradleup.shadow")
}

// This looks like jibberish, but it makes the shadow configuration extend implementation.
// So `shadow` now basically means `implementation` with extra steps.
configurations.implementation {
    extendsFrom(configurations.getByName("shadow"))
}

dependencies {
    shadow("com.googlecode.soundlibs:jlayer:1.0.1.4")
    shadow("org.jcraft:jorbis:0.0.17")
    
    compileOnly("com.google.code.gson:gson:2.8.0") // 1.12.2 uses 2.8.0
    compileOnly("io.netty:netty-all:4.1.9.Final") // 1.12.2 uses 4.1.9.Final
}

tasks {
    jar {
        archiveClassifier.set("thin")
        duplicatesStrategy = DuplicatesStrategy.WARN
        dependsOn(shadowJar)
    }

    shadowJar {
        configurations = listOf(project.configurations.getByName("shadow"))
        mergeServiceFiles()
        archiveClassifier.set("fat")
    }
}
