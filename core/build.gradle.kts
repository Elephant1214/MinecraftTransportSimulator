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
    jar {
        doFirst {
            from(embed.files.map { zipTree(it) })
        }
        
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
