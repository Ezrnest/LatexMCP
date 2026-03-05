package com.github.ezrnest.latexmcp.protocol

import com.intellij.openapi.components.Service

/**
 * Application-level facade for dispatching MCP JSON-RPC messages inside the IDE process.
 */
@Service(Service.Level.APP)
class LatexMcpService {

    private val server = LatexMcpServer()

    /**
     * Handles one MCP JSON-RPC payload.
     *
     * Returns `null` for notifications or other no-response flows.
     */
    fun handle(requestJson: String): String? = server.handleJsonRpc(requestJson)
}
