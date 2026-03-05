package com.github.ezrnest.latexmcp.tools.labels

import com.github.ezrnest.latexmcp.updateFilesets
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class RenameLabelSafeToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testRenameLabelSafeRenamesDefinitionAndReferences() {
        myFixture.copyDirectoryToProject("rename/simple", "")
        myFixture.updateFilesets()

        val result = RenameLabelSafeTool.execute(
            RenameLabelSafeToolParams(
                projectPath = project.basePath!!,
                mainTex = "main.tex",
                oldLabel = "sec:intro",
                newLabel = "sec:introduction",
                applyChanges = true,
            ),
        )

        assertEquals(1, result.definitionsFound)
        assertTrue(result.referencesFound >= 2)
        assertTrue(result.plannedEdits >= 3)
        assertEquals(result.plannedEdits, result.appliedEdits)
        assertTrue(result.changedFiles.contains("main.tex"))
        assertTrue(result.changedFiles.contains("sections/a.tex"))

        val mainText = myFixture.findFileInTempDir("main.tex").contentsToByteArray().toString(Charsets.UTF_8)
        val sectionText = myFixture.findFileInTempDir("sections/a.tex").contentsToByteArray().toString(Charsets.UTF_8)

        assertTrue(mainText.contains("\\label{sec:introduction}"))
        assertTrue(mainText.contains("\\ref{sec:introduction}"))
        assertFalse(mainText.contains("\\label{sec:intro}"))
        assertFalse(mainText.contains("\\ref{sec:intro}"))
        assertTrue(sectionText.contains("\\cref{sec:introduction}"))
        assertFalse(sectionText.contains("\\cref{sec:intro}"))
    }

    fun testRenameLabelSafePreviewOnlyDoesNotModifyFiles() {
        myFixture.copyDirectoryToProject("rename/simple", "")
        myFixture.updateFilesets()

        val result = RenameLabelSafeTool.execute(
            RenameLabelSafeToolParams(
                projectPath = project.basePath!!,
                mainTex = "main.tex",
                oldLabel = "sec:intro",
                newLabel = "sec:introduction",
                applyChanges = false,
            ),
        )

        assertTrue(result.plannedEdits >= 3)
        assertEquals(0, result.appliedEdits)

        val mainText = myFixture.findFileInTempDir("main.tex").contentsToByteArray().toString(Charsets.UTF_8)
        assertTrue(mainText.contains("\\label{sec:intro}"))
        assertFalse(mainText.contains("\\label{sec:introduction}"))
    }
}
