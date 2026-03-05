package com.github.ezrnest.latexmcp.mcp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import nl.hannahsten.texifyidea.psi.LatexCommands
import nl.hannahsten.texifyidea.psi.LatexEnvironment
import nl.hannahsten.texifyidea.psi.getEnvironmentName
import nl.hannahsten.texifyidea.psi.traverseCommands
import nl.hannahsten.texifyidea.util.parser.traverseTyped
import java.util.regex.Pattern

internal data class StructuredSearchToolParams(
    val projectPath: String,
    val scope: String = "fileset",
    val mainTex: String? = null,
    val texFile: String? = null,
    val namePattern: String,
    val patternMode: String = "auto",
    val type: String = "both",
    val caseSensitive: Boolean = true,
    val limit: Int = 1000,
)

internal data class StructuredSearchToolResult(
    val projectPath: String,
    val scope: String,
    val targetContext: String,
    val count: Int,
    val truncated: Boolean,
    val limit: Int,
    val results: List<StructuredSearchMatch>,
    val source: String = "texify-structured-search",
)

internal data class StructuredSearchMatch(
    val type: String,
    val name: String,
    val file: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val text: String,
)

internal object StructuredSearchTool {

    private const val SCOPE_FILESET = "fileset"
    private const val SCOPE_SINGLE = "single_document"
    private const val TYPE_BOTH = "both"
    private const val TYPE_COMMAND = "command"
    private const val TYPE_ENV = "environment"
    private const val MODE_AUTO = "auto"
    private const val MODE_LITERAL = "literal"
    private const val MODE_WILDCARD = "wildcard"
    private const val MODE_REGEX = "regex"

    private data class SearchSpec(
        val scope: String,
        val type: String,
        val mode: String,
        val normalizedPattern: String,
        val caseSensitive: Boolean,
        val limit: Int,
    )

    fun execute(params: StructuredSearchToolParams): StructuredSearchToolResult {
        val spec = validateAndNormalize(params)

        val contextTex = when (spec.scope) {
            SCOPE_FILESET -> params.mainTex
                ?: throw IllegalArgumentException("mainTex is required when scope=fileset")
            SCOPE_SINGLE -> params.texFile
                ?: throw IllegalArgumentException("texFile is required when scope=single_document")
            else -> throw IllegalArgumentException("Unsupported scope: ${spec.scope}")
        }

        val resolved = ToolExecutionHelper.resolveAndPrepare(
            projectPath = params.projectPath,
            texFile = contextTex,
        )

        val project = resolved.project
        val projectRoot = resolved.projectPath
        val targetFile = resolved.targetFile

        val files = ReadAction.compute<List<PsiFile>, RuntimeException> {
            val psiManager = PsiManager.getInstance(project)
            val targets = when (spec.scope) {
                SCOPE_SINGLE -> listOf(targetFile)
                SCOPE_FILESET -> {
                    val data = LatexProjectStructure.getFilesetDataFor(targetFile, project)
                    data?.relatedFiles?.toList() ?: listOf(targetFile)
                }
                else -> listOf(targetFile)
            }

            targets.mapNotNull { psiManager.findFile(it) }
        }

        val matcher = buildMatcher(spec)
        val results = mutableListOf<StructuredSearchMatch>()
        var truncated = false

        ReadAction.run<RuntimeException> {
            for (psiFile in files) {
                val relativePath = ProjectFileResolver.toProjectRelativePath(psiFile.virtualFile ?: continue, project, projectRoot) ?: continue
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

                if (spec.type == TYPE_BOTH || spec.type == TYPE_COMMAND) {
                    for (command in psiFile.traverseCommands()) {
                        val commandName = command.name ?: continue
                        val withSlash = normalizeCommandName(commandName)
                        val withoutSlash = removeSlash(withSlash)
                        if (!matchesCommand(matcher, withSlash, withoutSlash)) continue
                        results.add(
                            toMatch(
                                type = TYPE_COMMAND,
                                name = withSlash,
                                elementText = command.text,
                                offset = command.textOffset,
                                file = relativePath,
                                document = document,
                            ),
                        )
                        if (results.size >= spec.limit) {
                            truncated = true
                            return@run
                        }
                    }
                }

                if (spec.type == TYPE_BOTH || spec.type == TYPE_ENV) {
                    for (environment in psiFile.traverseTyped<LatexEnvironment>()) {
                        val envName = environment.getEnvironmentName()
                        if (!matcher(envName)) continue
                        results.add(
                            toMatch(
                                type = TYPE_ENV,
                                name = envName,
                                elementText = environment.text,
                                offset = environment.textOffset,
                                file = relativePath,
                                document = document,
                            ),
                        )
                        if (results.size >= spec.limit) {
                            truncated = true
                            return@run
                        }
                    }
                }
            }
        }

        return StructuredSearchToolResult(
            projectPath = projectRoot,
            scope = spec.scope,
            targetContext = resolved.relativeTexFile,
            count = results.size,
            truncated = truncated,
            limit = spec.limit,
            results = results,
        )
    }

    private fun toMatch(
        type: String,
        name: String,
        elementText: String,
        offset: Int,
        file: String,
        document: Document?,
    ): StructuredSearchMatch {
        val line = if (document == null) 1 else document.getLineNumber(offset) + 1
        val column = if (document == null) 1 else offset - document.getLineStartOffset(line - 1) + 1
        return StructuredSearchMatch(
            type = type,
            name = name,
            file = file,
            line = line,
            column = column,
            offset = offset,
            text = elementText,
        )
    }

    private fun validateAndNormalize(params: StructuredSearchToolParams): SearchSpec {
        val scope = params.scope.lowercase()
        if (scope != SCOPE_FILESET && scope != SCOPE_SINGLE) {
            throw IllegalArgumentException("scope must be one of: fileset, single_document")
        }

        val type = params.type.lowercase()
        if (type != TYPE_BOTH && type != TYPE_COMMAND && type != TYPE_ENV) {
            throw IllegalArgumentException("type must be one of: both, command, environment")
        }

        val rawMode = params.patternMode.lowercase()
        val mode = when (rawMode) {
            MODE_AUTO, MODE_LITERAL, MODE_WILDCARD, MODE_REGEX -> rawMode
            else -> throw IllegalArgumentException("patternMode must be one of: auto, literal, wildcard, regex")
        }

        val normalizedMode = if (mode == MODE_AUTO) detectMode(params.namePattern) else mode
        val pattern = params.namePattern.trim()
        if (pattern.isEmpty()) {
            throw IllegalArgumentException("namePattern must not be blank")
        }

        if (params.limit <= 0) {
            throw IllegalArgumentException("limit must be positive")
        }

        return SearchSpec(
            scope = scope,
            type = type,
            mode = normalizedMode,
            normalizedPattern = pattern,
            caseSensitive = params.caseSensitive,
            limit = params.limit,
        )
    }

    private fun detectMode(pattern: String): String {
        val trimmed = pattern.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("/") && trimmed.endsWith("/")) {
            return MODE_REGEX
        }
        if (trimmed.contains('*') || trimmed.contains('?')) {
            return MODE_WILDCARD
        }
        return MODE_LITERAL
    }

    private fun buildMatcher(spec: SearchSpec): (String) -> Boolean {
        return when (spec.mode) {
            MODE_LITERAL -> literalMatcher(spec.normalizedPattern, spec.caseSensitive)
            MODE_WILDCARD -> wildcardMatcher(spec.normalizedPattern, spec.caseSensitive)
            MODE_REGEX -> regexMatcher(spec.normalizedPattern, spec.caseSensitive)
            else -> literalMatcher(spec.normalizedPattern, spec.caseSensitive)
        }
    }

    private fun literalMatcher(pattern: String, caseSensitive: Boolean): (String) -> Boolean {
        if (caseSensitive) {
            return { candidate -> candidate == pattern }
        }
        val expected = pattern.lowercase()
        return { candidate -> candidate.lowercase() == expected }
    }

    private fun wildcardMatcher(pattern: String, caseSensitive: Boolean): (String) -> Boolean {
        val regex = buildString {
            append('^')
            pattern.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Pattern.quote(ch.toString()))
                }
            }
            append('$')
        }
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
        val compiled = Pattern.compile(regex, flags)
        return { candidate -> compiled.matcher(candidate).matches() }
    }

    private fun regexMatcher(pattern: String, caseSensitive: Boolean): (String) -> Boolean {
        val trimmed = pattern.trim()
        val body = if (trimmed.length >= 2 && trimmed.startsWith("/") && trimmed.endsWith("/")) {
            trimmed.substring(1, trimmed.length - 1)
        }
        else {
            trimmed
        }
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
        val compiled = runCatching { Pattern.compile(body, flags) }
            .getOrElse { throw IllegalArgumentException("Invalid regex pattern: ${it.message}") }
        return { candidate -> compiled.matcher(candidate).matches() }
    }

    private fun matchesCommand(matcher: (String) -> Boolean, withSlash: String, withoutSlash: String): Boolean =
        matcher(withSlash) || matcher(withoutSlash)

    private fun normalizeCommandName(name: String): String =
        if (name.startsWith("\\")) name else "\\$name"

    private fun removeSlash(name: String): String =
        if (name.startsWith("\\")) name.substring(1) else name
}
