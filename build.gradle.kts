import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

plugins {
    kotlin("jvm") version "1.3.31"
    id("org.jetbrains.dokka") version "0.9.18"
    `java-gradle-plugin`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation(embeddedKotlin("stdlib-jdk8"))
    implementation(embeddedKotlin("reflect"))

    compileOnly(gradleApi())

    compileOnly("org.liquibase:liquibase-core:3.6.3")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        javaParameters = true
        jvmTarget = "1.8"
    }
}

tasks.withType<DokkaTask> {
    moduleName = "Liquibase Gradle Plugin"
    outputFormat = "javadoc"
    outputDirectory = "$buildDir/dokka/javadoc"
    includes = listOf("module.md", "packages.md")
    jdkVersion = 8
}

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from("$buildDir/dokka/javadoc")
    dependsOn("dokka")
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    val sourceSet = sourceSets.main.get()
    from(sourceSet.allSource)
}

fun password(user: String): String {
    val process = Runtime.getRuntime().exec("pinentry-mac")
    try {
        val input = BufferedReader(InputStreamReader(process.inputStream))
        val output = BufferedWriter(OutputStreamWriter(process.outputStream))

        val timeout = "SETTIMEOUT 30"
        val description = "SETDESC Enter password for $user"
        val prompt = "SETPROMPT Password:"

        var line: String
        val ok: () -> Unit = {
            line = input.readLine()
            //println(line)
            if (!line.startsWith("OK"))
                throw UnsupportedOperationException()
        }
        val command: (str: String) -> Unit = {
            //println(it)
            output.appendln(it)
            output.flush()
            ok()
        }
        val password: () -> String = {
            //println("GETPIN")
            output.appendln("GETPIN")
            output.flush()
            line = input.readLine()
            if (!line.startsWith("D "))
                throw UnsupportedOperationException()
            line.substring(2)
        }

        ok()
        command(timeout)
        command(description)
        command(prompt)
        return password()
    } finally {
        process.destroy()
    }
}

gradlePlugin {
    plugins {
        create("liquibase") {
            id = "mx.com.inftel.oss.liquibase-gradle-plugin"
            implementationClass = "mx.com.inftel.liquibase.gradle.plugin.LiquibasePlugin"
        }
    }
}

publishing {
    /*publications {
        create<MavenPublication>("LiquibaseGradlePlugin") {

            groupId = "${properties["Project.GroupID"]}"
            artifactId = "${properties["Project.ArtifactID"]}"
            version = "${properties["Project.Version"]}"

            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("Liquibase Gradle Plugin")
                description.set("Liquibase Gradle Plugin")
                url.set("https://github.com/santoszv")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("https://github.com/santoszv")
                        name.set("Santos Zatarain Vera")
                        email.set("coder.santoszv(at)gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/santoszv")
                    developerConnection.set("scm:git:https://github.com/santoszv")
                    url.set("https://github.com/santoszv")
                }
            }
        }
    }*/

    repositories {
        val isSnapshot = "${properties["Project.Version"] ?: ""}".endsWith("-SNAPSHOT")
        val isUpload = "${properties["OSSRH.Upload"] ?: ""}".toBoolean()
        val username = "${properties["OSSRH.Username"] ?: ""}"
        if (isUpload) {
            maven {
                url = if (isSnapshot)
                    uri("https://oss.sonatype.org/content/repositories/snapshots/")
                else
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    if (username.isNotBlank()) {
                        this.username = username
                        val password = password("OSSRH.Password")
                        if (password.isNotBlank()) {
                            this.password = password
                        }
                    }
                }
            }
        } else {
            maven("$buildDir/repository")
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            val pluginMaven = findByName("pluginMaven") as DefaultMavenPublication
            pluginMaven.apply {
                groupId = "${properties["Project.GroupID"]}"
                artifactId = "${properties["Project.ArtifactID"]}"
                version = "${properties["Project.Version"]}"

                artifact(tasks["sourcesJar"])
                artifact(tasks["javadocJar"])
            }
            val jasperreportsPluginMarkerMaven = findByName("liquibasePluginMarkerMaven") as DefaultMavenPublication
            jasperreportsPluginMarkerMaven.apply {
                version = "${properties["Project.Version"]}"
            }
        }
    }

    signing {
        useGpgCmd()
        val keyName = "${properties["signing.gnupg.keyName"] ?: ""}"
        if (keyName.isNotBlank()) {
            sign(publishing.publications["pluginMaven"])
            sign(publishing.publications["liquibasePluginMarkerMaven"])
        }
    }
}