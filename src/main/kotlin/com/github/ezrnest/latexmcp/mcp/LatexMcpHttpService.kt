package com.github.ezrnest.latexmcp.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

@Service(Service.Level.APP)
class LatexMcpHttpService : Disposable {

    private val logger = thisLogger()
    @Volatile
    private var server: LatexMcpHttpServer? = null

    @Synchronized
    fun startIfNeeded() {
        if (server != null) return

        val host = System.getenv("LATEX_MCP_HOST") ?: "127.0.0.1"
        val port = (System.getenv("LATEX_MCP_PORT") ?: "18765").toIntOrNull() ?: 18765

        runCatching {
            LatexMcpHttpServer(host, port).also { it.start() }
        }.onSuccess { startedServer ->
            server = startedServer
            logger.info("LatexMCP HTTP server started at ${startedServer.baseUrl()}")
        }.onFailure { error ->
            logger.warn("Failed to start LatexMCP HTTP server", error)
        }
    }

    override fun dispose() {
        server?.stop()
        server = null
    }
}
