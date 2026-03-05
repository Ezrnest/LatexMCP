package com.github.ezrnest.latexmcp.settings

import com.github.ezrnest.latexmcp.transport.http.LatexMcpHttpService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * IDE Settings page for MCP HTTP port configuration.
 *
 * Port changes are applied immediately by restarting the in-process HTTP server.
 */
class LatexMcpConfigurable : Configurable {

    private var panel: JPanel? = null
    private var portSpinner: JSpinner? = null

    override fun getDisplayName(): String = "LatexMCP"

    override fun createComponent(): JComponent {
        val spinner = JSpinner(SpinnerNumberModel(LatexMcpSettingsService.DEFAULT_PORT, 1, 65535, 1))
        portSpinner = spinner

        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent("HTTP Port:", spinner)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel = built
        reset()
        return built
    }

    override fun isModified(): Boolean {
        val spinner = portSpinner ?: return false
        return spinner.intValue() != settings().getPort()
    }

    override fun apply() {
        val spinner = portSpinner ?: return
        val oldPort = settings().getPort()
        val newPort = spinner.intValue()
        settings().setPort(newPort)

        if (oldPort != newPort) {
            httpService().restartIfRunning()
        }
    }

    override fun reset() {
        portSpinner?.value = settings().getPort()
    }

    override fun disposeUIResources() {
        panel = null
        portSpinner = null
    }

    private fun settings(): LatexMcpSettingsService =
        ApplicationManager.getApplication().getService(LatexMcpSettingsService::class.java)

    private fun httpService(): LatexMcpHttpService =
        ApplicationManager.getApplication().getService(LatexMcpHttpService::class.java)

    private fun JSpinner.intValue(): Int = (this.value as? Int) ?: LatexMcpSettingsService.DEFAULT_PORT
}
