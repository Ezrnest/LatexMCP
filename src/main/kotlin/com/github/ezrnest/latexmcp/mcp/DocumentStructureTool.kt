package com.github.ezrnest.latexmcp.mcp

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import nl.hannahsten.texifyidea.lang.predefined.CommandNames
import nl.hannahsten.texifyidea.psi.LatexCommands
import nl.hannahsten.texifyidea.reference.InputFileReference
import nl.hannahsten.texifyidea.structure.latex.LatexStructureViewCommandElement
import nl.hannahsten.texifyidea.structure.latex.LatexStructureViewElement
import nl.hannahsten.texifyidea.util.magic.CommandMagic

internal data class DocumentStructureToolParams(
    val projectPath: String,
    val texFile: String,
)

internal data class DocumentStructureToolResult(
    val projectPath: String,
    val texFile: String,
    val entries: List<DocumentStructureEntry>,
    val source: String = "texify-structure",
)

internal data class DocumentStructureEntry(
    val kind: String,
    val command: String,
    val line: Int,
    val level: Int? = null,
    val title: String? = null,
    val label: String? = null,
    val includeTarget: String? = null,
    val resolvedFiles: List<String>? = null,
)

internal object DocumentStructureTool {

    private val includeCommands: Set<String> = setOf(
        CommandNames.INPUT,
        CommandNames.INCLUDE,
        CommandNames.SUBFILE,
        CommandNames.SUBFILE_INCLUDE,
        CommandNames.IMPORT,
        CommandNames.SUB_IMPORT,
        CommandNames.INPUT_FROM,
        CommandNames.SUB_INPUT_FROM,
        CommandNames.INCLUDE_FROM,
        CommandNames.SUB_INCLUDE_FROM,
    )

    private val twoPartIncludeCommands: Set<String> = setOf(
        CommandNames.IMPORT,
        CommandNames.SUB_IMPORT,
        CommandNames.INPUT_FROM,
        CommandNames.SUB_INPUT_FROM,
        CommandNames.INCLUDE_FROM,
        CommandNames.SUB_INCLUDE_FROM,
    )

    private data class StructureCommandCandidate(
        val command: LatexCommands,
        val includeFromStructure: Boolean,
    )

    fun execute(params: DocumentStructureToolParams): DocumentStructureToolResult {
        val resolved = ToolExecutionHelper.resolveAndPrepare(
            projectPath = params.projectPath,
            texFile = params.texFile,
        )
        val projectRoot = resolved.projectPath
        val project = resolved.project
        val targetFile = resolved.targetFile

        val entries = ReadAction.compute<List<DocumentStructureEntry>, RuntimeException> {
            val psiFile = PsiManager.getInstance(project).findFile(targetFile)
                ?: throw IllegalArgumentException("Cannot resolve PSI file for: ${targetFile.path}")
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

            val commands = mutableListOf<StructureCommandCandidate>()
            val root = LatexStructureViewElement(psiFile)
            root.children.forEach { walkTree(it, commands, targetFile.path) }

            commands
                .sortedBy { it.command.textOffset }
                .mapNotNull { candidate ->
                    val command = candidate.command
                    val name = command.name ?: return@mapNotNull null
                    val line = lineOf(command.textOffset, document)

                    when {
                        name in CommandMagic.sectionNameToLevel -> {
                            DocumentStructureEntry(
                                kind = "section",
                                command = name,
                                line = line,
                                level = CommandMagic.sectionNameToLevel[name],
                                title = command.requiredParameterText(0),
                            )
                        }

                        name in CommandMagic.labels -> {
                            DocumentStructureEntry(
                                kind = "label",
                                command = name,
                                line = line,
                                label = command.requiredParameterText(0),
                            )
                        }

                        candidate.includeFromStructure || name in includeCommands -> {
                            val resolvedFiles = InputFileReference.getIncludedFiles(command)
                                .mapNotNull { it.virtualFile }
                                .mapNotNull { ProjectFileResolver.toProjectRelativePath(it, project, projectRoot) }
                                .distinct()
                                .sorted()

                            DocumentStructureEntry(
                                kind = "include",
                                command = name,
                                line = line,
                                includeTarget = includeTarget(command, name),
                                resolvedFiles = resolvedFiles.ifEmpty { null },
                            )
                        }

                        else -> null
                    }
                }
        }

        return DocumentStructureToolResult(
            projectPath = projectRoot,
            texFile = resolved.relativeTexFile,
            entries = entries,
        )
    }

    private fun walkTree(treeElement: TreeElement, out: MutableList<StructureCommandCandidate>, targetPath: String) {
        if (treeElement is LatexStructureViewCommandElement) {
            val cmd = treeElement.value
            if (cmd.containingFile.virtualFile?.path == targetPath) {
                out.add(
                    StructureCommandCandidate(
                        command = cmd,
                        includeFromStructure = treeElement.isFileInclude,
                    ),
                )
            }
        }
        treeElement.children.forEach { child -> walkTree(child, out, targetPath) }
    }

    private fun includeTarget(command: LatexCommands, name: String): String? {
        val required = command.requiredParametersText()
        if (required.isEmpty()) return null
        return if (name in twoPartIncludeCommands && required.size >= 2) required[0] + required[1] else required[0]
    }

    private fun lineOf(offset: Int, document: Document?): Int =
        if (document == null) 1 else document.getLineNumber(offset) + 1
}
