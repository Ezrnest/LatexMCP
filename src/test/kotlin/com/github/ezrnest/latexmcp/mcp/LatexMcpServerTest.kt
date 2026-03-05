package com.github.ezrnest.latexmcp.mcp

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
    fun `tools list includes fileset document structure label locations and rename label tools`() {
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
}
