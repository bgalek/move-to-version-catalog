package com.github.bgalek.movetoversioncatalog

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

class MoveToVersionCatalogIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Move to version catalog"
    override fun getText(): String = "Move to version catalog"

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (!file.name.endsWith(".gradle.kts")) return false
        return findTarget(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val target = findTarget(element) ?: return
        val buildFile = element.containingFile?.virtualFile ?: return
        val catalog = VersionCatalog.findOrCreateCatalogFile(project, buildFile) ?: run {
            Messages.showErrorDialog(
                project,
                "Could not locate or create gradle/libs.versions.toml.",
                "Version Catalog"
            )
            return
        }

        when (target) {
            is DependencyTarget -> handleDependency(project, catalog, target)
            is PluginTarget -> handlePlugin(project, catalog, target)
        }
    }

    private fun handleDependency(
        project: Project,
        catalog: com.intellij.openapi.vfs.VirtualFile,
        target: DependencyTarget
    ) {
        val defaultAlias = VersionCatalog.defaultAlias(target.coordinate)
        val alias = promptAlias(project, defaultAlias, CatalogSection.LIBRARIES, catalog) ?: return
        val entry = LibraryEntry(
            alias = alias,
            module = "${target.coordinate.group}:${target.coordinate.name}",
            version = target.coordinate.version,
        )

        WriteCommandAction.runWriteCommandAction(project, "Move Dependency to Version Catalog", null, {
            VersionCatalog.addEntry(catalog, CatalogSection.LIBRARIES, entry.toToml())
            val factory = KtPsiFactory(project)
            val accessor = "libs.${VersionCatalog.accessor(alias)}"
            val inner = if (target.wrapper != null) "${target.wrapper}($accessor)" else accessor
            val replacement = factory.createExpression("${target.configuration}($inner)")
            target.call.replace(replacement)
        })
    }

    private fun handlePlugin(project: Project, catalog: com.intellij.openapi.vfs.VirtualFile, target: PluginTarget) {
        val defaultAlias = VersionCatalog.defaultPluginAlias(target.declaration.id)
        val alias = promptAlias(project, defaultAlias, CatalogSection.PLUGINS, catalog) ?: return
        val entry = PluginEntry(alias, target.declaration.id, target.declaration.version)

        WriteCommandAction.runWriteCommandAction(project, "Move Plugin to Version Catalog", null, {
            VersionCatalog.addEntry(catalog, CatalogSection.PLUGINS, entry.toToml())
            val factory = KtPsiFactory(project)
            val replacement = factory.createExpression("alias(libs.plugins.${VersionCatalog.accessor(alias)})")
            target.declaration.replaceTarget.replace(replacement)
        })
    }

    private fun promptAlias(
        project: Project,
        default: String,
        section: CatalogSection,
        catalog: com.intellij.openapi.vfs.VirtualFile,
    ): String? {
        val validator = object : InputValidatorEx {
            override fun getErrorText(inputString: String?): String? {
                val value = inputString.orEmpty()
                if (value.isBlank()) return "Alias must not be empty"
                if (!value.matches(Regex("[a-z][a-zA-Z0-9._-]*"))) {
                    return "Use lowercase letters, digits, '.', '_' or '-'"
                }
                if (VersionCatalog.aliasExists(catalog, section, value)) {
                    return "Alias already exists in $section"
                }
                return null
            }

            override fun checkInput(inputString: String?): Boolean = getErrorText(inputString) == null
            override fun canClose(inputString: String?): Boolean = checkInput(inputString)
        }
        return Messages.showInputDialog(
            project,
            "Alias for ${section.header}:",
            "Move to libs.versions.toml",
            null,
            default,
            validator,
        )?.takeIf { it.isNotBlank() }?.replace('.', '-')
    }

    private fun findTarget(element: PsiElement): ExtractTarget? {
        val binary = PsiTreeUtil.getParentOfType(element, KtBinaryExpression::class.java, false)
        if (binary != null && GradleDsl.enclosingBlock(binary) == GradleBlock.PLUGINS) {
            pluginDeclarationFromBinary(binary)?.let { return PluginTarget(it) }
        }

        val call = outermostCall(element) ?: return null
        when (GradleDsl.enclosingBlock(call)) {
            GradleBlock.DEPENDENCIES -> {
                val configuration = call.calleeExpression?.text ?: return null
                if (!GradleDsl.isDependencyConfigurationName(configuration)) return null
                val (coordinateSource, wrapper) = unwrapWrapperCall(call)
                val coordinate = GradleDsl.shorthandCoordinateFromCall(coordinateSource)
                    ?: GradleDsl.namedArgsCoordinateFromCall(coordinateSource)
                    ?: return null
                return DependencyTarget(call, configuration, wrapper, coordinate)
            }

            GradleBlock.PLUGINS -> {
                pluginDeclarationFromCall(call)?.let { return PluginTarget(it) }
                return null
            }

            GradleBlock.OTHER -> return null
        }
    }

    private fun unwrapWrapperCall(call: KtCallExpression): Pair<KtCallExpression, String?> {
        val args = call.valueArguments
        if (args.size != 1) return call to null
        val inner = args[0].getArgumentExpression() as? KtCallExpression ?: return call to null
        val name = inner.calleeExpression?.text ?: return call to null
        return inner to name
    }

    private fun outermostCall(element: PsiElement): KtCallExpression? {
        val direct = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return null
        var candidate: KtCallExpression = direct
        var parent = PsiTreeUtil.getParentOfType(direct, KtCallExpression::class.java, true)
        while (parent != null) {
            val callee = parent.calleeExpression?.text ?: break
            if (GradleDsl.isDependencyConfigurationName(callee) || callee == "id") {
                candidate = parent
            }
            parent = PsiTreeUtil.getParentOfType(parent, KtCallExpression::class.java, true)
        }
        return candidate
    }
}

private sealed interface ExtractTarget

private data class DependencyTarget(
    val call: KtCallExpression,
    val configuration: String,
    val wrapper: String?,
    val coordinate: Coordinate,
) : ExtractTarget

private data class PluginTarget(
    val declaration: PluginDeclaration,
) : ExtractTarget
