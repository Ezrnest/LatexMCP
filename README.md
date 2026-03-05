# LatexMCP

LatexMCP is an MCP server for LaTeX project analysis on top of IntelliJ + TeXiFy.
It focuses on reusing TeXiFy capabilities (especially fileset/project-structure logic) instead of reimplementing parsers.

## Current Status

Implemented:
- MCP JSON-RPC server core (`initialize`, `tools/list`, `tools/call`)
- `fileset` tool backed by TeXiFy `LatexProjectStructure`
- Stdio transport entrypoint
- HTTP transport entrypoint

## Run

Build:

```bash
./gradlew build
```

Run as stdio MCP server:

```bash
./gradlew runMcpStdio
```

Run as HTTP MCP server (default `127.0.0.1:18765`):

```bash
./gradlew runMcpHttp
```

Optional environment variables:
- `LATEX_MCP_HOST` (default `127.0.0.1`)
- `LATEX_MCP_PORT` (default `18765`)

Health check:

```bash
curl http://127.0.0.1:18765/health
```

## MCP Client Config Example

For clients using `mcp-remote`:

```json
{
  "mcpServers": {
    "latexmcp": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://127.0.0.1:18765/mcp"
      ]
    }
  }
}
```

## Tool: `fileset`

Purpose:
- Input one LaTeX file path and return the related fileset in the project.

Input arguments:
- `path` (required): target `.tex` file path
- `projectPath` (optional): disambiguate when multiple projects are open
- `includeLibraries` (optional, default `true`)
- `includeExternalDocuments` (optional, default `false`)

Output fields:
- `rootCandidates`
- `files`
- `libraries`
- `externalDocuments`
- `source` (`texify-fileset`)

## Notes

- TeXiFy plugin dependency is required at runtime (`nl.rubensten.texifyidea`).
- `fileset` resolution relies on IntelliJ open project context.
- Design notes are in `notes/mcp-design.md`.
