package net.worldbinder.export.common;

import net.worldbinder.util.Lang;

/** Bilingual notices from the same language catalog as the UI, including offline exports. */
public final class ExportLegalNotice {
    private ExportLegalNotice() {}

    public static String text() { return notice(false); }
    public static String markdown() { return notice(true); }

    public static String shortLine() {
        return Lang.in("de_de", "worldbinder.generated.legal.short") + " / "
                + Lang.in("en_us", "worldbinder.generated.legal.short");
    }

    private static String notice(boolean markdown) {
        var text = new StringBuilder();
        for (String language : new String[]{"de_de", "en_us"}) {
            text.append('\n').append(markdown ? "## " : "")
                    .append(Lang.in(language, "worldbinder.generated.legal.title")).append('\n');
            if (!markdown) text.append("------------------------------------------------------------\n");
            for (int bullet = 1; bullet <= 7; bullet++)
                text.append("- ").append(Lang.in(language, "worldbinder.generated.legal." + bullet)).append('\n');
        }
        return text.toString();
    }
}
