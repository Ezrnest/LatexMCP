package com.github.ezrnest.latexmcp.mcp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LatexMcpStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<LatexMcpHttpService>().startIfNeeded()
    }
}
