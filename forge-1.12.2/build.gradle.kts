import dev.architectury.pack200.java.Pack200Adapter

plugins {
    id("gg.essential.loom")
    id("dev.architectury.architectury-pack200")
    id("com.gradleup.shadow")
}

loom {
    runs {
        getByName("server").runDir("runServer")
    }
    runConfigs.all {
        isIdeConfigGenerated = true
    }
    forge {
        pack200Provider.set(Pack200Adapter())
        // Required because JEI's AT is broken or something
        accessTransformer("jei_at.cfg")
    }
}

// See the explanation for this block of code in `core`.
configurations.implementation {
    extendsFrom(configurations.getByName("shadow"))
}

dependencies {
    minecraft("com.mojang:minecraft:1.12.2")
    mappings("de.oceanlabs.mcp:mcp_stable:39-1.12")
    forge("net.minecraftforge:forge:1.12.2-14.23.5.2840")
    
    // Include the code from `../core` when this compiles
    shadow(project(":core"))

    compileOnly("mezz.jei:jei_1.12.2:${properties["jei_version"]}:api")
    runtimeOnly("mezz.jei:jei_1.12.2:${properties["jei_version"]}")
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
        archiveClassifier = null
    }
}
