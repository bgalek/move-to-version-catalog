package com.github.bgalek.movetoversioncatalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionCatalogTest {

    @Test
    fun `library entry with version renders module and version`() {
        val entry = LibraryEntry("guava", "com.google.guava:guava", "33.0.0-jre")
        assertEquals(
            "guava = { module = \"com.google.guava:guava\", version = \"33.0.0-jre\" }",
            entry.toToml(),
        )
    }

    @Test
    fun `library entry without version renders module only`() {
        val entry = LibraryEntry("junit-bom", "org.junit:junit-bom", null)
        assertEquals("junit-bom = { module = \"org.junit:junit-bom\" }", entry.toToml())
    }

    @Test
    fun `plugin entry with version renders as table`() {
        val entry = PluginEntry("kotlin-jvm", "org.jetbrains.kotlin.jvm", "2.0.21")
        assertEquals(
            "kotlin-jvm = { id = \"org.jetbrains.kotlin.jvm\", version = \"2.0.21\" }",
            entry.toToml(),
        )
    }

    @Test
    fun `plugin entry without version renders as string`() {
        val entry = PluginEntry("kotlin-jvm", "org.jetbrains.kotlin.jvm", null)
        assertEquals("kotlin-jvm = \"org.jetbrains.kotlin.jvm\"", entry.toToml())
    }

    @Test
    fun `sanitize replaces invalid chars with dashes`() {
        assertEquals("kotlin-stdlib", VersionCatalog.sanitize("kotlin_stdlib"))
        assertEquals("spring-boot-starter", VersionCatalog.sanitize("spring.boot.starter"))
        assertEquals("library", VersionCatalog.sanitize(""))
        assertEquals("library", VersionCatalog.sanitize("___"))
    }

    @Test
    fun `defaultAlias uses coordinate name`() {
        val alias = VersionCatalog.defaultAlias(Coordinate("com.google.guava", "guava", "33.0.0"))
        assertEquals("guava", alias)
    }

    @Test
    fun `defaultPluginAlias maps well-known ids`() {
        assertEquals("kotlin", VersionCatalog.defaultPluginAlias("org.jetbrains.kotlin.jvm"))
        assertEquals("kotlin-android", VersionCatalog.defaultPluginAlias("org.jetbrains.kotlin.android"))
        assertEquals("kotlin-multiplatform", VersionCatalog.defaultPluginAlias("org.jetbrains.kotlin.multiplatform"))
        assertEquals("android-application", VersionCatalog.defaultPluginAlias("com.android.application"))
        assertEquals("ksp", VersionCatalog.defaultPluginAlias("com.google.devtools.ksp"))
    }

    @Test
    fun `defaultPluginAlias prefixes kotlin compiler plugins`() {
        assertEquals("kotlin-serialization", VersionCatalog.defaultPluginAlias("org.jetbrains.kotlin.plugin.serialization"))
        assertEquals("kotlin-spring", VersionCatalog.defaultPluginAlias("org.jetbrains.kotlin.plugin.spring"))
    }

    @Test
    fun `defaultPluginAlias falls back to last id segment`() {
        assertEquals("axion-release", VersionCatalog.defaultPluginAlias("pl.allegro.tech.build.axion-release"))
        assertEquals("shadow", VersionCatalog.defaultPluginAlias("com.gradleup.shadow"))
    }

    @Test
    fun `accessor converts dashes to dots`() {
        assertEquals("kotlin.jvm", VersionCatalog.accessor("kotlin-jvm"))
        assertEquals("junit.bom", VersionCatalog.accessor("junit-bom"))
    }
}
