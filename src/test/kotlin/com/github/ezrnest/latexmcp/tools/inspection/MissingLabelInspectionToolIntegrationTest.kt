package com.github.ezrnest.latexmcp.tools.inspection

import com.github.ezrnest.latexmcp.updateFilesets
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MissingLabelInspectionToolIntegrationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    fun testMissingLabelInspectionInFileset() {
        myFixture.copyDirectoryToProject("inspection/missing_label/simple", "")
        myFixture.updateFilesets()

        val result = MissingLabelInspectionTool.execute(
            MissingLabelInspectionToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
            ),
        )

        assertEquals("fileset", result.scope)
        assertEquals("main.tex", result.targetContext)
        assertTrue(result.count >= 2)

        val locations = result.issues.map { it.file to it.line }.toSet()
        assertTrue(locations.contains("main.tex" to 4))
        assertTrue(locations.contains("sections/a.tex" to 1))
        assertFalse(locations.contains("main.tex" to 5))
    }

    fun testMissingLabelInspectionInSingleDocument() {
        myFixture.copyDirectoryToProject("inspection/missing_label/simple", "")
        myFixture.updateFilesets()

        val result = MissingLabelInspectionTool.execute(
            MissingLabelInspectionToolParams(
                projectPath = project.basePath!!,
                scope = "single_document",
                texFile = "main.tex",
            ),
        )

        assertEquals("single_document", result.scope)
        assertEquals(1, result.count)
        assertEquals("main.tex", result.issues[0].file)
        assertEquals(4, result.issues[0].line)
    }

    fun testMissingLabelInspectionLimitAndTruncation() {
        myFixture.copyDirectoryToProject("inspection/missing_label/simple", "")
        myFixture.updateFilesets()

        val result = MissingLabelInspectionTool.execute(
            MissingLabelInspectionToolParams(
                projectPath = project.basePath!!,
                scope = "fileset",
                mainTex = "main.tex",
                limit = 1,
            ),
        )

        assertEquals(1, result.count)
        assertTrue(result.truncated)
    }
}
