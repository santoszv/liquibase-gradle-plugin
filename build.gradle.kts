import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `embedded-kotlin`
    `maven-publish`
    `java-gradle-plugin`
    signing
    id("com.gradle.plugin-publish") version "0.10.1"
}

group = "mx.com.inftel.oss.liquibase"
version = "1.0.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation(embeddedKotlin("stdlib-jdk8"))
    implementation(embeddedKotlin("reflect"))

    compileOnly(gradleApi())

    compileOnly("org.liquibase:liquibase-core:3.6.3")
}

sourceSets {
    main {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("$buildDir/version/src")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        javaParameters = true
        jvmTarget = "1.8"
    }
}

tasks.named("compileKotlin", KotlinCompile::class) {
    dependsOn("generateVersion")
}

tasks.register("generateVersion") {
    outputs.dir("$buildDir/version/src")
    doFirst {
        val dir = file("$buildDir/version/src")
        dir.exists() || dir.mkdirs()
        val file = File(dir, "LiquibaseVersion.kt")
        file.writeText("""
            package mx.com.inftel.liquibase.gradle.plugin

            object LiquibaseVersion {
                val group = "${project.group}"
                val name = "${project.name}"
                val version = "${project.version}"
            }
        """.trimIndent())
    }
}

tasks.register<Jar>("javadocJar") {
    outputs.dir("$buildDir/javadoc")

    doFirst {
        val dir = file("$buildDir/javadoc")
        dir.exists() || dir.mkdirs()
        val file = File(dir, "README")
        file.writeText("No Javadoc, please see directly Kotlin source code.")
    }

    archiveClassifier.set("javadoc")
    from("$buildDir/javadoc")
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    val sourceSet = sourceSets.main.get()
    from(sourceSet.allSource)
}

gradlePlugin {
    plugins {
        create("liquibase") {
            id = "mx.com.inftel.oss.liquibase"
            implementationClass = "mx.com.inftel.liquibase.gradle.plugin.LiquibasePlugin"
        }
    }
}

afterEvaluate {

    publishing {

        repositories {

            maven {
                name = "build"
                url = file("$buildDir/repository").toURI()
            }

            if ("${properties["ossrh.upload"]}".toBoolean()) {
                maven {
                    name = "snapshots"
                    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
                    credentials {
                        username = "${properties["ossrh.username"]}"
                        password = "${properties["ossrh.password"]}"
                    }
                }
                maven {
                    name = "staging"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = "${properties["ossrh.username"]}"
                        password = "${properties["ossrh.password"]}"
                    }
                }
            }
            if ("${properties["bintray.upload"]}".toBoolean()) {
                maven {
                    name = "bintray"
                    url = uri("https://api.bintray.com/maven/santoszv/oss/liquibase-gradle-plugin/;publish=1")
                    credentials {
                        username = "${properties["bintray.username"]}"
                        password = "${properties["bintray.password"]}"
                    }
                }
            }
        }

        publications {
            named("pluginMaven", MavenPublication::class) {

                artifact(tasks["sourcesJar"])
                artifact(tasks["javadocJar"])

                pom {
                    name.set("Liquibase Gradle Plugin")
                    description.set("Liquibase Gradle Plugin")
                    url.set("https://github.com/santoszv/liquibase-gradle-plugin")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("santoszv")
                            name.set("Santos Zatarain Vera")
                            email.set("santoszv@inftel.com.mx")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/santoszv/liquibase-gradle-plugin.git")
                        developerConnection.set("scm:git:https://github.com/santoszv/liquibase-gradle-plugin.git")
                        url.set("https://github.com/santoszv/liquibase-gradle-plugin")
                    }
                }
            }

            named("liquibasePluginMarkerMaven", MavenPublication::class) {

                pom {
                    name.set("Liquibase Gradle Plugin")
                    description.set("Liquibase Gradle Plugin")
                    url.set("https://github.com/santoszv/liquibase-gradle-plugin")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("santoszv")
                            name.set("Santos Zatarain Vera")
                            email.set("santoszv@inftel.com.mx")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/santoszv/liquibase-gradle-plugin.git")
                        developerConnection.set("scm:git:https://github.com/santoszv/liquibase-gradle-plugin.git")
                        url.set("https://github.com/santoszv/liquibase-gradle-plugin")
                    }
                }
            }
        }
    }

    if (!"$version".endsWith("-SNAPSHOT")) {
        signing {
            useGpgCmd()
            sign(publishing.publications["pluginMaven"])
            sign(publishing.publications["liquibasePluginMarkerMaven"])
        }
    }
}

if (!"$version".endsWith("-SNAPSHOT")) {
    pluginBundle {
        website = "https://github.com/santoszv/liquibase-gradle-plugin"
        vcsUrl = "https://github.com/santoszv/liquibase-gradle-plugin.git"
        description = "Liquibase Gradle Plugin"
        tags = listOf("liquibase", "database")

        (plugins) {
            "liquibase" {
                displayName = "Liquibase Gradle Plugin"
            }
        }
    }
}