package com.github.ezrnest.latexmcp.ide

import com.github.ezrnest.latexmcp.transport.http.LatexMcpHttpService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Starts the embedded MCP HTTP server when a project opens.
 *
 * Startup is idempotent; repeated project opens do not create duplicate servers.
 */
class LatexMcpStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<LatexMcpHttpService>().startIfNeeded()
    }
}
