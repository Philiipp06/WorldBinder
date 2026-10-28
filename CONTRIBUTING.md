# Contributing to WorldBinder

Thank you for wanting to help with WorldBinder! Bug reports, ideas, translations, documentation, testing on different Minecraft versions and code are all welcome. This guide explains how the project works and how to get your contribution merged.

By participating you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Quick links

- Release repository and issues: https://github.com/Philiipp06/WorldBinder ([issues](https://github.com/Philiipp06/WorldBinder/issues))
- Development repository: https://github.com/Philiipp06/WorldBinder-DEVELOPMENT
- Discord: https://discord.com/invite/V7h3vaXxDZ
- User guide: [HOW_TO_USE.md](HOW_TO_USE.md)
- Security problems: [SECURITY.md](SECURITY.md), never as public issues
- Accessibility: [ACCESSIBILITY.md](ACCESSIBILITY.md)

## Project boundaries

The [1.7.0 validation record](docs/1.7.0-validation.md) separates builds, standalone data checks, actual client/server execution and import tests. Run `gradlew checkLanguages regressionTest` for DE/EN key/placeholder/argument checks and archive regressions. Native expansion tests use `:runExpansionTest :runExpansionRestartTest` and the matching `:neoforge:` tasks; they exercise real packet receipt, bounded selections, scanner cancellation, filters, both languages and process restart. Their helpers live in separate test source sets and must never enter production jars.

Real schematic interoperability uses pinned WorldEdit, Litematica/MaLiLib and FAWE/Paper releases. `scripts/qa/download-import-tools.ps1` verifies downloads against `scripts/qa/import-tools.json`. These are local QA tools, not WorldBinder dependencies or bundled binaries. Follow the full commands and environment limits in the validation record. Node.js is needed for the language audit and dedicated-server QA helper; Java 25 is needed for the current build.

WorldBinder archives data the client has **already received** and exports it as vanilla worlds. Before proposing a feature, please check it fits these boundaries. We do not accept contributions that:

- reveal hidden world data, request data the server did not send, or otherwise act as an x-ray or "world downloader" that goes beyond what the client received,
- bypass or hide from server permissions, anti-cheat systems or bans, including Movement Tools changes meant to avoid detection,
- send any data to external services (WorldBinder works fully offline and contains no telemetry),
- add closed-source or license-incompatible code or assets (the project is MIT licensed).

If you are unsure, open an issue or ask on Discord first.

## Reporting bugs

Search the [existing issues](https://github.com/Philiipp06/WorldBinder/issues) first. A good bug report contains:

- Minecraft, WorldBinder, loader/version (and Fabric API on Fabric), plus other installed mods,
- the selected target Minecraft version (for export problems),
- window mode and GUI scale (for interface problems),
- what you did, what you expected and what happened instead,
- `latest.log`, crash reports, screenshots or a small test world if possible.

Please remove private information (other players' data, server addresses you do not want to share) before uploading logs or worlds.

## Suggesting features

Describe the problem you want to solve, not only the solution. Mention who benefits (players, builders, server owners …) and whether it touches capture, export, the interface, the live map or Movement Tools. Larger changes should be discussed in an issue before you start coding, so your work fits the plans.

## Translations

The interface ships in English (`en_us.json`), German (`de_de.json`) and French (`fr_fr.json`) under `src/main/resources/assets/worldbinder/lang/`. English is the reference catalog. Update **all three** files for every user-facing text and keep `fr_fr.json` in the same key order as `en_us.json`, including status details, exceptions and generated help. Use Minecraft placeholders (`%s`, indexed `%1$s`, and `%%` for a literal percent), preserving their arguments and line breaks. Keep labels short enough for the actual buttons. The [language audit](scripts/qa/check-language.mjs) enforces catalog, placeholder and call-argument coverage.

A new language needs its catalog, an entry in `Lang.LANGUAGES`, a mapping in `Lang.bundle` and the audit in `scripts/qa/check-language.mjs`; the native UI scenario iterates `Lang.LANGUAGES` automatically. Generated export files intentionally stay German/English.

Use `Lang.text`/`Lang.string` for display, `Lang.stored` or structured `Finding` objects for persisted/asynchronous messages, and `Lang.in(language, ...)` for explicitly localized generated files. Nested findings must retain their keys. Legacy literal report strings and captured player/server content remain readable without being rewritten. Do not replace registry IDs, NBT keys, file syntax, paths or diagnostic resource names with translated values.

Run `gradlew checkLanguages regressionTest`. The build also runs the six Node tests for the audit itself. Register dynamic key families when adding them; enum-derived keys and conditional call arities are checked. The reviewed literal exemptions and shared DE/EN values explain intentional technical/proper-name cases and reject stale entries. Do not add an exemption for untranslated interface prose. Native language checks run through the shared expansion scenario on Fabric and NeoForge and compare every catalog entry after real resource-language reloads.

Generated map/world/legal help uses the same catalog. Both loader descriptions are built from `modmenu.descriptionTranslation.worldbinder`; change the language values rather than hardcoding loader metadata. Mod Menu can show its selected-language description; other metadata consumers receive both languages.

## Development setup

Requirements:

- Java 25 (JDK)
- Git
- Node.js for the language audit and optional network/replay QA helpers
- An IDE with Gradle support (IntelliJ IDEA recommended)

```bash
git clone https://github.com/Philiipp06/WorldBinder.git
cd WorldBinder
./gradlew build
```

Useful tasks:

| Task | Purpose |
| ---- | ------- |
| `./gradlew build` | Builds Fabric and NeoForge artifacts (root and `neoforge/build/libs/`) |
| `./gradlew buildFabric` / `./gradlew buildNeoForge` | Builds the selected loader artifact |
| `./gradlew regressionTest` | Standalone archive/update/read-back/failure tests using Minecraft classes; no in-game claim |
| `./gradlew runClient` | Starts a development client with WorldBinder |
| `./gradlew :neoforge:runClient` | Starts a NeoForge 26.3 development client |
| `./gradlew runClientTest` | Runs the automated client game test (UI, settings, movement, live map, capture/cache/recovery/export) and writes screenshots to `build/client-test/screenshots` |
| `./gradlew runProjectTest runProjectRestartTest` | Real Fabric client/server packets, export opening, then archive resume/re-export in a separate Minecraft process |
| `./gradlew :neoforge:runSmokeTest` | Real NeoForge client/integrated-server capture, movement, UI, reload and export smoke checks |
| `./gradlew largeProjectTest largeProjectRestartTest` | Measured 10,000-chunk cache/export/restore workload with a separate JVM |
| `node scripts/qa/dedicated-server.mjs` then `./gradlew runServerRecordTest runServerResumeTest` | Real vanilla multiplayer with an HTTP pack, long capture, autosaves and full process restart; loopback test setup only |
| `./gradlew :neoforge:runMultiplayerSmokeTest` | NeoForge client-only command and actual pack texture on that unmodded dedicated server |

WorldBinder ships **one Fabric jar for Minecraft 26.1 – 26.3** and a separate **NeoForge 26.3** jar. Shared code is in `src/main/`; loader event/key/render/path integration is in `src/fabric/` and `neoforge/src/main/`. Keep common code independent of loader APIs. Tests are separate source sets. The build targets 26.3; Fabric compatibility is checked by loading the packaged jar on older versions, for example (quote each `-P...` argument in PowerShell):

```bash
./gradlew runClientTest -PcompatibilityJar=build/libs/worldbinder-<version>+mc26.1-26.3-fabric.jar -Pminecraft_version=26.1 -Pfabric_version=0.145.1+26.1 -Pmodmenu_version=18.0.0
```

If you use Minecraft classes that changed between 26.1 and 26.3, go through the reflection helpers in `net.worldbinder.compat` instead of calling them directly.

See [1.7.0 verification](docs/1.7.0-validation.md) for target-native server commands and the actual evidence. Report builds, standalone tests, automated in-game tests and manual acceptance separately. Do not turn declared compatibility or shared code into an assertion that every modpack or Minecraft target was tested.

## Code guidelines

- Match the style of the surrounding code: naming, comment density, formatting and the existing helpers (`WbButton`, `WbChrome`, `WbTheme`, `Lang` …).
- Keep work off the render thread when it can be slow (exports, file IO) and respect the existing per-frame budgets.
- Every user-facing text goes through the language files; never hard-code English strings in the UI.
- Interface changes must follow the accessibility checklist in [ACCESSIBILITY.md](ACCESSIBILITY.md): keyboard reachable, readable in high contrast, no information by color alone, respects reduced motion.
- Do not add dependencies without discussing them first.
- Add or extend the client test when you change capture, export or important UI flows.

## Pull requests

1. Fork the development repository and create a branch from `main` (`fix/map-hover`, `feature/waypoint-colors` …).
2. Keep pull requests focused; one topic per PR is much easier to review.
3. Run `./gradlew build runClientTest` before opening the PR and mention the result. For version-specific changes, also run the compatibility test on the affected versions.
4. Describe what changed and why, link related issues ("Fixes #123"), and add screenshots for interface changes.
5. Update `README.md`, `HOW_TO_USE.md` or the changelog when behavior visible to users changes.

The maintainer reviews pull requests in spare time; feedback may take a few days. Reviews are about the code, not about you. If a change is not accepted, we explain why.

## Releases

Releases are prepared by the maintainer. The version is configured in `gradle.properties` (`mod_version`); the build expands loader metadata, and the displayed `WbBrand.VERSION` must agree. Platform-specific copy and product screenshots are maintained separately in the development repository. Please do not bump versions in pull requests.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE) of this project.
