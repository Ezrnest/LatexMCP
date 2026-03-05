package com.github.ezrnest.latexmcp.tools.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.ProjectRootManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal data class ResolvedProjectTexFile(
    val project: Project,
    val targetFile: VirtualFile,
    val projectPath: String,
    val relativeTexFile: String,
)

internal object ProjectFileResolver {

    fun resolve(projectPath: String, texFile: String): ResolvedProjectTexFile {
        val normalizedProjectPath = normalizePath(projectPath)
        val project = resolveProject(normalizedProjectPath, texFile)
            ?: throw IllegalArgumentException("Cannot resolve open IntelliJ project for projectPath: $normalizedProjectPath")

        val targetFile = resolveTargetFile(project, normalizedProjectPath, texFile)
            ?: throw IllegalArgumentException("Cannot resolve texFile in project: $texFile")

        val relative = toProjectRelativePath(targetFile, project, normalizedProjectPath)
            ?: throw IllegalArgumentException("Cannot map texFile to project-relative path: ${targetFile.path}")

        return ResolvedProjectTexFile(
            project = project,
            targetFile = targetFile,
            projectPath = normalizedProjectPath,
            relativeTexFile = relative,
        )
    }

    fun toProjectRelativePath(file: VirtualFile, project: Project, projectPath: String): String? {
        projectRoots(project).forEach { root ->
            VfsUtilCore.getRelativePath(file, root, '/')
                ?.let { return it }
        }

        val normalizedPath = runCatching { normalizePath(file.path) }.getOrNull() ?: return null
        val root = Path.of(projectPath)
        val candidate = Path.of(normalizedPath)
        if (candidate == root || candidate.startsWith(root)) {
            return root.relativize(candidate).toString()
        }
        return null
    }

    private fun resolveProject(projectPath: String, texFile: String): Project? {
        val projects = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        val exact = projects.firstOrNull { normalizePath(it.basePath ?: "") == projectPath }
        if (exact != null) return exact

        val single = projects.singleOrNull()
        if (single != null) return single

        val absoluteTex = absolutePathOrNull(projectPath, texFile) ?: return null
        val targetFile = LocalFileSystem.getInstance().findFileByNioFile(absoluteTex)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(absoluteTex)
            ?: return null

        return ProjectLocator.getInstance().guessProjectForFile(targetFile)
    }

    private fun resolveTargetFile(project: Project, projectPath: String, texFile: String): VirtualFile? {
        // Prefer project VFS lookup to support temp:// test projects.
        val normalizedRelative = normalizeRelativePath(texFile)
        projectRoots(project).forEach { root ->
            root.findFileByRelativePath(normalizedRelative)?.let { return it }
        }

        val absoluteTex = absolutePathOrNull(projectPath, texFile) ?: return null
        if (!isUnderProjectRoot(absoluteTex, Path.of(projectPath))) {
            throw IllegalArgumentException("texFile must be under projectPath: $absoluteTex")
        }

        return LocalFileSystem.getInstance().findFileByNioFile(absoluteTex)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(absoluteTex)
    }

    private fun absolutePathOrNull(projectPath: String, texFile: String): Path? {
        val texPath = Path.of(texFile)
        return if (texPath.isAbsolute) texPath.normalize() else Path.of(projectPath, texFile).normalize()
    }

    private fun isUnderProjectRoot(file: Path, root: Path): Boolean = file == root || file.startsWith(root)

    private fun normalizeRelativePath(path: String): String = path.replace('\\', '/').trimStart('/')

    private fun projectRoots(project: Project): List<VirtualFile> {
        val roots = ProjectRootManager.getInstance(project).contentRoots.toMutableList()
        if (roots.isEmpty()) {
            project.basePath?.let { basePath ->
                LocalFileSystem.getInstance().findFileByPath(basePath)?.let { roots.add(it) }
            }
        }
        return roots.distinctBy { it.url }
    }

    private fun normalizePath(rawPath: String): String {
        val normalized = Paths.get(rawPath).toAbsolutePath().normalize()
        return runCatching {
            if (Files.exists(normalized)) normalized.toRealPath().normalize().toString() else normalized.toString()
        }.getOrElse { normalized.toString() }
    }
}
