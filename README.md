# LatexMCP

LatexMCP is an MCP server for LaTeX project analysis on top of IntelliJ + TeXiFy.
It focuses on reusing TeXiFy capabilities (especially fileset/project-structure logic) instead of reimplementing parsers.

## Current Status

Implemented:
- MCP JSON-RPC server core (`initialize`, `tools/list`, `tools/call`)
- `fileset` tool backed by TeXiFy `LatexProjectStructure`
- `document_structure` tool backed by TeXiFy `LatexStructureViewElement`
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
    "latex-mcp": {
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
- Input `projectPath + texFile` and return the related fileset in that project.

Input arguments:
- `projectPath` (required): project root directory path
- `texFile` (required): target `.tex` file path (relative to `projectPath` is preferred; absolute is also accepted)
- `includeLibraries` (optional, default `false`)
- `includeExternalDocuments` (optional, default `false`)

Output fields:
- `rootCandidates`
- `files`
- `libraries`
- `externalDocuments`
- `source` (`texify-fileset`)

Output path convention:
- `targetFile`, `rootCandidates`, `files`, and external-document file paths are returned as paths relative to `projectPath`.
- By default, only files under `projectPath` are returned (package/library files are not listed in `files`).

Behavior notes:
- Before resolving, the tool triggers a TeXiFy fileset refresh to reduce stale-cache results.
- If no fileset is available, it falls back to a singleton result containing only the target file.

## Tool: `document_structure`

Purpose:
- Input `projectPath + texFile` and return ordered document structure entries from TeXiFy structure view.

Input arguments:
- `projectPath` (required): project root directory path
- `texFile` (required): target `.tex` file path (relative to `projectPath` is preferred; absolute is also accepted)

Output fields:
- `entries`: ordered entries from source order
- `entries[].kind`: `section` or `label`
- `entries[].command`: command name such as `\\section`, `\\subsection`, `\\paragraph`, `\\label`
- `entries[].line`: 1-based line number in the target file
- `entries[].level`: section level (only for `section`)
- `entries[].title`: section title text (only for `section`)
- `entries[].label`: label name (only for `label`)
- `source` (`texify-structure`)

## Notes

- TeXiFy plugin dependency is required at runtime (`nl.rubensten.texifyidea`).
- Tool resolution relies on IntelliJ open project context.
- Design notes are in `notes/mcp-design.md`.
