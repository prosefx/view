package prosefx.view;

import java.util.regex.Pattern;

/**
 * The `>>` comment convention: a paragraph starting with `>>` is a comment in a thread, one starting `>> AI:` a reply
 * from the assistant (emphasis marks around "AI" ignored, as an editor may have added them). Only the look changes;
 * the text stays exactly as written.
 */
final class Comments {

    /** AI, with or without ^^, ** or _ around it and a stamp in brackets after it: `AI:`, `^^AI^^:`, `**AI** (2026-01-15 09:30 UTC):`. */
    static final Pattern AI = Pattern.compile("^[\\s^*_~]*AI(?:\\s*\\([^)]*\\))?[\\s^*_~]*:", Pattern.CASE_INSENSITIVE);

    /** pm-comment-ai, pm-comment-user, or null for an ordinary paragraph. */
    static String kind(String text) {
        var t = text.stripLeading();
        if (!t.startsWith(">>")) return null;
        return AI.matcher(t.substring(2)).find() ? "pm-comment-ai" : "pm-comment-user";
    }

    /** What is drawn in place of the two `>>` characters: a big angle for the user, a bullet for the assistant. The rest is made up with zero-width characters (see Decor), so the line still has one drawn character per document position. */
    static String glyph(String kind) { return "pm-comment-ai".equals(kind) ? "●" : "❯"; }

    private Comments() {}
}
