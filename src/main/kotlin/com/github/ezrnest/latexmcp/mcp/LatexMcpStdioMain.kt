package com.github.ezrnest.latexmcp.mcp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * MCP stdio entrypoint.
 *
 * MCP stdio transport uses newline-delimited JSON-RPC messages.
 */
object LatexMcpStdioMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val server = LatexMcpServer()
        val input = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        val output = BufferedWriter(OutputStreamWriter(System.out, StandardCharsets.UTF_8))

        while (true) {
            val line = input.readLine() ?: break
            if (line.isBlank()) {
                continue
            }

            try {
                val response = server.handleJsonRpc(line)
                if (response != null) {
                    output.write(response)
                    output.newLine()
                    output.flush()
                }
            }
            catch (t: Throwable) {
                System.err.println("LatexMCP stdio error: ${t.message}")
            }
        }
    }
}
