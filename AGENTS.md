# Repository Guidelines

## Project Structure & Module Organization
- Root build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and Gradle wrapper scripts.
- Main plugin code: `src/main/kotlin/com/github/ezrnest/latexmcp`.
- Plugin metadata and resources: `src/main/resources` (notably `META-INF/plugin.xml`, icons, message bundles).
- Generated and sandbox artifacts: `build/` (do not commit).
- Add tests under `src/test/kotlin` and test resources under `src/test/resources`.

## Build, Test, and Development Commands
- `./gradlew build`: full compile/package lifecycle for the plugin.
- `./gradlew test`: run automated tests.
- `./gradlew runIde`: launch a sandbox IntelliJ instance with this plugin.
- `./gradlew verifyPlugin`: run IntelliJ plugin verification checks.
- `./gradlew help`: quick sanity check for Gradle configuration changes.

Use the wrapper (`./gradlew`) rather than a system Gradle installation.

## Coding Style & Naming Conventions
- Language: Kotlin (JVM 21 target). Keep code compatible with project Gradle settings.
- Indentation: 4 spaces; avoid tabs.
- Package names: lowercase (e.g., `com.github.ezrnest.latexmcp`).
- Types: `PascalCase`; functions/variables: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep classes focused; extract reusable PSI/analysis logic into dedicated services/utilities.
- Prefer explicit, descriptive names (`LatexPsiParserService` over `Utils`).

## Testing Guidelines
- Framework baseline: IntelliJ Platform test framework via Gradle `test` task.
- Name tests by behavior, e.g., `ParseLatexCommandTest`, `ResolveLabelReferenceTest`.
- Test both happy paths and malformed LaTeX input.
- For PSI features, keep fixture snippets small and assert node type, range, and key text.

## Commit & Pull Request Guidelines
- Current history is minimal (`init`), so use short imperative commit messages.
- Recommended format: `scope: summary` (example: `parser: add PSI tree serializer`).
- PRs should include:
  - What changed and why.
  - How to validate (`./gradlew build`, relevant tests).
  - Any plugin.xml or dependency changes.
  - Screenshots/log snippets only when UI/runtime behavior changed.

## Security & Configuration Tips
- Never commit secrets or local IDE paths.
- Keep plugin/version settings in `gradle.properties`.
- If adding publish/sign tasks later, pass tokens via environment variables, not source files.

## Local Reference Project
- If available on your machine, use `../TeXiFy-IDEA` as a local reference for LaTeX PSI patterns, parser usage, and IntelliJ plugin implementation details.
