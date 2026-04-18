package com.github.bgalek.movetoversioncatalog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

enum class CatalogSection(val header: String) {
    LIBRARIES("[libraries]"),
    PLUGINS("[plugins]"),
}

data class LibraryEntry(
    val alias: String,
    val module: String,
    val version: String?,
) {
    fun toToml(): String = if (version != null) {
        "$alias = { module = \"$module\", version = \"$version\" }"
    } else {
        "$alias = { module = \"$module\" }"
    }
}

data class PluginEntry(
    val alias: String,
    val id: String,
    val version: String?,
) {
    fun toToml(): String = if (version != null) {
        "$alias = { id = \"$id\", version = \"$version\" }"
    } else {
        "$alias = \"$id\""
    }
}

object VersionCatalog {

    /**
     * Walks up from the given build file to the nearest directory containing a Gradle
     * settings file, and returns (creating if needed) `gradle/libs.versions.toml`
     * under it. Falls back to the project base dir when no settings file is found.
     */
    fun findOrCreateCatalogFile(project: Project, buildFile: VirtualFile): VirtualFile? {
        val rootDir = findGradleRoot(buildFile) ?: run {
            val basePath = project.basePath ?: return null
            LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        }
        val existing = rootDir.findFileByRelativePath("gradle/libs.versions.toml")
        if (existing != null) return existing

        return runCatching {
            ApplicationManager.getApplication().runWriteAction<VirtualFile> {
                val gradleDir = VfsUtil.createDirectoryIfMissing(rootDir, "gradle")
                    ?: return@runWriteAction null
                val file = gradleDir.findChild("libs.versions.toml")
                    ?: gradleDir.createChildData(this, "libs.versions.toml")
                VfsUtil.saveText(
                    file,
                    "[versions]\n\n[libraries]\n\n[plugins]\n",
                )
                file
            }
        }.getOrNull()
    }

    private fun findGradleRoot(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = file.parent
        while (dir != null) {
            if (dir.findChild("settings.gradle.kts") != null || dir.findChild("settings.gradle") != null) {
                return dir
            }
            dir = dir.parent
        }
        return null
    }

    fun addEntry(catalog: VirtualFile, section: CatalogSection, entry: String) {
        val document = FileDocumentManager.getInstance().getDocument(catalog) ?: return
        val text = document.text
        val headerIndex = text.indexOf(section.header)

        val newText = if (headerIndex < 0) {
            val suffix = if (text.endsWith("\n") || text.isEmpty()) "" else "\n"
            "$text$suffix\n${section.header}\n$entry\n"
        } else {
            val insertAt = text.indexOf('\n', headerIndex).let { if (it < 0) text.length else it + 1 }
            buildString {
                append(text, 0, insertAt)
                append(entry).append('\n')
                append(text, insertAt, text.length)
            }
        }

        document.setText(newText)
        FileDocumentManager.getInstance().saveDocument(document)
    }

    fun aliasExists(catalog: VirtualFile, section: CatalogSection, alias: String): Boolean {
        val document = FileDocumentManager.getInstance().getDocument(catalog) ?: return false
        val text = document.text
        val headerIndex = text.indexOf(section.header)
        if (headerIndex < 0) return false

        val nextHeader = Regex("\\n\\[[^]]+]")
            .find(text, headerIndex + section.header.length)
            ?.range?.first
            ?: text.length
        val body = text.substring(headerIndex + section.header.length, nextHeader)
        val pattern = Regex("(?m)^\\s*${Regex.escape(alias)}\\s*=")
        return pattern.containsMatchIn(body)
    }

    fun defaultAlias(coordinate: Coordinate): String =
        sanitize(coordinate.name)

    private val WELL_KNOWN_PLUGIN_ALIASES: Map<String, String> by lazy {
        val properties = java.util.Properties()
        VersionCatalog::class.java.getResourceAsStream("/well-known-plugin-aliases.properties")
            ?.use { properties.load(it) }
        properties.entries.associate { (k, v) -> k.toString() to v.toString() }
    }

    fun defaultPluginAlias(id: String): String {
        WELL_KNOWN_PLUGIN_ALIASES[id]?.let { return it }
        if (id.startsWith("org.jetbrains.kotlin.plugin.")) {
            return "kotlin-" + sanitize(id.removePrefix("org.jetbrains.kotlin.plugin."))
        }
        return sanitize(id.substringAfterLast('.'))
    }

    fun sanitize(raw: String): String {
        val normalized = raw.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
        return normalized.ifEmpty { "library" }
    }

    fun accessor(alias: String): String = alias.replace('-', '.')
}
