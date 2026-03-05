package com.github.ezrnest.latexmcp.tools.fileset

import com.github.ezrnest.latexmcp.updateFilesets
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FilesetToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testFilesetReturnsProjectRelativePathsAndHidesLibrariesByDefault() {
        myFixture.copyDirectoryToProject("fileset/simple", "")
        myFixture.updateFilesets()

        val result = FilesetTool.execute(
            FilesetToolParams(
                projectPath = project.basePath!!,
                texFile = "main.tex",
            ),
        )

        assertEquals("main.tex", result.targetFile)
        assertTrue(result.rootCandidates.contains("main.tex"))

        val files = result.files.sorted()
        assertTrue("files=$files", files.contains("main.tex"))
        assertTrue("files=$files", files.contains("sections/a.tex"))

        // default includeLibraries=false
        assertTrue(result.libraries.isEmpty())
    }

    fun testFilesetAcceptsRelativeTexFilePath() {
        myFixture.copyDirectoryToProject("fileset/simple", "")
        myFixture.updateFilesets()

        val result = FilesetTool.execute(
            FilesetToolParams(
                projectPath = project.basePath!!,
                texFile = "sections/a.tex",
            ),
        )

        assertEquals("sections/a.tex", result.targetFile)
        assertTrue(result.files.contains("sections/a.tex"))
    }
}
