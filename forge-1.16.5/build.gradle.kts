plugins {
    id("gg.essential.loom")
    id("dev.architectury.architectury-pack200")
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

val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.16.5")
    mappings("net.fabricmc:yarn:1.16.5+build.10:v2")
    forge("net.minecraftforge:forge:1.16.5-36.2.34")

    embed(project(":core"))

    modCompileOnly("mezz.jei:jei-1.16.5:${properties["jei_version"]}:api")
    modRuntimeOnly("mezz.jei:jei-1.16.5:${properties["jei_version"]}")

    // Dev auth so that you can sign in inside dev
    modRuntimeOnly("me.djtheredstoner:DevAuth-forge-latest:${properties["dev_auth_version"]}")
}

tasks.jar {
    doFirst {
        from(embed.files.map { zipTree(it) })
    }

    manifest.attributes(
        "MixinTweaker" to "org.spongepowere.dasm.launch.MixinTweaker",
        "TweakOrder" to "0",
        "MixinConfigs" to "mts.mixins.json",
        "MixinConnect" to "mcinterface1165.mixin.MixinConnector",
        "FMLAT" to "mts_at.cfg",
    )
    duplicatesStrategy = DuplicatesStrategy.WARN
}
