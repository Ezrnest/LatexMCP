# MCP 设计确认（面向 LaTeX PSI 服务）

## 目标
本项目作为 **MCP Server**，向 Agent 暴露 TeXiFy/IntelliJ PSI 能力，优先支持“可自动调用”的分析工具，并保持协议兼容与可观测性。

## 协议与生命周期（建议）
- 采用 MCP 的 JSON-RPC 会话模型，严格走 `initialize -> initialized -> operation`。
- 传输层优先 `stdio`（本地插件/Agent 集成最稳妥），后续可扩展 Streamable HTTP。
- 在 `initialize` 中仅声明第一阶段需要的能力，避免过度暴露。

## 第一阶段能力边界
- `tools`：**必须**。第一优先级工具为 `fileset`（给定 `.tex` 文件，返回其所在 fileset）。
- `resources`：建议第二阶段引入。适合暴露 `latex://`、`file://` 资源和缓存结果。
- `prompts`：可选。仅当你要提供“固定工作流模板”时再加。

## 第一工具：`fileset`（与 TeXiFy 对齐）
### 输入（inputSchema）
- `projectPath` (string, required): IntelliJ 项目根路径。
- `texFile` (string, required): 目标 LaTeX 文件路径（推荐相对 `projectPath`）。
- `includeLibraries` (boolean, default `false`): 是否返回推断到的 package/library 名称。
- `includeExternalDocuments` (boolean, default `false`): 是否返回 `\\externaldocument` 信息。

### 输出（result）
- `rootCandidates`: 该文件所属 fileset 的根文件列表（一个文件可能属于多个 fileset）。
- `files`: 同 fileset 的全部文件（去重，建议保持遇到顺序）。
- `libraries`: fileset 中 `\\usepackage` 等引入的库名集合。
- `externalDocuments`: 外部文档标签来源（可选）。
- `source`: 固定返回 `texify-fileset`，用于标识算法来源。
- 所有文件路径返回为相对 `projectPath` 的相对路径。

### 语义基准（参考 TeXiFy）
- 一份 fileset = root + included files（文件可属于多个 fileset）。
- 构建逻辑依据命令语义（如 `\\input`/`\\include`/`\\subfile`/`\\usepackage`/bibliography）。
- `\\externaldocument` 默认不并入 fileset 文件集合，只记录外部文档信息。
- 参考实现入口：`LatexProjectStructure`、`FilesetProcessor`、`Fileset`、`FilesetData`。

## 后续工具（第二批）
1. `parse_latex`：返回 PSI 树摘要。  
2. `resolve_symbol`：按位置解析定义。  
3. `find_references`：符号反向引用。  
4. `diagnostics`：结构与语义诊断。  

## 关键实现约束
- Tool `inputSchema` 必须完整（必填、类型、枚举），并对输入做严格校验。
- 长任务支持 progress（可选），错误分两类：
  - 协议错误（JSON-RPC error）
  - 业务错误（`result.isError = true`）
- 对 `tools/list` 做分页预留（即使第一版工具少）。
- 避免在 stdout 输出非 JSON-RPC 内容；日志写 stderr。

## 安全与信任
- 仅允许访问用户显式授权的 workspace/root。
- 默认只读；未来如加写操作，需单独工具并标注 destructive。
- 对路径、URI、参数做白名单和规范化处理，防止目录穿越。

## 参考规范
- MCP Spec Index: https://modelcontextprotocol.io/specification/2025-03-26/index
- Architecture: https://modelcontextprotocol.io/specification/2025-06-18/architecture
- Lifecycle: https://modelcontextprotocol.io/specification/2025-03-26/basic/lifecycle
- Transports: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports
- Tools: https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- Resources: https://modelcontextprotocol.io/specification/2025-03-26/server/resources
- Prompts: https://modelcontextprotocol.io/specification/2025-11-25/server/prompts
- Pagination: https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/pagination
