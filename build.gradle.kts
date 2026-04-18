import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intelij.platform)
    alias(libs.plugins.axion.release)
    alias(libs.plugins.test.logger)
}

group = "com.github.bgalek"
version = scmVersion.version

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1")
        bundledPlugins("org.jetbrains.kotlin", "com.intellij.gradle", "org.toml.lang")
    }
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        name = "Move to version catalog"
        version = project.version.toString()
        changeNotes = provider { "Initial release." }
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
