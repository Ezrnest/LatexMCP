package com.github.ezrnest.latexmcp.tools.labels

import com.github.ezrnest.latexmcp.tools.common.ProjectFileResolver
import com.github.ezrnest.latexmcp.tools.common.ToolExecutionHelper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import nl.hannahsten.texifyidea.util.labels.LatexLabelUtil

internal data class RenameLabelSafeToolParams(
    val projectPath: String,
    val mainTex: String,
    val oldLabel: String,
    val newLabel: String,
    val applyChanges: Boolean = true,
)

internal data class RenameLabelSafeToolResult(
    val projectPath: String,
    val mainTex: String,
    val oldLabel: String,
    val newLabel: String,
    val applyChanges: Boolean,
    val definitionsFound: Int,
    val referencesFound: Int,
    val plannedEdits: Int,
    val appliedEdits: Int,
    val changedFiles: List<String>,
    val edits: List<RenameLabelEdit>,
    val source: String = "texify-label-rename",
)

internal data class RenameLabelEdit(
    val kind: String,
    val file: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val applied: Boolean,
)

internal object RenameLabelSafeTool {

    private data class PlannedEdit(
        val kind: String,
        val file: VirtualFile,
        val relativePath: String,
        val startOffset: Int,
        val endOffset: Int,
        val oldText: String,
        val line: Int,
        val column: Int,
    )

    private data class Plan(
        val definitionsFound: Int,
        val referencesFound: Int,
        val edits: List<PlannedEdit>,
    )

    fun execute(params: RenameLabelSafeToolParams): RenameLabelSafeToolResult {
        val oldLabel = params.oldLabel.trim()
        val newLabel = params.newLabel.trim()
        if (oldLabel.isBlank()) {
            throw IllegalArgumentException("oldLabel must not be blank")
        }
        if (newLabel.isBlank()) {
            throw IllegalArgumentException("newLabel must not be blank")
        }

        val resolved = ToolExecutionHelper.resolveAndPrepare(
            projectPath = params.projectPath,
            texFile = params.mainTex,
        )
        val projectRoot = resolved.projectPath
        val project = resolved.project
        val mainFile = resolved.targetFile

        val plan = ReadAction.compute<Plan, RuntimeException> {
            val psiMainFile = PsiManager.getInstance(project).findFile(mainFile)
                ?: throw IllegalArgumentException("Cannot resolve PSI file for: ${mainFile.path}")

            val definitions = LatexLabelUtil.getLabelParamsByName(
                label = oldLabel,
                file = psiMainFile,
                withExternal = false,
                withCustomized = true,
            )
            if (definitions.isEmpty()) {
                throw IllegalArgumentException("No label definition found for '$oldLabel'")
            }

            if (oldLabel != newLabel) {
                val existingNew = LatexLabelUtil.getLabelParamsByName(
                    label = newLabel,
                    file = psiMainFile,
                    withExternal = false,
                    withCustomized = true,
                )
                if (existingNew.isNotEmpty()) {
                    throw IllegalArgumentException("Target label '$newLabel' already exists in fileset")
                }
            }

            val scope = LatexProjectStructure.getFilesetScopeFor(psiMainFile, onlyTexFiles = true)
            val references = definitions
                .flatMap { defParam -> ReferencesSearch.search(defParam, scope).findAll().map { it.element } }

            val definitionEdits = definitions.mapNotNull {
                toPlannedEdit(
                    kind = "definition",
                    element = it,
                    expectedText = oldLabel,
                    projectRoot = projectRoot,
                    project = project,
                )
            }
            val referenceEdits = references.mapNotNull {
                toPlannedEdit(
                    kind = "reference",
                    element = it,
                    expectedText = oldLabel,
                    projectRoot = projectRoot,
                    project = project,
                )
            }

            val dedupedEdits = (definitionEdits + referenceEdits)
                .distinctBy { "${it.relativePath}:${it.startOffset}:${it.endOffset}" }
                .sortedWith(compareBy<PlannedEdit> { it.relativePath }.thenBy { it.startOffset })

            Plan(
                definitionsFound = definitions.size,
                referencesFound = references.size,
                edits = dedupedEdits,
            )
        }

        val appliedKeys = if (params.applyChanges && oldLabel != newLabel) {
            applyEdits(project, plan.edits, newLabel)
        }
        else {
            emptySet()
        }

        val editResults = plan.edits.map { edit ->
            RenameLabelEdit(
                kind = edit.kind,
                file = edit.relativePath,
                line = edit.line,
                column = edit.column,
                offset = edit.startOffset,
                applied = "${edit.relativePath}:${edit.startOffset}:${edit.endOffset}" in appliedKeys,
            )
        }
        val changedFiles = editResults
            .filter { it.applied }
            .map { it.file }
            .distinct()
            .sorted()

        return RenameLabelSafeToolResult(
            projectPath = projectRoot,
            mainTex = resolved.relativeTexFile,
            oldLabel = oldLabel,
            newLabel = newLabel,
            applyChanges = params.applyChanges,
            definitionsFound = plan.definitionsFound,
            referencesFound = plan.referencesFound,
            plannedEdits = plan.edits.size,
            appliedEdits = appliedKeys.size,
            changedFiles = changedFiles,
            edits = editResults,
        )
    }

    private fun applyEdits(project: com.intellij.openapi.project.Project, edits: List<PlannedEdit>, newLabel: String): Set<String> {
        if (edits.isEmpty()) return emptySet()

        val applied = linkedSetOf<String>()
        val grouped = edits.groupBy { it.file }

        WriteCommandAction.runWriteCommandAction(project) {
            grouped.forEach { (file, fileEdits) ->
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@forEach
                val sorted = fileEdits.sortedByDescending { it.startOffset }
                sorted.forEach { edit ->
                    val currentText = document.charsSequence.subSequence(edit.startOffset, edit.endOffset).toString()
                    if (currentText == edit.oldText) {
                        document.replaceString(edit.startOffset, edit.endOffset, newLabel)
                        applied.add("${edit.relativePath}:${edit.startOffset}:${edit.endOffset}")
                    }
                }
                FileDocumentManager.getInstance().saveDocument(document)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        return applied
    }

    private fun toPlannedEdit(
        kind: String,
        element: PsiElement,
        expectedText: String,
        projectRoot: String,
        project: com.intellij.openapi.project.Project,
    ): PlannedEdit? {
        if (element.text != expectedText) return null

        val containingFile = element.containingFile ?: return null
        val file = containingFile.virtualFile ?: return null
        val relativePath = ProjectFileResolver.toProjectRelativePath(file, project, projectRoot) ?: return null

        val range = element.textRange ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
        val line = if (document != null) document.getLineNumber(range.startOffset) + 1 else 1
        val column = if (document != null) range.startOffset - document.getLineStartOffset(line - 1) + 1 else 1

        return PlannedEdit(
            kind = kind,
            file = file,
            relativePath = relativePath,
            startOffset = range.startOffset,
            endOffset = range.endOffset,
            oldText = expectedText,
            line = line,
            column = column,
        )
    }
}
