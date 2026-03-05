# LatexMCP

LatexMCP is an MCP server for LaTeX project analysis on top of IntelliJ + TeXiFy.
It focuses on reusing TeXiFy capabilities (especially fileset/project-structure logic) instead of reimplementing parsers.

## Current Status

Implemented:
- MCP JSON-RPC server core (`initialize`, `tools/list`, `tools/call`)
- `fileset` tool backed by TeXiFy `LatexProjectStructure`
- `document_structure` tool backed by TeXiFy `LatexStructureViewElement`
- `label_locations` tool backed by TeXiFy `LatexLabelUtil`
- `rename_label_safe` tool for safe label rename across definitions and references
- `structured_search` tool for PSI-based command/environment name search
- `inspection_missing_label` tool backed by TeXiFy `LatexMissingLabelInspection`
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
- `LATEX_MCP_PORT` (overrides plugin setting; default `18765`)

IDE setting:
- In `Settings/Preferences -> Tools -> LatexMCP`, you can configure the HTTP port used by the embedded server.

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
- `entries[].kind`: `section`, `label`, or `include`
- `entries[].command`: command name such as `\\section`, `\\subsection`, `\\paragraph`, `\\label`, `\\input`, `\\include`
- `entries[].line`: 1-based line number in the target file
- `entries[].level`: section level (only for `section`)
- `entries[].title`: section title text (only for `section`)
- `entries[].label`: label name (only for `label`)
- `entries[].includeTarget`: include target text (only for `include`)
- `entries[].resolvedFiles`: resolved include files relative to `projectPath` (only for `include`, when resolvable)
- `source` (`texify-structure`)

## Tool: `label_locations`

Purpose:
- Input `projectPath + mainTex + label` and return the label definition locations, plus references by default.

Input arguments:
- `projectPath` (required): project root directory path
- `mainTex` (required): main `.tex` file path used as fileset context (relative to `projectPath` is preferred)
- `label` (required): label text, for example `sec:intro`
- `includeReferences` (optional, default `true`): include all reference locations

Output fields:
- `definitions`: definition locations from `LatexLabelUtil.getLabelParamsByName(...)`
- `references`: reference locations (empty when `includeReferences=false`)
- `definitions[]/references[]`: `{ file, line, column, offset }` where `file` is relative to `projectPath`
- `source` (`texify-labels`)

## Tool: `rename_label_safe`

Purpose:
- Rename an existing label definition and its references in the same fileset, with collision checks.

Input arguments:
- `projectPath` (required): project root directory path
- `mainTex` (required): main `.tex` file used as fileset context
- `oldLabel` (required): existing label text
- `newLabel` (required): target label text
- `applyChanges` (optional, default `true`): if `false`, only preview edits

Output fields:
- `definitionsFound`, `referencesFound`: matched definition/reference count before filtering
- `plannedEdits`, `appliedEdits`: edit counts
- `changedFiles`: modified files (relative paths)
- `edits[]`: per-location result with `{ kind, file, line, column, offset, applied }`
- `source` (`texify-label-rename`)

## Tool: `structured_search`

Purpose:
- Search command/environment names using PSI traversal in either one document or an entire fileset.

Input arguments:
- `projectPath` (required): project root directory path
- `namePattern` (required): name matcher pattern
- `scope` (optional, default `fileset`): `fileset` or `single_document`
- `mainTex` (required when `scope=fileset`): fileset context main file
- `texFile` (required when `scope=single_document`): target file
- `type` (optional, default `both`): `both`, `command`, or `environment`
- `patternMode` (optional, default `auto`): `auto`, `literal`, `wildcard`, `regex`
- `caseSensitive` (optional, default `true`)
- `limit` (optional, default `1000`)

Output fields:
- `count`, `truncated`, `limit`
- `results[]`: `{ type, name, file, line, column, offset, text }`
- `source` (`texify-structured-search`)

## Tool: `inspection_missing_label`

Purpose:
- Run TeXiFy missing-label inspection and return issue locations.

Input arguments:
- `projectPath` (required): project root directory path
- `scope` (optional, default `fileset`): `fileset` or `single_document`
- `mainTex` (required when `scope=fileset`): fileset context main file
- `texFile` (required when `scope=single_document`): target file
- `limit` (optional, default `1000`)

Output fields:
- `count`, `truncated`, `limit`
- `issues[]`: `{ file, line, column, offset }`
- `source` (`texify-inspection-missing-label`)

## Notes

- TeXiFy plugin dependency is required at runtime (`nl.rubensten.texifyidea`).
- Tool resolution relies on IntelliJ open project context.
- Before each tool execution, the server uses a disk-first refresh policy (refresh VFS, reload unsaved project documents from disk) and then updates TeXiFy filesets.
- Design notes are in `notes/mcp-design.md`.
