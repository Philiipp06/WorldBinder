package net.worldbinder.export.common;

/**
 * Legal and responsible-use notice appended to every info/readme text file WorldBinder writes next to an export
 * (world exports, validation reports, 2D and 3D map exports), so the notice travels with the files.
 */
public final class ExportLegalNotice {
    private ExportLegalNotice() {
    }

    /** Plain text block (for .txt files). */
    public static String text() {
        return """

                ------------------------------------------------------------
                LEGAL NOTICE / RESPONSIBLE USE
                ------------------------------------------------------------
                - This export was created with WorldBinder, a client-side Minecraft mod. It contains only data that the
                  Minecraft client received while playing; it is not a complete or authoritative copy of any world or server.
                - The exported worlds, builds, maps, textures, signs, books and other content remain the property of their
                  respective creators, server owners and rights holders. WorldBinder does not grant any rights to copy,
                  redistribute, publish, sell or otherwise use this content.
                - Only use, keep or share this export if you own the content, administer the server, created the build
                  yourself, or have explicit permission from the rights holders.
                - Exports can contain personal data of other players (for example names, skins, signs, books or chat-like
                  text). Check the export before sharing it and respect applicable privacy laws such as the GDPR.
                - The WorldBinder MIT license covers the WorldBinder software only, not the exported content.
                - NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
                  Minecraft is a trademark of Mojang Synergies AB / Microsoft.
                - The export is provided "as is", without warranty of any kind. You are responsible for how you use it.
                """;
    }

    /** Markdown section (for README.md files). */
    public static String markdown() {
        return """

                ## Legal notice

                - This export was created with WorldBinder, a client-side Minecraft mod. It contains only data that the Minecraft client received while playing; it is not a complete or authoritative copy of any world or server.
                - The exported worlds, builds, maps, textures, signs, books and other content remain the property of their respective creators, server owners and rights holders. WorldBinder does not grant any rights to copy, redistribute, publish, sell or otherwise use this content.
                - Only use, keep or share this export if you own the content, administer the server, created the build yourself, or have explicit permission from the rights holders.
                - Exports can contain personal data of other players (for example names, skins, signs, books or chat-like text). Check the export before sharing it and respect applicable privacy laws such as the GDPR.
                - The WorldBinder MIT license covers the WorldBinder software only, not the exported content.
                - **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.** Minecraft is a trademark of Mojang Synergies AB / Microsoft.
                - The export is provided "as is", without warranty of any kind. You are responsible for how you use it.
                """;
    }

    /** One line for embedded metadata (glTF asset copyright, PNG text). */
    public static String shortLine() {
        return "Content belongs to its creators and rights holders; exported with WorldBinder from client-received data. "
                + "Use only with permission. Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.";
    }
}
