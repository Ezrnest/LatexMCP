package com.github.ezrnest.latexmcp.transport.http

/**
 * Standalone HTTP entrypoint for local development and external process hosting.
 *
 * In plugin mode, startup is managed by [LatexMcpHttpService] and
 * [com.github.ezrnest.latexmcp.ide.LatexMcpStartupActivity].
 */
object LatexMcpHttpMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val port = (System.getenv("LATEX_MCP_PORT") ?: "18765").toIntOrNull() ?: 18765
        val host = System.getenv("LATEX_MCP_HOST") ?: "127.0.0.1"

        val server = LatexMcpHttpServer(host, port)
        server.start()

        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop()
        })

        System.err.println("LatexMCP HTTP server listening on ${server.baseUrl()}")
        Thread.currentThread().join()
    }
}
