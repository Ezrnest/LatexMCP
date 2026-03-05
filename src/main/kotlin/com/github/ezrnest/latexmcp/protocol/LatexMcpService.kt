package com.github.ezrnest.latexmcp.protocol

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class LatexMcpService {

    private val server = LatexMcpServer()

    /**
     * Handle one MCP JSON-RPC message and return the JSON-RPC response text.
     */
    fun handle(requestJson: String): String? = server.handleJsonRpc(requestJson)
}
