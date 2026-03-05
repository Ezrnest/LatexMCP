package com.github.ezrnest.latexmcp.protocol

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.ezrnest.latexmcp.tools.fileset.FilesetTool
import com.github.ezrnest.latexmcp.tools.fileset.FilesetToolParams
import com.github.ezrnest.latexmcp.tools.labels.LabelLocationsTool
import com.github.ezrnest.latexmcp.tools.labels.LabelLocationsToolParams
import com.github.ezrnest.latexmcp.tools.labels.RenameLabelSafeTool
import com.github.ezrnest.latexmcp.tools.labels.RenameLabelSafeToolParams
import com.github.ezrnest.latexmcp.tools.search.StructuredSearchTool
import com.github.ezrnest.latexmcp.tools.search.StructuredSearchToolParams
import com.github.ezrnest.latexmcp.tools.structure.DocumentStructureTool
import com.github.ezrnest.latexmcp.tools.structure.DocumentStructureToolParams

/**
 * JSON-RPC 2.0 MCP server core.
 *
 * This class is transport-agnostic: stdio and HTTP both delegate request handling here.
 */
internal class LatexMcpServer(
    private val mapper: ObjectMapper = ObjectMapper(),
) {

    /**
     * Handles one JSON-RPC payload (single request or batch).
     *
     * Returns `null` for notification-only input where JSON-RPC requires no response body.
     */
    fun handleJsonRpc(requestText: String): String? {
        val payload = mapper.readTree(requestText)
        return when {
            payload.isArray -> handleBatch(payload)
            payload.isObject -> handleSingle(payload)
            else -> mapper.writeValueAsString(
                mapper.createObjectNode().apply {
                    put("jsonrpc", "2.0")
                    putNull("id")
                    set<JsonNode>(
                        "error",
                        mapper.createObjectNode()
                            .put("code", -32600)
                            .put("message", "Invalid Request"),
                    )
                },
            )
        }
    }

    private fun handleBatch(batch: JsonNode): String? {
        if (batch.size() == 0) {
            return mapper.writeValueAsString(
                mapper.createObjectNode().apply {
                    put("jsonrpc", "2.0")
                    putNull("id")
                    set<JsonNode>(
                        "error",
                        mapper.createObjectNode()
                            .put("code", -32600)
                            .put("message", "Invalid Request: empty batch"),
                    )
                },
            )
        }

        val responses = mapper.createArrayNode()
        for (request in batch) {
            val response = if (request.isObject) {
                handleSingle(request)
            }
            else {
                mapper.writeValueAsString(
                    mapper.createObjectNode().apply {
                        put("jsonrpc", "2.0")
                        putNull("id")
                        set<JsonNode>(
                            "error",
                            mapper.createObjectNode()
                                .put("code", -32600)
                                .put("message", "Invalid Request"),
                        )
                    },
                )
            }
            if (response != null) {
                responses.add(mapper.readTree(response))
            }
        }
        return if (responses.isEmpty) null else mapper.writeValueAsString(responses)
    }

    private fun handleSingle(request: JsonNode): String? {
        val id = request.get("id")
        val hasId = id != null && !id.isMissingNode && !id.isNull
        val method = request.path("method").asText(null)

        if (method == null) {
            return if (hasId) errorResponse(id, -32600, "Invalid Request: missing method") else null
        }

        return when (method) {
            "initialize" -> if (hasId) initializeResponse(id) else null
            "initialized" -> null
            "tools/list" -> if (hasId) toolsListResponse(id) else null
            "tools/call" -> if (hasId) toolsCallResponse(id, request.path("params")) else null
            else -> if (hasId) errorResponse(id, -32601, "Method not found: $method") else null
        }
    }

    private fun initializeResponse(id: JsonNode): String {
        val result = mapper.createObjectNode().apply {
            put("protocolVersion", "2025-06-18")
            set<JsonNode>(
                "serverInfo",
                mapper.createObjectNode().apply {
                    put("name", "latex-mcp")
                    put("version", "0.1.0")
                },
            )
            set<JsonNode>(
                "capabilities",
                mapper.createObjectNode().apply {
                    set<JsonNode>("tools", mapper.createObjectNode().put("listChanged", false))
                },
            )
        }

        return successResponse(id, result)
    }

    private fun toolsListResponse(id: JsonNode): String {
        val tools = mapper.createArrayNode()
        tools.add(filesetToolDescriptor())
        tools.add(documentStructureToolDescriptor())
        tools.add(labelLocationsToolDescriptor())
        tools.add(renameLabelSafeToolDescriptor())
        tools.add(structuredSearchToolDescriptor())

        val result = mapper.createObjectNode().apply {
            set<JsonNode>("tools", tools)
        }
        return successResponse(id, result)
    }

    private fun toolsCallResponse(id: JsonNode, params: JsonNode): String {
        val name = params.path("name").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: missing tool name")

        val arguments = params.path("arguments")

        return when (name) {
            "fileset" -> handleFilesetCall(id, arguments)
            "document_structure" -> handleDocumentStructureCall(id, arguments)
            "label_locations" -> handleLabelLocationsCall(id, arguments)
            "rename_label_safe" -> handleRenameLabelSafeCall(id, arguments)
            "structured_search" -> handleStructuredSearchCall(id, arguments)
            else -> errorResponse(id, -32601, "Unknown tool: $name")
        }
    }

    private fun handleFilesetCall(id: JsonNode, arguments: JsonNode): String {
        val projectPath = arguments.path("projectPath").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: fileset.projectPath is required")
        val texFile = arguments.path("texFile").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: fileset.texFile is required")

        val toolParams = FilesetToolParams(
            projectPath = projectPath,
            texFile = texFile,
            includeLibraries = arguments.path("includeLibraries").asBoolean(false),
            includeExternalDocuments = arguments.path("includeExternalDocuments").asBoolean(false),
        )

        return runCatching {
            val resultData = FilesetTool.execute(toolParams)
            val structured = mapper.valueToTree<JsonNode>(resultData)

            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put(
                                "text",
                                "Resolved ${resultData.targetFile} to ${resultData.rootCandidates.size} fileset root candidate(s), ${resultData.files.size} file(s).",
                            ),
                    ),
                )
                set<JsonNode>("structuredContent", structured)
                put("isError", false)
            }

            successResponse(id, callResult)
        }.getOrElse { error ->
            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", error.message ?: "fileset tool execution failed"),
                    ),
                )
                put("isError", true)
            }

            successResponse(id, callResult)
        }
    }

    private fun handleDocumentStructureCall(id: JsonNode, arguments: JsonNode): String {
        val projectPath = arguments.path("projectPath").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: document_structure.projectPath is required")
        val texFile = arguments.path("texFile").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: document_structure.texFile is required")

        val toolParams = DocumentStructureToolParams(
            projectPath = projectPath,
            texFile = texFile,
        )

        return runCatching {
            val resultData = DocumentStructureTool.execute(toolParams)
            val structured = mapper.valueToTree<JsonNode>(resultData)

            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put(
                                "text",
                                "Extracted ${resultData.entries.size} structure entries from ${resultData.texFile}.",
                            ),
                    ),
                )
                set<JsonNode>("structuredContent", structured)
                put("isError", false)
            }

            successResponse(id, callResult)
        }.getOrElse { error ->
            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", error.message ?: "document_structure tool execution failed"),
                    ),
                )
                put("isError", true)
            }

            successResponse(id, callResult)
        }
    }

    private fun handleLabelLocationsCall(id: JsonNode, arguments: JsonNode): String {
        val projectPath = arguments.path("projectPath").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: label_locations.projectPath is required")
        val mainTex = arguments.path("mainTex").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: label_locations.mainTex is required")
        val label = arguments.path("label").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: label_locations.label is required")

        val toolParams = LabelLocationsToolParams(
            projectPath = projectPath,
            mainTex = mainTex,
            label = label,
            includeReferences = arguments.path("includeReferences").asBoolean(true),
        )

        return runCatching {
            val resultData = LabelLocationsTool.execute(toolParams)
            val structured = mapper.valueToTree<JsonNode>(resultData)

            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put(
                                "text",
                                "Found ${resultData.definitions.size} definition(s) and ${resultData.references.size} reference(s) for label '${resultData.label}'.",
                            ),
                    ),
                )
                set<JsonNode>("structuredContent", structured)
                put("isError", false)
            }

            successResponse(id, callResult)
        }.getOrElse { error ->
            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", error.message ?: "label_locations tool execution failed"),
                    ),
                )
                put("isError", true)
            }

            successResponse(id, callResult)
        }
    }

    private fun handleRenameLabelSafeCall(id: JsonNode, arguments: JsonNode): String {
        val projectPath = arguments.path("projectPath").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: rename_label_safe.projectPath is required")
        val mainTex = arguments.path("mainTex").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: rename_label_safe.mainTex is required")
        val oldLabel = arguments.path("oldLabel").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: rename_label_safe.oldLabel is required")
        val newLabel = arguments.path("newLabel").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: rename_label_safe.newLabel is required")

        val toolParams = RenameLabelSafeToolParams(
            projectPath = projectPath,
            mainTex = mainTex,
            oldLabel = oldLabel,
            newLabel = newLabel,
            applyChanges = arguments.path("applyChanges").asBoolean(true),
        )

        return runCatching {
            val resultData = RenameLabelSafeTool.execute(toolParams)
            val structured = mapper.valueToTree<JsonNode>(resultData)

            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put(
                                "text",
                                "Planned ${resultData.plannedEdits} edit(s), applied ${resultData.appliedEdits} edit(s) for '${resultData.oldLabel}' -> '${resultData.newLabel}'.",
                            ),
                    ),
                )
                set<JsonNode>("structuredContent", structured)
                put("isError", false)
            }

            successResponse(id, callResult)
        }.getOrElse { error ->
            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", error.message ?: "rename_label_safe tool execution failed"),
                    ),
                )
                put("isError", true)
            }

            successResponse(id, callResult)
        }
    }

    private fun handleStructuredSearchCall(id: JsonNode, arguments: JsonNode): String {
        val projectPath = arguments.path("projectPath").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: structured_search.projectPath is required")
        val namePattern = arguments.path("namePattern").asText(null)
            ?: return errorResponse(id, -32602, "Invalid params: structured_search.namePattern is required")

        val scope = arguments.path("scope").asText("fileset").lowercase()
        val mainTex = arguments.path("mainTex").asText(null)
        val texFile = arguments.path("texFile").asText(null)

        if (scope == "fileset" && mainTex == null) {
            return errorResponse(id, -32602, "Invalid params: structured_search.mainTex is required when scope=fileset")
        }
        if (scope == "single_document" && texFile == null) {
            return errorResponse(id, -32602, "Invalid params: structured_search.texFile is required when scope=single_document")
        }

        val toolParams = StructuredSearchToolParams(
            projectPath = projectPath,
            scope = scope,
            mainTex = mainTex,
            texFile = texFile,
            namePattern = namePattern,
            patternMode = arguments.path("patternMode").asText("auto"),
            type = arguments.path("type").asText("both"),
            caseSensitive = arguments.path("caseSensitive").asBoolean(true),
            limit = arguments.path("limit").asInt(1000),
        )

        return runCatching {
            val resultData = StructuredSearchTool.execute(toolParams)
            val structured = mapper.valueToTree<JsonNode>(resultData)

            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put(
                                "text",
                                "Structured search found ${resultData.count} match(es) in ${resultData.scope}, truncated=${resultData.truncated}.",
                            ),
                    ),
                )
                set<JsonNode>("structuredContent", structured)
                put("isError", false)
            }

            successResponse(id, callResult)
        }.getOrElse { error ->
            val callResult = mapper.createObjectNode().apply {
                set<JsonNode>(
                    "content",
                    mapper.createArrayNode().add(
                        mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", error.message ?: "structured_search tool execution failed"),
                    ),
                )
                put("isError", true)
            }

            successResponse(id, callResult)
        }
    }

    private fun filesetToolDescriptor(): ObjectNode {
        val schema = mapper.createObjectNode().apply {
            put("type", "object")
            set<JsonNode>("required", mapper.createArrayNode().add("projectPath").add("texFile"))
            set<JsonNode>(
                "properties",
                mapper.createObjectNode().apply {
                    set<JsonNode>(
                        "projectPath",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Absolute or workspace-relative path of the IntelliJ project root directory."),
                    )
                    set<JsonNode>(
                        "texFile",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Target .tex file path, relative to projectPath (absolute path also accepted)."),
                    )
                    set<JsonNode>(
                        "includeLibraries",
                        mapper.createObjectNode()
                            .put("type", "boolean")
                            .put("default", false),
                    )
                    set<JsonNode>(
                        "includeExternalDocuments",
                        mapper.createObjectNode()
                            .put("type", "boolean")
                            .put("default", false),
                    )
                },
            )
        }

        return mapper.createObjectNode().apply {
            put("name", "fileset")
            put("title", "Resolve TeXiFy Fileset")
            put("description", "Return the TeXiFy fileset containing the given LaTeX file.")
            set<JsonNode>("inputSchema", schema)
        }
    }

    private fun documentStructureToolDescriptor(): ObjectNode {
        val schema = mapper.createObjectNode().apply {
            put("type", "object")
            set<JsonNode>("required", mapper.createArrayNode().add("projectPath").add("texFile"))
            set<JsonNode>(
                "properties",
                mapper.createObjectNode().apply {
                    set<JsonNode>(
                        "projectPath",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Absolute or workspace-relative path of the IntelliJ project root directory."),
                    )
                    set<JsonNode>(
                        "texFile",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Target .tex file path, relative to projectPath (absolute path also accepted)."),
                    )
                },
            )
        }

        return mapper.createObjectNode().apply {
            put("name", "document_structure")
            put("title", "Extract LaTeX Document Structure")
            put("description", "Return ordered section/paragraph commands and label commands with line numbers, based on TeXiFy structure view.")
            set<JsonNode>("inputSchema", schema)
        }
    }

    private fun labelLocationsToolDescriptor(): ObjectNode {
        val schema = mapper.createObjectNode().apply {
            put("type", "object")
            set<JsonNode>("required", mapper.createArrayNode().add("projectPath").add("mainTex").add("label"))
            set<JsonNode>(
                "properties",
                mapper.createObjectNode().apply {
                    set<JsonNode>(
                        "projectPath",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Absolute or workspace-relative path of the IntelliJ project root directory."),
                    )
                    set<JsonNode>(
                        "mainTex",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Main LaTeX file path used as fileset context, relative to projectPath (absolute path also accepted)."),
                    )
                    set<JsonNode>(
                        "label",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Label name to resolve, for example sec:intro."),
                    )
                    set<JsonNode>(
                        "includeReferences",
                        mapper.createObjectNode()
                            .put("type", "boolean")
                            .put("default", true)
                            .put("description", "Whether to include all references to the label."),
                    )
                },
            )
        }

        return mapper.createObjectNode().apply {
            put("name", "label_locations")
            put("title", "Resolve Label Definition And References")
            put("description", "Find label definition locations by label name and optionally all reference locations in the same TeXiFy fileset.")
            set<JsonNode>("inputSchema", schema)
        }
    }

    private fun renameLabelSafeToolDescriptor(): ObjectNode {
        val schema = mapper.createObjectNode().apply {
            put("type", "object")
            set<JsonNode>("required", mapper.createArrayNode().add("projectPath").add("mainTex").add("oldLabel").add("newLabel"))
            set<JsonNode>(
                "properties",
                mapper.createObjectNode().apply {
                    set<JsonNode>(
                        "projectPath",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Absolute or workspace-relative path of the IntelliJ project root directory."),
                    )
                    set<JsonNode>(
                        "mainTex",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Main LaTeX file path used as fileset context, relative to projectPath (absolute path also accepted)."),
                    )
                    set<JsonNode>(
                        "oldLabel",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Existing label name to rename."),
                    )
                    set<JsonNode>(
                        "newLabel",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Target label name."),
                    )
                    set<JsonNode>(
                        "applyChanges",
                        mapper.createObjectNode()
                            .put("type", "boolean")
                            .put("default", true)
                            .put("description", "If false, only preview edits without writing files."),
                    )
                },
            )
        }

        return mapper.createObjectNode().apply {
            put("name", "rename_label_safe")
            put("title", "Rename Label Safely")
            put("description", "Rename a LaTeX label definition and all references in the same fileset, with collision checks.")
            set<JsonNode>("inputSchema", schema)
        }
    }

    private fun structuredSearchToolDescriptor(): ObjectNode {
        val schema = mapper.createObjectNode().apply {
            put("type", "object")
            set<JsonNode>("required", mapper.createArrayNode().add("projectPath").add("namePattern"))
            set<JsonNode>(
                "properties",
                mapper.createObjectNode().apply {
                    set<JsonNode>(
                        "projectPath",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Absolute or workspace-relative path of the IntelliJ project root directory."),
                    )
                    set<JsonNode>(
                        "scope",
                        mapper.createObjectNode().apply {
                            put("type", "string")
                            set<JsonNode>("enum", mapper.createArrayNode().add("fileset").add("single_document"))
                            put("default", "fileset")
                            put("description", "Search scope: fileset or single_document.")
                        },
                    )
                    set<JsonNode>(
                        "mainTex",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Main LaTeX file path used as fileset context, required when scope=fileset."),
                    )
                    set<JsonNode>(
                        "texFile",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Target LaTeX file path, required when scope=single_document."),
                    )
                    set<JsonNode>(
                        "namePattern",
                        mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "Name pattern to match command/environment names."),
                    )
                    set<JsonNode>(
                        "patternMode",
                        mapper.createObjectNode().apply {
                            put("type", "string")
                            set<JsonNode>("enum", mapper.createArrayNode().add("auto").add("literal").add("wildcard").add("regex"))
                            put("default", "auto")
                            put("description", "Pattern mode: auto, literal, wildcard, regex.")
                        },
                    )
                    set<JsonNode>(
                        "type",
                        mapper.createObjectNode().apply {
                            put("type", "string")
                            set<JsonNode>("enum", mapper.createArrayNode().add("both").add("command").add("environment"))
                            put("default", "both")
                            put("description", "Target type: both, command, environment.")
                        },
                    )
                    set<JsonNode>(
                        "caseSensitive",
                        mapper.createObjectNode()
                            .put("type", "boolean")
                            .put("default", true),
                    )
                    set<JsonNode>(
                        "limit",
                        mapper.createObjectNode()
                            .put("type", "integer")
                            .put("default", 1000)
                            .put("minimum", 1),
                    )
                },
            )
        }

        return mapper.createObjectNode().apply {
            put("name", "structured_search")
            put("title", "Structured PSI Search")
            put("description", "Search commands and/or environments by name in a single document or TeXiFy fileset, with literal/wildcard/regex matching.")
            set<JsonNode>("inputSchema", schema)
        }
    }

    private fun successResponse(id: JsonNode, result: JsonNode): String {
        val response = mapper.createObjectNode().put("jsonrpc", "2.0")
        response.set<JsonNode>("id", id)
        response.set<JsonNode>("result", result)
        return mapper.writeValueAsString(response)
    }

    private fun errorResponse(id: JsonNode, code: Int, message: String): String {
        val error = mapper.createObjectNode().put("code", code).put("message", message)
        val response = mapper.createObjectNode().put("jsonrpc", "2.0")
        response.set<JsonNode>("id", id)
        response.set<ObjectNode>("error", error)
        return mapper.writeValueAsString(response)
    }
}
