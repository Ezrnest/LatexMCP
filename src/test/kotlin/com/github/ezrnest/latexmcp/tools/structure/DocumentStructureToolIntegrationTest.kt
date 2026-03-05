package com.github.ezrnest.latexmcp.tools.structure

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
                Triple("include", "\\input", 3),
                Triple("section", "\\section", 4),
                Triple("label", "\\label", 5),
                Triple("section", "\\subsection", 7),
                Triple("section", "\\paragraph", 8),
                Triple("label", "\\label", 9),
            ),
            simplified,
        )

        assertEquals("sections/a", result.entries[0].includeTarget)
        assertEquals(listOf("sections/a.tex"), result.entries[0].resolvedFiles)
        assertEquals("Intro", result.entries[1].title)
        assertEquals("sec:intro", result.entries[2].label)
        assertEquals("par:detail", result.entries[5].label)
    }
}
