package com.github.ezrnest.latexmcp.transport.http

import com.github.ezrnest.latexmcp.settings.LatexMcpSettingsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Application service owning the embedded HTTP MCP server lifecycle.
 */
@Service(Service.Level.APP)
class LatexMcpHttpService : Disposable {

    private val logger = thisLogger()
    @Volatile
    private var server: LatexMcpHttpServer? = null

    /**
     * Starts the embedded server once; no-op when already running.
     */
    @Synchronized
    fun startIfNeeded() {
        if (server != null) return

        startServer()
    }

    /**
     * Restarts server only when currently running, used after settings changes.
     */
    @Synchronized
    fun restartIfRunning() {
        if (server == null) return
        stopServer()
        startServer()
    }

    override fun dispose() {
        stopServer()
    }

    private fun resolveHost(): String = System.getenv("LATEX_MCP_HOST") ?: "127.0.0.1"

    private fun resolvePort(): Int {
        val envPort = System.getenv("LATEX_MCP_PORT")?.toIntOrNull()
        if (envPort != null && envPort in 1..65535) {
            return envPort
        }
        val settings = ApplicationManager.getApplication().getService(LatexMcpSettingsService::class.java)
        return settings.getPort()
    }

    private fun startServer() {
        val host = resolveHost()
        val port = resolvePort()
        runCatching {
            LatexMcpHttpServer(host, port).also { it.start() }
        }.onSuccess { startedServer ->
            server = startedServer
            logger.info("LatexMCP HTTP server started at ${startedServer.baseUrl()}")
        }.onFailure { error ->
            logger.warn("Failed to start LatexMCP HTTP server", error)
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }
}
