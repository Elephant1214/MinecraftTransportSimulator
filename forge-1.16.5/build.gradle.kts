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
    accessWidenerPath = file("mts.accesswidener")
}

// See the explanation for this block of code in `core`.
configurations.implementation {
    extendsFrom(configurations.getByName("shadow"))
}

dependencies {
    minecraft("com.mojang:minecraft:1.16.5")
    mappings("net.fabricmc:yarn:1.16.5+build.10:v2")
    forge("net.minecraftforge:forge:1.16.5-36.2.34")

    shadow(project(":core"))

    modCompileOnly("mezz.jei:jei-1.16.5:${properties["jei_version"]}:api")
    modRuntimeOnly("mezz.jei:jei-1.16.5:${properties["jei_version"]}")
}

tasks {
    jar {
        manifest.attributes(
            "MixinTweaker" to "org.spongepowere.dasm.launch.MixinTweaker",
            "TweakOrder" to "0",
            "MixinConfigs" to "mts.mixins.json",
            "MixinConnect" to "mcinterface1165.mixin.MixinConnector",
            "FMLAT" to "mts_at.cfg",
        )
        archiveClassifier.set("thin")
        duplicatesStrategy = DuplicatesStrategy.WARN
        dependsOn(shadowJar)
    }
    shadowJar {
        configurations = listOf(project.configurations.getByName("shadow"))
        mergeServiceFiles()
    }
}
