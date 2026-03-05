package com.github.ezrnest.latexmcp.mcp

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure

internal object ToolExecutionHelper {

    fun resolveAndPrepare(projectPath: String, texFile: String): ResolvedProjectTexFile {
        val resolved = ProjectFileResolver.resolve(projectPath, texFile)
        refreshLocalFiles(resolved.project, resolved.targetFile)
        updateFilesets(resolved.project)
        return resolved
    }

    private fun refreshLocalFiles(project: Project, targetFile: VirtualFile) {
        // Disk-first policy: refresh VFS and discard unsaved in-editor buffers inside project.
        LocalFileSystem.getInstance().refresh(false)

        val roots = ProjectRootManager.getInstance(project).contentRoots
        if (roots.isNotEmpty()) {
            roots.forEach { root -> root.refresh(false, true) }
        }
        else {
            targetFile.refresh(false, true)
        }

        val fileIndex = ProjectFileIndex.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        fileDocumentManager.unsavedDocuments.forEach { document ->
            val file = fileDocumentManager.getFile(document) ?: return@forEach
            if (fileIndex.isInContent(file)) {
                fileDocumentManager.reloadFromDisk(document)
            }
        }
    }

    private fun updateFilesets(project: Project) {
        runBlocking {
            withTimeout(10_000L) {
                LatexProjectStructure.updateFilesetsSuspend(project)
            }
        }
    }
}
