import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    java
}

group = "ru.pavel.coldsnap"
version = "1.6.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.enginehub.org/repo/")
}

val jumperVersion = "0.12.0"
val jumperUrl = "https://github.com/jumper-lang/jumper/releases/download/v$jumperVersion/jmp.jar"
val jumperSha256 = "ecacb504e18b9e54152fc12582597688f0794d899b5f3801fc620e9a89f2dceb"
val jumperJar = layout.buildDirectory.file("dependencies/jumper-$jumperVersion.jar")
val downloadJumper by tasks.registering {
    inputs.property("url", jumperUrl)
    inputs.property("sha256", jumperSha256)
    outputs.file(jumperJar)

    doLast {
        val connection = URI(jumperUrl).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val bytes = connection.getInputStream().use { it.readBytes() }
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        check(digest == jumperSha256) { "Unexpected SHA-256 for Jumper $jumperVersion: $digest" }
        jumperJar.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(bytes)
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
    implementation(files(jumperJar).builtBy(downloadJumper))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("Coldsnap-${project.version}.jar")
    from("LICENSE") {
        into("META-INF")
        rename { "LICENSE-coldsnap.txt" }
    }
    from({ zipTree(jumperJar.get().asFile) }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    manifest.attributes("paperweight-mappings-namespace" to "mojang")
}
