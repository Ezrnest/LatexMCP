package com.github.ezrnest.latexmcp.tools.labels

import com.github.ezrnest.latexmcp.tools.common.ProjectFileResolver
import com.github.ezrnest.latexmcp.tools.common.ToolExecutionHelper
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import nl.hannahsten.texifyidea.util.labels.LatexLabelUtil

/**
 * Input for [LabelLocationsTool].
 */
internal data class LabelLocationsToolParams(
    val projectPath: String,
    val mainTex: String,
    val label: String,
    val includeReferences: Boolean = true,
)

/**
 * Label definition/reference lookup result in fileset scope.
 */
internal data class LabelLocationsToolResult(
    val projectPath: String,
    val mainTex: String,
    val label: String,
    val definitions: List<LabelLocation>,
    val references: List<LabelLocation>,
    val source: String = "texify-labels",
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

    /**
     * Returns deterministic, de-duplicated label locations sorted by file and offset.
     */
    fun execute(params: LabelLocationsToolParams): LabelLocationsToolResult {
        val resolved = ToolExecutionHelper.resolveAndPrepare(
            projectPath = params.projectPath,
            texFile = params.mainTex,
        )
        val projectRoot = resolved.projectPath
        val project = resolved.project
        val mainFile = resolved.targetFile

        val (definitions, references) = ReadAction.compute<Pair<List<LabelLocation>, List<LabelLocation>>, RuntimeException> {
            val psiMainFile = PsiManager.getInstance(project).findFile(mainFile)
                ?: throw IllegalArgumentException("Cannot resolve PSI file for: ${mainFile.path}")

            val definitionParams = LatexLabelUtil.getLabelParamsByName(
                label = params.label,
                file = psiMainFile,
                withExternal = true,
                withCustomized = true,
            )

            val definitionLocations = definitionParams
                .mapNotNull { toLocation(it, projectRoot, project) }
                .distinctBy { "${it.file}:${it.offset}" }
                .sortedWith(compareBy<LabelLocation> { it.file }.thenBy { it.offset })

            if (!params.includeReferences || definitionParams.isEmpty()) {
                return@compute definitionLocations to emptyList()
            }

            val scope = LatexProjectStructure.getFilesetScopeFor(psiMainFile, onlyTexFiles = true)
            val referenceLocations = definitionParams
                .flatMap { param ->
                    ReferencesSearch.search(param, scope)
                        .findAll()
                        .mapNotNull { ref -> toLocation(ref.element, projectRoot, project) }
                }
                .distinctBy { "${it.file}:${it.offset}" }
                .sortedWith(compareBy<LabelLocation> { it.file }.thenBy { it.offset })

            definitionLocations to referenceLocations
        }

        return LabelLocationsToolResult(
            projectPath = projectRoot,
            mainTex = resolved.relativeTexFile,
            label = params.label,
            definitions = definitions,
            references = references,
        )
    }

    private fun toLocation(element: PsiElement, projectRoot: String, project: com.intellij.openapi.project.Project): LabelLocation? {
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
}
