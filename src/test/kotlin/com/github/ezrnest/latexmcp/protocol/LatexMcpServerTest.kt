package com.github.ezrnest.latexmcp.protocol

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexMcpServerTest {

    private val mapper = ObjectMapper()
    private val server = LatexMcpServer(mapper)

    @Test
    fun `initialize returns server info and protocol version`() {
        val responseText = server.handleJsonRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""
        )
        assertNotNull(responseText)

        val response = mapper.readTree(requireNotNull(responseText))
        assertEquals("2.0", response.path("jsonrpc").asText())
        assertEquals(1, response.path("id").asInt())
        assertEquals("2025-06-18", response.path("result").path("protocolVersion").asText())
        assertEquals("latex-mcp", response.path("result").path("serverInfo").path("name").asText())
    }

    @Test
    fun `initialized notification returns no response`() {
        val responseText = server.handleJsonRpc(
            """{"jsonrpc":"2.0","method":"initialized","params":{}}"""
        )

        assertNull(responseText)
    }

    @Test
    fun `tools list includes all implemented tools`() {
        val responseText = server.handleJsonRpc(
            """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        )
        assertNotNull(responseText)

        val response = mapper.readTree(requireNotNull(responseText))
        val tools = response.path("result").path("tools")
        assertTrue(tools.isArray)
        assertTrue(tools.any { it.path("name").asText() == "fileset" })
        assertTrue(tools.any { it.path("name").asText() == "document_structure" })
        assertTrue(tools.any { it.path("name").asText() == "label_locations" })
        assertTrue(tools.any { it.path("name").asText() == "rename_label_safe" })
        assertTrue(tools.any { it.path("name").asText() == "structured_search" })
        assertTrue(tools.any { it.path("name").asText() == "inspection_missing_label" })
    }

    @Test
    fun `unknown tool returns json rpc error`() {
        val responseText = server.handleJsonRpc(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"unknown","arguments":{}}}"""
        )
        assertNotNull(responseText)

        val response = mapper.readTree(requireNotNull(responseText))
        assertEquals(-32601, response.path("error").path("code").asInt())
        assertTrue(response.path("error").path("message").asText().contains("Unknown tool"))
    }

    @Test
    fun `batch request returns only request responses`() {
        val responseText = server.handleJsonRpc(
            """
            [
              {"jsonrpc":"2.0","method":"initialized","params":{}},
              {"jsonrpc":"2.0","id":4,"method":"tools/list","params":{}}
            ]
            """.trimIndent()
        )
        assertNotNull(responseText)

        val response = mapper.readTree(requireNotNull(responseText))
        assertTrue(response.isArray)
        assertEquals(1, response.size())
        assertEquals(4, response[0].path("id").asInt())
    }

    @Test
    fun `inspection missing label validates scope specific required params`() {
        val responseText = server.handleJsonRpc(
            """
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"inspection_missing_label","arguments":{"projectPath":"/tmp","scope":"fileset"}}}
            """.trimIndent()
        )
        assertNotNull(responseText)

        val response = mapper.readTree(requireNotNull(responseText))
        assertEquals(-32602, response.path("error").path("code").asInt())
        assertTrue(response.path("error").path("message").asText().contains("mainTex"))
    }

    @Test
    fun `label locations requires query and scope specific file params`() {
        val missingQueryResponseText = server.handleJsonRpc(
            """
            {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"label_locations","arguments":{"projectPath":"/tmp","mainTex":"main.tex"}}}
            """.trimIndent()
        )
        assertNotNull(missingQueryResponseText)
        val missingQueryResponse = mapper.readTree(requireNotNull(missingQueryResponseText))
        assertEquals(-32602, missingQueryResponse.path("error").path("code").asInt())
        assertTrue(missingQueryResponse.path("error").path("message").asText().contains("labelPattern or label"))

        val singleScopeResponseText = server.handleJsonRpc(
            """
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"label_locations","arguments":{"projectPath":"/tmp","scope":"single_document","labelPattern":"sec:*"}}}
            """.trimIndent()
        )
        assertNotNull(singleScopeResponseText)
        val singleScopeResponse = mapper.readTree(requireNotNull(singleScopeResponseText))
        assertEquals(-32602, singleScopeResponse.path("error").path("code").asInt())
        assertTrue(singleScopeResponse.path("error").path("message").asText().contains("texFile"))
    }
}
