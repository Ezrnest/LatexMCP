package com.github.ezrnest.latexmcp.mcp

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import nl.hannahsten.texifyidea.psi.LatexCommands
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
)

internal object DocumentStructureTool {

    fun execute(params: DocumentStructureToolParams): DocumentStructureToolResult {
        val resolved = ProjectFileResolver.resolve(
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

            val commands = mutableListOf<LatexCommands>()
            val root = LatexStructureViewElement(psiFile)
            root.children.forEach { walkTree(it, commands, targetFile.path) }

            commands
                .sortedBy { it.textOffset }
                .mapNotNull { command ->
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

    private fun walkTree(treeElement: TreeElement, out: MutableList<LatexCommands>, targetPath: String) {
        if (treeElement is LatexStructureViewCommandElement) {
            val cmd = treeElement.value
            if (cmd.containingFile.virtualFile?.path == targetPath) {
                out.add(cmd)
            }
        }
        treeElement.children.forEach { child -> walkTree(child, out, targetPath) }
    }

    private fun lineOf(offset: Int, document: Document?): Int =
        if (document == null) 1 else document.getLineNumber(offset) + 1
}
