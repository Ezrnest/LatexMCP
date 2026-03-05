package com.github.ezrnest.latexmcp.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class LatexMcpHttpServer(
    private val host: String,
    private val port: Int,
    private val mcpServer: LatexMcpServer = LatexMcpServer(),
) {

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = "2025-03-26"
    }

    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private val requestMapper = ObjectMapper()
    @Volatile
    private var httpServer: HttpServer? = null

    @Synchronized
    fun start() {
        if (httpServer != null) return

        val server = HttpServer.create(InetSocketAddress(host, port), 0)
        server.createContext("/mcp") { exchange -> handleMcp(exchange) }
        server.createContext("/health") { exchange ->
            if (exchange.requestMethod != "GET") {
                send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed")
                return@createContext
            }
            send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}")
        }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        httpServer = server
    }

    @Synchronized
    fun stop(delaySeconds: Int = 0) {
        httpServer?.stop(delaySeconds)
        httpServer = null
        sessions.clear()
    }

    fun baseUrl(): String = "http://$host:$port"

    private fun handleMcp(exchange: HttpExchange) {
        if (!validateProtocolVersion(exchange)) return

        when (exchange.requestMethod) {
            "POST" -> handlePost(exchange)
            "GET" -> handleGet(exchange)
            "DELETE" -> handleDelete(exchange)
            else -> send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed")
        }
    }

    private fun handlePost(exchange: HttpExchange) {
        val sessionId = exchange.requestHeaders.getFirst("Mcp-Session-Id")
            ?: exchange.requestHeaders.getFirst("MCP-Session-Id")

        val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (body.isBlank()) {
            send(exchange, 400, "text/plain; charset=utf-8", "Empty request body")
            return
        }

        val isInitialize = runCatching {
            val payload = requestMapper.readTree(body)
            when {
                payload.isObject -> payload.path("method").asText("") == "initialize"
                payload.isArray -> payload.any { it.path("method").asText("") == "initialize" }
                else -> false
            }
        }.getOrDefault(false)

        if (!isInitialize && sessionId != null && !sessions.contains(sessionId)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Unknown MCP session")
            return
        }

        val response = runCatching { mcpServer.handleJsonRpc(body) }.getOrElse { error ->
            val escapedMessage = (error.message ?: "Invalid JSON-RPC payload").replace("\"", "\\\"")
            val errorResponse = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"$escapedMessage\"}}"
            send(exchange, 400, "application/json; charset=utf-8", errorResponse)
            return
        }

        if (response == null) {
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
            return
        }

        val assignedSessionId = if (isInitialize) {
            (sessionId ?: UUID.randomUUID().toString()).also { sessions.add(it) }
        }
        else {
            sessionId
        }

        if (assignedSessionId != null) {
            exchange.responseHeaders.set("Mcp-Session-Id", assignedSessionId)
        }
        exchange.responseHeaders.set("MCP-Protocol-Version", SUPPORTED_PROTOCOL_VERSION)

        val accept = exchange.requestHeaders.getFirst("Accept").orEmpty()
        if (accept.contains("text/event-stream")) {
            sendSse(exchange, response)
        }
        else {
            send(exchange, 200, "application/json; charset=utf-8", response)
        }
    }

    private fun handleGet(exchange: HttpExchange) {
        val sessionId = exchange.requestHeaders.getFirst("Mcp-Session-Id")
            ?: exchange.requestHeaders.getFirst("MCP-Session-Id")
        if (sessionId != null && !sessions.contains(sessionId)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Unknown MCP session")
            return
        }

        exchange.responseHeaders.set("Content-Type", "text/event-stream; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        exchange.responseHeaders.set("Connection", "keep-alive")
        exchange.responseHeaders.set("MCP-Protocol-Version", SUPPORTED_PROTOCOL_VERSION)
        if (sessionId != null) {
            exchange.responseHeaders.set("Mcp-Session-Id", sessionId)
        }
        val payload = ": connected\n\n"
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    private fun handleDelete(exchange: HttpExchange) {
        val sessionId = exchange.requestHeaders.getFirst("Mcp-Session-Id")
            ?: exchange.requestHeaders.getFirst("MCP-Session-Id")
        if (sessionId == null) {
            send(exchange, 400, "text/plain; charset=utf-8", "Missing Mcp-Session-Id header")
            return
        }
        if (!sessions.remove(sessionId)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Unknown MCP session")
            return
        }
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
    }

    private fun validateProtocolVersion(exchange: HttpExchange): Boolean {
        val version = exchange.requestHeaders.getFirst("MCP-Protocol-Version")
        if (version != null && version != SUPPORTED_PROTOCOL_VERSION) {
            send(exchange, 400, "text/plain; charset=utf-8", "Unsupported MCP-Protocol-Version: $version")
            return false
        }
        return true
    }

    private fun sendSse(exchange: HttpExchange, jsonPayload: String) {
        exchange.responseHeaders.set("Content-Type", "text/event-stream; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        exchange.responseHeaders.set("Connection", "keep-alive")
        val sseData = buildString {
            append("event: message\\n")
            jsonPayload.lines().forEach { line ->
                append("data: ").append(line).append('\n')
            }
            append('\n')
        }
        val bytes = sseData.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
            output.flush()
        }
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
