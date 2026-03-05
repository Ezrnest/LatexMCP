package com.github.ezrnest.latexmcp.tools.labels

import com.github.ezrnest.latexmcp.updateFilesets
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LabelLocationsToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testLabelLocationsReturnsDefinitionAndReferencesByDefault() {
        myFixture.copyDirectoryToProject("labels/simple", "")
        myFixture.updateFilesets()

        val result = LabelLocationsTool.execute(
            LabelLocationsToolParams(
                projectPath = project.basePath!!,
                mainTex = "main.tex",
                label = "sec:intro",
            ),
        )

        assertEquals("main.tex", result.mainTex)
        assertEquals("sec:intro", result.label)
        assertEquals(1, result.definitions.size)
        assertEquals(2, result.references.size)

        val definition = result.definitions.single()
        assertEquals("main.tex", definition.file)
        assertEquals(3, definition.line)

        val referenceFilesAndLines = result.references.map { it.file to it.line }.toSet()
        assertTrue(referenceFilesAndLines.contains("main.tex" to 4))
        assertTrue(referenceFilesAndLines.contains("sections/a.tex" to 1))
    }

    fun testLabelLocationsCanDisableReferences() {
        myFixture.copyDirectoryToProject("labels/simple", "")
        myFixture.updateFilesets()

        val result = LabelLocationsTool.execute(
            LabelLocationsToolParams(
                projectPath = project.basePath!!,
                mainTex = "main.tex",
                label = "sec:intro",
                includeReferences = false,
            ),
        )

        assertEquals(1, result.definitions.size)
        assertTrue(result.references.isEmpty())
    }
}
