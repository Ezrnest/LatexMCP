plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    testImplementation(kotlin("test"))

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        plugins(
            providers.gradleProperty("platformPlugins").map { property ->
                property.split(',').map(String::trim).filter(String::isNotEmpty)
            }
        )
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild")
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    register<JavaExec>("runMcpStdio") {
        group = "run"
        description = "Run LatexMCP as a standalone MCP stdio server process."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.github.ezrnest.latexmcp.transport.stdio.LatexMcpStdioMain")
        standardInput = System.`in`
        standardOutput = System.out
        errorOutput = System.err
    }

    register<JavaExec>("runMcpHttp") {
        group = "run"
        description = "Run LatexMCP as a standalone MCP HTTP server process on port 18765 (configurable via LATEX_MCP_PORT)."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.github.ezrnest.latexmcp.transport.http.LatexMcpHttpMain")
        standardInput = System.`in`
        standardOutput = System.out
        errorOutput = System.err
    }

    named("buildSearchableOptions") {
        enabled = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
