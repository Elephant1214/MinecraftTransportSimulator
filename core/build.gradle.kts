// This simply creates a configuration called `embed` that extends implementation (i.e., it does
// the same thing) and allows sources from other libraries to be included inside this.
val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    embed("com.googlecode.soundlibs:jlayer:1.0.1.4")
    embed("org.jcraft:jorbis:0.0.17")

    compileOnly("com.google.code.gson:gson:2.8.0") // 1.12.2 uses 2.8.0
    compileOnly("io.netty:netty-all:4.1.9.Final") // 1.12.2 uses 4.1.9.Final
}

tasks {
    // This mess is what automatically updates the mod version in MtsInfo.java
    compileJava {
        // Take the main source directory, find MtsInfo
        val versionFile = project.sourceSets.main.get().java.srcDirs.first()
            .walk()
            .filter {
                it.nameWithoutExtension == "MtsInfo"
            }.first()
        
        // Read the file and setup Regex for the field
        val content = versionFile.readText()
        val target = Regex("""public static final String VERSION = "(.*?)";""")

        // Find a match for the Regex, includes the version that is currently listed
        val match = target.find(content)!!

        // If the version is incorrect, fix it and write the updated file contents
        if (match.groupValues[1] != rootProject.version) {
            val updatedContent = content.replace(
                target,
                """public static final String VERSION = "${rootProject.version}";"""
            )
            versionFile.writeText(updatedContent)
            
            println("Updated mod version in MtsVersion.java (was: ${match.groupValues[1]} now: ${rootProject.version})")
        }
    }
    jar {
        // Includes the dependencies marked with `embed` in the final jar.
        doFirst {
            from(embed.files.map { zipTree(it) })
        }

        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
