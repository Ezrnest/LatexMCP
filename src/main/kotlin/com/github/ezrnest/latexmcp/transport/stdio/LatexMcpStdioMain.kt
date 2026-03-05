package com.github.ezrnest.latexmcp.transport.stdio

import com.github.ezrnest.latexmcp.protocol.LatexMcpServer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Standalone MCP stdio entrypoint.
 *
 * Transport format is newline-delimited JSON-RPC payloads.
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
