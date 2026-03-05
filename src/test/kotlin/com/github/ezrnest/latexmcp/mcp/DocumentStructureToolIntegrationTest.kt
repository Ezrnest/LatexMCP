package com.github.ezrnest.latexmcp.mcp

import com.github.ezrnest.latexmcp.updateCommandDef
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals

class DocumentStructureToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testDocumentStructureReturnsOrderedSectionsAndLabelsWithLineNumbers() {
        myFixture.copyDirectoryToProject("structure/simple", "")
        myFixture.updateCommandDef()

        val result = DocumentStructureTool.execute(
            DocumentStructureToolParams(
                projectPath = project.basePath!!,
                texFile = "main.tex",
            ),
        )

        assertEquals("main.tex", result.texFile)

        val simplified = result.entries.map { Triple(it.kind, it.command, it.line) }
        assertEquals(
            listOf(
                Triple("section", "\\section", 3),
                Triple("label", "\\label", 4),
                Triple("section", "\\subsection", 6),
                Triple("section", "\\paragraph", 7),
                Triple("label", "\\label", 8),
            ),
            simplified,
        )

        assertEquals("Intro", result.entries[0].title)
        assertEquals("sec:intro", result.entries[1].label)
        assertEquals("par:detail", result.entries[4].label)
    }
}
