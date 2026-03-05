package com.github.ezrnest.latexmcp.tools.fileset

import com.github.ezrnest.latexmcp.tools.common.ProjectFileResolver
import com.github.ezrnest.latexmcp.tools.common.ToolExecutionHelper
import com.intellij.openapi.application.ReadAction
import nl.hannahsten.texifyidea.index.projectstructure.FilesetData
import nl.hannahsten.texifyidea.index.projectstructure.LatexProjectStructure

internal data class FilesetToolParams(
    val projectPath: String,
    val texFile: String,
    val includeLibraries: Boolean = false,
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
        val resolved = ToolExecutionHelper.resolveAndPrepare(
            projectPath = params.projectPath,
            texFile = params.texFile,
        )
        val projectRoot = resolved.projectPath
        val project = resolved.project
        val targetFile = resolved.targetFile

        val data = ReadAction.compute<FilesetData?, RuntimeException> {
            LatexProjectStructure.getFilesetDataFor(targetFile, project)
        }

        if (data == null) {
            return FilesetToolResult(
                targetFile = resolved.relativeTexFile,
                projectPath = projectRoot,
                rootCandidates = listOf(resolved.relativeTexFile),
                files = listOf(resolved.relativeTexFile),
                libraries = emptyList(),
                externalDocuments = emptyList(),
            )
        }

        val relatedProjectFiles = data.relatedFiles
            .mapNotNull { ProjectFileResolver.toProjectRelativePath(it, project, projectRoot) }
            .distinct()

        val rootProjectFiles = data.filesets
            .mapNotNull { ProjectFileResolver.toProjectRelativePath(it.root, project, projectRoot) }
            .distinct()

        val libraries = if (params.includeLibraries) data.libraries.toList() else emptyList()
        val externalDocuments = if (params.includeExternalDocuments) {
            data.externalDocumentInfo.map { info ->
                ExternalDocumentResult(
                    labelPrefix = info.labelPrefix,
                    files = info.files
                        .mapNotNull { ProjectFileResolver.toProjectRelativePath(it, project, projectRoot) }
                        .distinct()
                )
            }
        }
        else {
            emptyList()
        }

        return FilesetToolResult(
            targetFile = resolved.relativeTexFile,
            projectPath = projectRoot,
            rootCandidates = rootProjectFiles,
            files = relatedProjectFiles,
            libraries = libraries,
            externalDocuments = externalDocuments,
        )
    }
}
