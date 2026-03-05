package com.github.ezrnest.latexmcp.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Simple MCP HTTP entrypoint.
 *
 * Endpoints:
 * - POST /mcp    JSON-RPC payload
 * - GET  /health Health check
 */
object LatexMcpHttpMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val port = (System.getenv("LATEX_MCP_PORT") ?: "18765").toIntOrNull() ?: 18765
        val host = System.getenv("LATEX_MCP_HOST") ?: "127.0.0.1"

        val mcpServer = LatexMcpServer()
        val httpServer = HttpServer.create(InetSocketAddress(host, port), 0)

        httpServer.createContext("/mcp") { exchange ->
            handleMcp(exchange, mcpServer)
        }

        httpServer.createContext("/health") { exchange ->
            if (exchange.requestMethod != "GET") {
                send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed")
                return@createContext
            }
            send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}")
        }

        httpServer.executor = Executors.newCachedThreadPool()
        httpServer.start()

        System.err.println("LatexMCP HTTP server listening on http://$host:$port")
    }

    private fun handleMcp(exchange: HttpExchange, mcpServer: LatexMcpServer) {
        if (exchange.requestMethod != "POST") {
            send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed")
            return
        }

        val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (body.isBlank()) {
            send(exchange, 400, "text/plain; charset=utf-8", "Empty request body")
            return
        }

        val response = runCatching { mcpServer.handleJsonRpc(body) }.getOrElse { error ->
            val escapedMessage = (error.message ?: "Invalid JSON-RPC payload").replace("\"", "\\\"")
            val errorResponse = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"$escapedMessage\"}}"
            send(exchange, 400, "application/json; charset=utf-8", errorResponse)
            return
        }

        if (response == null) {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        send(exchange, 200, "application/json; charset=utf-8", response)
    }

    private fun send(exchange: HttpExchange, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }
}
