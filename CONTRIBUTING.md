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

WorldBinder archives data the client has **already received** and exports it as vanilla worlds. Before proposing a feature, please check it fits these boundaries. We do not accept contributions that:

- reveal hidden world data, request data the server did not send, or otherwise act as an x-ray or "world downloader" that goes beyond what the client received,
- bypass or hide from server permissions, anti-cheat systems or bans, including Movement Tools changes meant to avoid detection,
- send any data to external services (WorldBinder works fully offline and contains no telemetry),
- add closed-source or license-incompatible code or assets (the project is MIT licensed).

If you are unsure, open an issue or ask on Discord first.

## Reporting bugs

Search the [existing issues](https://github.com/Philiipp06/WorldBinder/issues) first. A good bug report contains:

- Minecraft, WorldBinder, Fabric Loader and Fabric API versions, plus other installed mods,
- the selected target Minecraft version (for export problems),
- window mode and GUI scale (for interface problems),
- what you did, what you expected and what happened instead,
- `latest.log`, crash reports, screenshots or a small test world if possible.

Please remove private information (other players' data, server addresses you do not want to share) before uploading logs or worlds.

## Suggesting features

Describe the problem you want to solve, not only the solution. Mention who benefits (players, builders, server owners …) and whether it touches capture, export, the interface, the live map or Movement Tools. Larger changes should be discussed in an issue before you start coding, so your work fits the plans.

## Translations

The interface ships in English (`en_us.json`) and German (`de_de.json`) under `src/main/resources/assets/worldbinder/lang/`. When you add or change a text key, update **both** files. New languages are welcome: copy `en_us.json`, translate the values, keep every `%s` placeholder and `\n` line break, and keep texts short enough for the buttons.

## Development setup

Requirements:

- Java 25 (JDK)
- Git
- An IDE with Gradle support (IntelliJ IDEA recommended)

```bash
git clone https://github.com/Philiipp06/WorldBinder-DEVELOPMENT.git
cd WorldBinder-DEVELOPMENT
./gradlew build
```

Useful tasks:

| Task | Purpose |
| ---- | ------- |
| `./gradlew build` | Compiles and builds `build/libs/worldbinder-<version>+mc26.1-26.3.jar` |
| `./gradlew runClient` | Starts a development client with WorldBinder |
| `./gradlew runClientTest` | Runs the automated client game test (UI, settings, movement, live map, capture/cache/recovery/export) and writes screenshots to `build/client-test/screenshots` |

WorldBinder ships **one jar for Minecraft 26.1 – 26.3**. The build targets 26.3; compatibility is checked by loading the packaged jar on older versions, for example:

```bash
./gradlew runClientTest -PcompatibilityJar=build/libs/worldbinder-<version>+mc26.1-26.3.jar -Pminecraft_version=26.1 -Pfabric_version=0.145.1+26.1 -Pmodmenu_version=18.0.2
```

If you use Minecraft classes that changed between 26.1 and 26.3, go through the reflection helpers in `net.worldbinder.compat` instead of calling them directly.

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

Releases are prepared by the maintainer. Version numbers live in `gradle.properties` (`mod_version`), `fabric.mod.json` and `WbBrand.VERSION`; release texts are kept in `Publish/`. Please do not bump versions in pull requests.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE) of this project.
