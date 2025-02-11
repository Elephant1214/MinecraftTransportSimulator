import dev.architectury.pack200.java.Pack200Adapter

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
    forge {
        pack200Provider.set(Pack200Adapter())
        // Required because JEI's AT is broken or something
        accessTransformer("jei_at.cfg")
    }
}

val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.12.2")
    mappings("de.oceanlabs.mcp:mcp_stable:39-1.12")
    forge("net.minecraftforge:forge:1.12.2-14.23.5.2840")

    // Include the code from `../core` when this compiles
    embed(project(":core"))

    compileOnly("mezz.jei:jei_1.12.2:${properties["jei_version"]}:api")
    runtimeOnly("mezz.jei:jei_1.12.2:${properties["jei_version"]}")

    // Dev auth so that you can sign in inside dev
    modRuntimeOnly("me.djtheredstoner:DevAuth-forge-legacy:${properties["dev_auth_version"]}")
}

tasks.jar {
    doFirst {
        from(embed.files.map { zipTree(it) })
    }

    duplicatesStrategy = DuplicatesStrategy.WARN
}
