package com.github.bgalek.movetoversioncatalog

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

enum class GradleBlock { DEPENDENCIES, PLUGINS, OTHER }

object GradleDsl {

    fun enclosingBlock(element: PsiElement): GradleBlock {
        var current: PsiElement = element
        while (true) {
            val literal = PsiTreeUtil.getParentOfType(current, KtFunctionLiteral::class.java)
                ?: return GradleBlock.OTHER
            val lambda = literal.parent as? KtLambdaExpression ?: return GradleBlock.OTHER
            val lambdaArg = lambda.parent as? KtLambdaArgument ?: return GradleBlock.OTHER
            val call = lambdaArg.parent as? KtCallExpression ?: return GradleBlock.OTHER
            when (call.calleeExpression?.text) {
                "dependencies" -> return GradleBlock.DEPENDENCIES
                "plugins" -> return GradleBlock.PLUGINS
            }
            current = call
        }
    }

    fun plainStringValue(arg: KtValueArgument?): String? {
        val expr = arg?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        val entries = expr.entries
        if (entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return entries.joinToString("") { it.text }
    }

    fun shorthandCoordinateFromCall(call: KtCallExpression): Coordinate? {
        val args = call.valueArguments
        if (args.size != 1 || args[0].getArgumentName() != null) return null
        val raw = plainStringValue(args[0]) ?: return null
        return Coordinate.parse(raw)
    }

    fun namedArgsCoordinateFromCall(call: KtCallExpression): Coordinate? {
        val args = call.valueArguments
        if (args.isEmpty() || args.any { it.getArgumentName() == null }) return null
        val map = args.associateBy(
            { it.getArgumentName()?.asName?.identifier },
            { plainStringValue(it) },
        )
        val known = setOf("group", "name", "version")
        if (map.keys.any { it !in known }) return null
        val group = map["group"] ?: return null
        val name = map["name"] ?: return null
        val version = map["version"]
        return Coordinate(group, name, version)
    }

    fun isDependencyConfigurationName(name: String): Boolean = name.matches(CONFIG_NAME_REGEX)

    private val CONFIG_NAME_REGEX = Regex("^[A-Za-z]+$")
}

data class PluginDeclaration(
    val id: String,
    val version: String?,
    val replaceTarget: PsiElement,
)

/**
 * Parses `id("foo.bar") version "1.2.3"` (with or without the version infix).
 * Returns the element to replace (the whole binary / call expression).
 */
fun pluginDeclarationFromBinary(binary: KtBinaryExpression): PluginDeclaration? {
    val op = binary.operationReference.getReferencedName()
    if (op != "version") return null
    val left = binary.left as? KtCallExpression ?: return null
    val id = idCallLiteral(left) ?: return null
    val versionExpr = binary.right as? KtStringTemplateExpression ?: return null
    val versionEntries = versionExpr.entries
    if (versionEntries.any { it !is KtLiteralStringTemplateEntry }) return null
    val version = versionEntries.joinToString("") { it.text }
    return PluginDeclaration(id, version, binary)
}

fun pluginDeclarationFromCall(call: KtCallExpression): PluginDeclaration? {
    val id = idCallLiteral(call) ?: return null
    return PluginDeclaration(id, null, call)
}

private fun idCallLiteral(call: KtCallExpression): String? {
    val callee = call.calleeExpression as? KtNameReferenceExpression ?: return null
    if (callee.getReferencedName() != "id") return null
    val args = call.valueArguments
    if (args.size != 1) return null
    return GradleDsl.plainStringValue(args[0])
}
