package com.github.ezrnest.latexmcp.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent application settings for the embedded MCP server.
 */
@Service(Service.Level.APP)
@State(
    name = "LatexMcpSettings",
    storages = [Storage("LatexMcpSettings.xml")],
)
class LatexMcpSettingsService : PersistentStateComponent<LatexMcpSettingsService.State> {

    /**
     * Serialized settings model stored in `LatexMcpSettings.xml`.
     */
    data class State(
        var port: Int = DEFAULT_PORT,
    )

    companion object {
        const val DEFAULT_PORT: Int = 18765
    }

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Returns a validated TCP port; invalid stored values are normalized to [DEFAULT_PORT].
     */
    fun getPort(): Int {
        val raw = state.port
        return if (raw in 1..65535) raw else DEFAULT_PORT
    }

    /**
     * Stores a validated TCP port; invalid inputs are replaced with [DEFAULT_PORT].
     */
    fun setPort(port: Int) {
        state.port = if (port in 1..65535) port else DEFAULT_PORT
    }
}
