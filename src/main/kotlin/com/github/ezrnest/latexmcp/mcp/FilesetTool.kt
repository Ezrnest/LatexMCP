package com.github.ezrnest.latexmcp.mcp

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import nl.hannahsten.texifyidea.index.projectstructure.FilesetData
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

internal data class FilesetToolParams(
    val path: String,
    val projectPath: String? = null,
    val includeLibraries: Boolean = true,
    val includeExternalDocuments: Boolean = false,
)

internal data class FilesetToolResult(
    val targetFile: String,
    val projectPath: String,
    val rootCandidates: List<String>,
    val files: List<String>,
    val libraries: List<String>,
    val externalDocuments: List<ExternalDocumentResult>,
    val source: String = "texify-fileset",
)

internal data class ExternalDocumentResult(
    val labelPrefix: String,
    val files: List<String>,
)

internal object FilesetTool {

    fun execute(params: FilesetToolParams): FilesetToolResult {
        val filePath = normalizePath(params.path)
        val project = resolveProject(filePath, params.projectPath)
            ?: throw IllegalArgumentException("Cannot resolve open IntelliJ project for path: $filePath")

        val targetFile = resolveVirtualFile(filePath)
            ?: throw IllegalArgumentException("Cannot resolve file in LocalFileSystem: $filePath")

        val data = ReadAction.compute<FilesetData?, RuntimeException> {
            LatexProjectStructure.getFilesetDataFor(targetFile, project)
        }

        if (data == null) {
            return FilesetToolResult(
                targetFile = targetFile.path,
                projectPath = project.basePath ?: project.name,
                rootCandidates = listOf(targetFile.path),
                files = listOf(targetFile.path),
                libraries = emptyList(),
                externalDocuments = emptyList(),
            )
        }

        val libraries = if (params.includeLibraries) data.libraries.sorted() else emptyList()
        val externalDocuments = if (params.includeExternalDocuments) {
            data.externalDocumentInfo.map { info ->
                ExternalDocumentResult(
                    labelPrefix = info.labelPrefix,
                    files = info.files.map { it.path }.sorted(),
                )
            }
        }
        else {
            emptyList()
        }

        return FilesetToolResult(
            targetFile = targetFile.path,
            projectPath = project.basePath ?: project.name,
            rootCandidates = data.filesets.map { it.root.path }.distinct().sorted(),
            files = data.relatedFiles.map { it.path }.distinct().sorted(),
            libraries = libraries,
            externalDocuments = externalDocuments,
        )
    }

    private fun resolveProject(path: String, projectPath: String?): Project? {
        val projects = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        if (projectPath != null) {
            val normalizedProjectPath = normalizePath(projectPath)
            return projects.firstOrNull { normalizePath(it.basePath ?: "") == normalizedProjectPath }
        }

        return projects
            .mapNotNull { project ->
                val basePath = project.basePath ?: return@mapNotNull null
                val normalizedBase = normalizePath(basePath)
                if (path == normalizedBase || path.startsWith(normalizedBase + File.separator)) {
                    normalizedBase.length to project
                }
                else {
                    null
                }
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private fun resolveVirtualFile(path: String) =
        LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path))
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(path))

    private fun normalizePath(rawPath: String): String = Path.of(rawPath).toAbsolutePath().normalize().toString()
}
