# Source layout and build inputs

This repository contains the complete WorldBinder 1.7.0 source for Fabric and NeoForge, plus the source tests needed to maintain it.

- `src/main/`: shared capture, project storage, export, UI, rendering, movement and required resources.
- `src/fabric/` and `neoforge/src/main/`: loader adapters and metadata.
- `src/clientTest/`, `src/sharedTest/`, `src/regressionTest/` and `neoforge/src/runtimeTest/`: isolated test source sets. They are excluded from production JARs.
- `scripts/qa/`: language checks and reproducible local client/server/replay/import helpers with pinned download inventories. External Minecraft/mod/server binaries are not bundled.
- `gradle/`, `gradlew`, `gradlew.bat`, `build.gradle.kts`, `neoforge/build.gradle.kts`, `settings.gradle.kts` and `gradle.properties`: required build inputs. The Gradle wrapper JAR is intentional.
- `src/main/resources/data/worldbinder/target-registry/`: required target registry facts and provenance, not disposable build output.
- `src/main/resources/data/worldbinder/gamerules/`: game-rule facts per Minecraft version, generated from the client jars by `scripts/qa/generate-gamerule-facts.mjs`; required at runtime.
- Root community documents, `HOW_TO_USE.md`, `CHANGELOG.md` and the user guides under `docs/`: source usage, contribution instructions, changes and known limits.

Generated JARs/classes, Gradle/IDE caches, logs, crash reports, downloaded test tools, captured worlds and machine-local QA output are ignored. Platform publishing copy, product screenshots, release-packaging helpers and historical internal QA reports stay in the development repository. A source checkout does not need those files to build or run its data checks.

Use Java 25 and Node.js. Build and verify from this root:

```shell
./gradlew :build :neoforge:build :regressionTest :compileClientTestJava :neoforge:compileRuntimeTestJava --no-parallel
```

Production output: `build/libs/worldbinder-1.7.0+mc26.1-26.3-fabric.jar` and `neoforge/build/libs/worldbinder-1.7.0+mc26.3-neoforge.jar`. See [CONTRIBUTING.md](../CONTRIBUTING.md) for optional native tests and [verification](1.7.0-validation.md) for the distinction between source builds, data checks and completed gameplay acceptance.
