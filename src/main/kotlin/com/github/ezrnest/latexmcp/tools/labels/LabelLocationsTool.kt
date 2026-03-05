package com.github.ezrnest.latexmcp.tools.labels

import com.github.ezrnest.latexmcp.tools.common.ProjectFileResolver
import com.github.ezrnest.latexmcp.tools.common.ToolExecutionHelper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import nl.hannahsten.texifyidea.util.labels.LatexLabelUtil
import java.util.regex.Pattern

/**
 * Input for [LabelLocationsTool].
 */
internal data class LabelLocationsToolParams(
    val projectPath: String,
    val mainTex: String? = null,
    val texFile: String? = null,
    val scope: String = "fileset",
    val label: String? = null,
    val labelPattern: String? = null,
    val patternMode: String = "auto",
    val caseSensitive: Boolean = true,
    val includeReferences: Boolean? = null,
    val limit: Int = 1000,
)

/**
 * Label definition/reference lookup result in fileset or single-document scope.
 */
internal data class LabelLocationsToolResult(
    val projectPath: String,
    val mainTex: String,
    val label: String,
    val scope: String,
    val targetContext: String,
    val patternMode: String,
    val includeReferences: Boolean,
    val matchedLabels: List<String>,
    val truncated: Boolean,
    val limit: Int,
    val matches: List<LabelMatchResult>,
    val definitions: List<LabelLocation>,
    val references: List<LabelLocation>,
    val source: String = "texify-labels",
)

/**
 * Per-label grouped lookup result.
 */
internal data class LabelMatchResult(
    val label: String,
    val definitions: List<LabelLocation>,
    val references: List<LabelLocation>,
)

/**
 * PSI location encoded for MCP responses.
 */
internal data class LabelLocation(
    val file: String,
    val line: Int,
    val column: Int,
    val offset: Int,
)

/**
 * Resolves label definitions and, optionally, all references in the same TeXiFy fileset.
 */
internal object LabelLocationsTool {

    private const val SCOPE_FILESET = "fileset"
    private const val SCOPE_SINGLE = "single_document"
    private const val MODE_AUTO = "auto"
    private const val MODE_LITERAL = "literal"
    private const val MODE_WILDCARD = "wildcard"
    private const val MODE_REGEX = "regex"

    private data class SearchSpec(
        val scope: String,
        val query: String,
        val mode: String,
        val includeReferences: Boolean,
        val caseSensitive: Boolean,
        val limit: Int,
    )

    /**
     * Returns deterministic, de-duplicated label locations sorted by file and offset.
     */
    fun execute(params: LabelLocationsToolParams): LabelLocationsToolResult {
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
        val projectRoot = resolved.projectPath
        val project = resolved.project
        val targetFile = resolved.targetFile

        val (
            matchedLabels,
            truncated,
            matches,
            definitions,
            references,
        ) = ReadAction.compute<ResultBundle, RuntimeException> {
            val psiMainFile = PsiManager.getInstance(project).findFile(targetFile)
                ?: throw IllegalArgumentException("Cannot resolve PSI file for: ${targetFile.path}")

            val definitionsByLabel = when {
                spec.mode == MODE_LITERAL && spec.caseSensitive -> {
                    resolveLiteralDefinitions(spec.query, spec.scope, targetFile, psiMainFile)
                }
                else -> {
                    resolvePatternDefinitions(spec, targetFile, psiMainFile)
                }
            }

            val allMatched = definitionsByLabel.keys.sorted()
            val limited = allMatched.take(spec.limit)
            val wasTruncated = allMatched.size > limited.size
            val referenceScope = referenceSearchScope(spec.scope, project, targetFile, psiMainFile)

            val grouped = limited.map { label ->
                val defParams = definitionsByLabel[label].orEmpty()
                val definitionLocations = defParams
                    .mapNotNull { toLocation(it, projectRoot, project) }
                    .sortedUnique()

                val referenceLocations = if (!spec.includeReferences || defParams.isEmpty()) {
                    emptyList()
                }
                else {
                    defParams
                        .flatMap { param ->
                            ReferencesSearch.search(param, referenceScope)
                                .findAll()
                                .mapNotNull { ref -> toLocation(ref.element, projectRoot, project) }
                        }
                        .sortedUnique()
                }

                LabelMatchResult(
                    label = label,
                    definitions = definitionLocations,
                    references = referenceLocations,
                )
            }

            val allDefinitions = grouped
                .flatMap { it.definitions }
                .sortedUnique()
            val allReferences = grouped
                .flatMap { it.references }
                .sortedUnique()

            ResultBundle(
                matchedLabels = limited,
                truncated = wasTruncated,
                matches = grouped,
                definitions = allDefinitions,
                references = allReferences,
            )
        }

        return LabelLocationsToolResult(
            projectPath = projectRoot,
            mainTex = resolved.relativeTexFile,
            label = spec.query,
            scope = spec.scope,
            targetContext = resolved.relativeTexFile,
            patternMode = spec.mode,
            includeReferences = spec.includeReferences,
            matchedLabels = matchedLabels,
            truncated = truncated,
            limit = spec.limit,
            matches = matches,
            definitions = definitions,
            references = references,
        )
    }

    private data class ResultBundle(
        val matchedLabels: List<String>,
        val truncated: Boolean,
        val matches: List<LabelMatchResult>,
        val definitions: List<LabelLocation>,
        val references: List<LabelLocation>,
    )

    private fun validateAndNormalize(params: LabelLocationsToolParams): SearchSpec {
        val scope = params.scope.lowercase()
        if (scope != SCOPE_FILESET && scope != SCOPE_SINGLE) {
            throw IllegalArgumentException("scope must be one of: fileset, single_document")
        }

        val query = (params.labelPattern ?: params.label ?: "").trim()
        if (query.isEmpty()) {
            throw IllegalArgumentException("labelPattern or label must not be blank")
        }

        val rawMode = params.patternMode.lowercase()
        val mode = when (rawMode) {
            MODE_AUTO, MODE_LITERAL, MODE_WILDCARD, MODE_REGEX -> rawMode
            else -> throw IllegalArgumentException("patternMode must be one of: auto, literal, wildcard, regex")
        }
        val normalizedMode = if (mode == MODE_AUTO) detectMode(query) else mode

        if (params.limit <= 0) {
            throw IllegalArgumentException("limit must be positive")
        }

        val includeReferences = params.includeReferences ?: (normalizedMode == MODE_LITERAL)

        return SearchSpec(
            scope = scope,
            query = query,
            mode = normalizedMode,
            includeReferences = includeReferences,
            caseSensitive = params.caseSensitive,
            limit = params.limit,
        )
    }

    private fun resolveLiteralDefinitions(
        query: String,
        scope: String,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
        contextFile: PsiFile,
    ): Map<String, List<PsiElement>> {
        val defs = LatexLabelUtil.getLabelParamsByName(
            label = query,
            file = contextFile,
            withExternal = true,
            withCustomized = true,
        ).filter { def ->
            if (scope != SCOPE_SINGLE) {
                true
            }
            else {
                def.containingFile?.virtualFile == targetFile
            }
        }
        return if (defs.isEmpty()) emptyMap() else mapOf(query to defs)
    }

    private fun resolvePatternDefinitions(
        spec: SearchSpec,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
        contextFile: PsiFile,
    ): Map<String, List<PsiElement>> {
        val matcher = buildMatcher(spec.query, spec.mode, spec.caseSensitive)
        val map = linkedMapOf<String, MutableList<PsiElement>>()

        LatexLabelUtil.processAllLabelsInFileSet(
            file = contextFile,
            withExternal = true,
            withCustomized = true,
        ) { label, container, param ->
            if (spec.scope == SCOPE_SINGLE && container.containingFile?.virtualFile != targetFile) {
                return@processAllLabelsInFileSet
            }
            if (!matcher(label)) {
                return@processAllLabelsInFileSet
            }
            map.getOrPut(label) { mutableListOf() }.add(param)
        }

        return map
    }

    private fun referenceSearchScope(
        scope: String,
        project: Project,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
        contextFile: PsiFile,
    ): GlobalSearchScope {
        return if (scope == SCOPE_SINGLE) {
            GlobalSearchScope.fileScope(project, targetFile)
        }
        else {
            LatexProjectStructure.getFilesetScopeFor(contextFile, onlyTexFiles = true)
        }
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

    private fun buildMatcher(query: String, mode: String, caseSensitive: Boolean): (String) -> Boolean {
        return when (mode) {
            MODE_LITERAL -> literalMatcher(query, caseSensitive)
            MODE_WILDCARD -> wildcardMatcher(query, caseSensitive)
            MODE_REGEX -> regexMatcher(query, caseSensitive)
            else -> literalMatcher(query, caseSensitive)
        }
    }

    private fun literalMatcher(query: String, caseSensitive: Boolean): (String) -> Boolean {
        if (caseSensitive) {
            return { candidate -> candidate == query }
        }
        val expected = query.lowercase()
        return { candidate -> candidate.lowercase() == expected }
    }

    private fun wildcardMatcher(query: String, caseSensitive: Boolean): (String) -> Boolean {
        val regex = buildString {
            append('^')
            query.forEach { ch ->
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

    private fun regexMatcher(query: String, caseSensitive: Boolean): (String) -> Boolean {
        val trimmed = query.trim()
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

    private fun toLocation(element: PsiElement, projectRoot: String, project: Project): LabelLocation? {
        val containingFile = element.containingFile ?: return null
        val virtualFile = containingFile.virtualFile ?: return null
        val relativePath = ProjectFileResolver.toProjectRelativePath(virtualFile, project, projectRoot) ?: return null

        val offset = element.textOffset
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val line = if (document != null) document.getLineNumber(offset) + 1 else 1
        val column = if (document != null) offset - document.getLineStartOffset(line - 1) + 1 else 1

        return LabelLocation(
            file = relativePath,
            line = line,
            column = column,
            offset = offset,
        )
    }

    private fun List<LabelLocation>.sortedUnique(): List<LabelLocation> =
        distinctBy { "${it.file}:${it.offset}" }
            .sortedWith(compareBy<LabelLocation> { it.file }.thenBy { it.offset })
}
