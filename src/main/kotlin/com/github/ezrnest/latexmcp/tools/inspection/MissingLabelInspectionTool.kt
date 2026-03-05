package com.github.ezrnest.latexmcp.tools.inspection

import com.github.ezrnest.latexmcp.tools.common.ProjectFileResolver
import com.github.ezrnest.latexmcp.tools.common.ToolExecutionHelper
import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.hannahsten.texifyidea.index.LatexDefinitionService
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import nl.hannahsten.texifyidea.inspections.latex.codestyle.LatexMissingLabelInspection

/**
 * Input for [MissingLabelInspectionTool].
 *
 * Scope-specific required fields:
 * - `scope=fileset`: `mainTex`
 * - `scope=single_document`: `texFile`
 */
internal data class MissingLabelInspectionToolParams(
    val projectPath: String,
    val scope: String = "fileset",
    val mainTex: String? = null,
    val texFile: String? = null,
    val limit: Int = 1000,
)

/**
 * Missing-label inspection result collected from TeXiFy inspection descriptors.
 */
internal data class MissingLabelInspectionToolResult(
    val projectPath: String,
    val scope: String,
    val targetContext: String,
    val count: Int,
    val truncated: Boolean,
    val limit: Int,
    val issues: List<MissingLabelIssue>,
    val source: String = "texify-inspection-missing-label",
)

/**
 * One missing-label issue location.
 */
internal data class MissingLabelIssue(
    val file: String,
    val line: Int,
    val column: Int,
    val offset: Int,
)

/**
 * Runs TeXiFy [LatexMissingLabelInspection] and exports locations in MCP-friendly form.
 */
internal object MissingLabelInspectionTool {

    private const val SCOPE_FILESET = "fileset"
    private const val SCOPE_SINGLE = "single_document"

    private data class SearchSpec(
        val scope: String,
        val limit: Int,
    )

    /**
     * Executes missing-label inspection in fileset or single-document scope.
     */
    fun execute(params: MissingLabelInspectionToolParams): MissingLabelInspectionToolResult {
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

        refreshDefinitions(project)

        val psiFiles = ReadAction.compute<List<PsiFile>, RuntimeException> {
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

        val issues = mutableListOf<MissingLabelIssue>()
        val seen = linkedSetOf<String>()
        var truncated = false

        ReadAction.run<RuntimeException> {
            val inspection = LatexMissingLabelInspection()
            val inspectionManager = InspectionManager.getInstance(project)

            for (psiFile in psiFiles) {
                val descriptors = inspection.checkFile(psiFile, inspectionManager, true) ?: emptyArray()
                for (descriptor in descriptors) {
                    val element = descriptor.psiElement ?: continue
                    val containingFile = element.containingFile ?: psiFile
                    val virtualFile = containingFile.virtualFile ?: continue
                    val relativePath = ProjectFileResolver.toProjectRelativePath(virtualFile, project, projectRoot) ?: continue

                    val offset = element.textOffset
                    val dedupeKey = "$relativePath:$offset"
                    if (!seen.add(dedupeKey)) {
                        continue
                    }

                    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
                    issues.add(
                        MissingLabelIssue(
                            file = relativePath,
                            line = lineOf(offset, document),
                            column = columnOf(offset, document),
                            offset = offset,
                        ),
                    )

                    if (issues.size >= spec.limit) {
                        truncated = true
                        return@run
                    }
                }
            }
        }

        return MissingLabelInspectionToolResult(
            projectPath = projectRoot,
            scope = spec.scope,
            targetContext = resolved.relativeTexFile,
            count = issues.size,
            truncated = truncated,
            limit = spec.limit,
            issues = issues,
        )
    }

    private fun validateAndNormalize(params: MissingLabelInspectionToolParams): SearchSpec {
        val scope = params.scope.lowercase()
        if (scope != SCOPE_FILESET && scope != SCOPE_SINGLE) {
            throw IllegalArgumentException("scope must be one of: fileset, single_document")
        }
        if (params.limit <= 0) {
            throw IllegalArgumentException("limit must be positive")
        }
        return SearchSpec(
            scope = scope,
            limit = params.limit,
        )
    }

    private fun refreshDefinitions(project: Project) {
        runBlocking {
            withTimeout(10_000L) {
                LatexDefinitionService.getInstance(project).ensureRefreshAll()
            }
        }
    }

    private fun lineOf(offset: Int, document: Document?): Int =
        if (document == null) 1 else document.getLineNumber(offset) + 1

    private fun columnOf(offset: Int, document: Document?): Int =
        if (document == null) 1 else offset - document.getLineStartOffset(lineOf(offset, document) - 1) + 1
}
