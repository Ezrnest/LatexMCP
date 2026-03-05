package com.github.ezrnest.latexmcp.tools.search

import com.github.ezrnest.latexmcp.updateFilesets
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class StructuredSearchToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testStructuredSearchCommandLiteralInFileset() {
        myFixture.copyDirectoryToProject("search/simple", "")
        myFixture.updateFilesets()

        val result = StructuredSearchTool.execute(
            StructuredSearchToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
                namePattern = "section",
                type = "command",
            ),
        )

        assertEquals("fileset", result.scope)
        assertEquals("main.tex", result.targetContext)
        assertEquals(1, result.count)
        assertEquals("\\section", result.results[0].name)
        assertEquals("main.tex", result.results[0].file)
    }

    fun testStructuredSearchEnvironmentLiteralInFileset() {
        myFixture.copyDirectoryToProject("search/simple", "")
        myFixture.updateFilesets()

        val result = StructuredSearchTool.execute(
            StructuredSearchToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
                namePattern = "itemize",
                type = "environment",
            ),
        )

        assertEquals(1, result.count)
        assertEquals("environment", result.results[0].type)
        assertEquals("itemize", result.results[0].name)
        assertEquals("main.tex", result.results[0].file)
    }

    fun testStructuredSearchSingleDocumentScope() {
        myFixture.copyDirectoryToProject("search/simple", "")
        myFixture.updateFilesets()

        val result = StructuredSearchTool.execute(
            StructuredSearchToolParams(
                projectPath = project.basePath!!,
                scope = "single_document",
                texFile = "main.tex",
                namePattern = "\\sub*",
                type = "command",
                patternMode = "wildcard",
            ),
        )

        assertEquals(1, result.count)
        assertEquals("\\subsection", result.results[0].name)
        assertEquals("main.tex", result.results[0].file)
    }

    fun testStructuredSearchWildcardAcrossFileset() {
        myFixture.copyDirectoryToProject("search/simple", "")
        myFixture.updateFilesets()

        val result = StructuredSearchTool.execute(
            StructuredSearchToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
                namePattern = "\\sub*",
                type = "command",
                patternMode = "wildcard",
            ),
        )

        val names = result.results.map { it.name }
        assertTrue(names.contains("\\subsection"))
        assertTrue(names.contains("\\subsubsection"))
    }

    fun testStructuredSearchRegexWithTruncation() {
        myFixture.copyDirectoryToProject("search/simple", "")
        myFixture.updateFilesets()

        val result = StructuredSearchTool.execute(
            StructuredSearchToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
                namePattern = "/^\\\\(sub)?section$/",
                type = "command",
                patternMode = "regex",
                limit = 1,
            ),
        )

        assertEquals(1, result.count)
        assertTrue(result.truncated)
    }
}
