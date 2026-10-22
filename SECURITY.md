# Security Policy

WorldBinder is a client-side Fabric mod. It runs inside the Minecraft client, reads world data the client has received, reads resource pack files, and writes archives, exports and configuration files to your computer. This document explains which versions receive security fixes, what counts as a security problem in WorldBinder, and how to report one privately.

## Supported versions

Security fixes are released for the latest WorldBinder version only. Please update before reporting, and mention if the problem also exists in older versions.

| WorldBinder | Minecraft | Supported |
| ----------- | --------- | --------- |
| 1.6.x (latest) | 26.1 – 26.3 | :white_check_mark: |
| 1.5.x and older | 26.1 – 26.3 and earlier | :x: |

Only builds downloaded from the official [GitHub releases](https://github.com/Philiipp06/WorldBinder/releases) or the official Modrinth project are supported. Re-uploads on other sites may be modified.

## What is a security problem?

Please report privately anything that could harm a WorldBinder user, their computer, their files or their accounts, for example:

- **Untrusted input from servers or files**: a server, world, schematic, archive, recovery file or resource pack that makes WorldBinder crash the client in a way that can be triggered remotely, execute code, or read or write files outside the WorldBinder folders.
- **Path handling**: export, archive or map export names that escape the intended folder (path traversal), overwrite unrelated files, or follow links unexpectedly.
- **Resource pack parsing**: malicious blockstate, model or texture files (used for map colors since 1.6.2) that cause hangs, excessive memory use or crashes.
- **Data exposure**: WorldBinder writing or sending more data than documented, for example personal data, session tokens or server addresses ending up in exports, logs or files meant to be shared.
- **Supply chain**: problems with the build, the published jar or dependencies.

The following are **not** security vulnerabilities and should be reported as normal issues or not at all:

- Bugs without a security impact (wrong colors, UI glitches, export errors). Use the [issue tracker](https://github.com/Philiipp06/WorldBinder/issues).
- WorldBinder saving data that the client legitimately received from a server. This is the purpose of the mod; please read the responsible-use section in the [README](README.md).
- Requests to bypass anti-cheat systems, server permissions or bans, or reports that a server detects Movement Tools. WorldBinder does not support circumventing server rules.
- Vulnerabilities in Minecraft, Fabric Loader, Fabric API or other mods. Report them to the respective project; let us know if WorldBinder makes them worse.

## Reporting a vulnerability

**Please do not open a public issue, pull request or Discord message for security problems.**

1. Preferred: use GitHub's private reporting on the release repository: [Report a vulnerability](https://github.com/Philiipp06/WorldBinder/security/advisories/new) (Security tab → "Report a vulnerability").
2. If that is not available to you: send a direct message to the maintainer on the [WorldBinder Discord](https://discord.com/invite/V7h3vaXxDZ) and ask for a private channel. Do not post details in public channels.

Helpful information:

- WorldBinder, Minecraft, Fabric Loader and Fabric API versions, plus other installed mods.
- What an attacker needs (a malicious server, a shared archive file, a resource pack …) and what they can achieve.
- Steps or a minimal file that reproduces the problem. Please do not include real players' private data or content you are not allowed to share.
- Whether you would like to be credited, and under which name.

## What happens next

- We aim to acknowledge reports within **7 days** and keep you updated at least every two weeks while we work on it.
- We confirm the problem, assess its impact and prepare a fix in the private development repository.
- A fixed version is released on GitHub and Modrinth. The release notes describe the issue once users have had a chance to update, and credit you if you want.
- If a report is declined (for example because it is out of scope), we explain why.

WorldBinder is maintained in spare time, so these are goals, not guarantees. Please give us a reasonable time to fix a problem before disclosing it publicly; we will coordinate a disclosure date with you.

## Staying safe as a user

- Download WorldBinder only from the official GitHub releases or Modrinth.
- Only open archives, recovery files and exported worlds from people you trust.
- Captured worlds can contain other players' names, signs, books and builds. Check exports before sharing them.
