package prosemirrorfx.view;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import javafx.scene.text.Text;

import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * Links, written as [text](address) or just as an address in the text, and footnote references [^note]. Neither is
 * followed by a plain click, because a click places the caret in a text that is being edited: Ctrl+click, or Ctrl+Enter
 * with the caret in it, follows it. A web address goes to the DocView's link handler (the browser until the host sets
 * its own, which also gets relative paths); a footnote reference goes to its definition, and the label of a definition
 * goes back to the first reference.
 */
final class Links {

    static final Pattern URL = Pattern.compile("https?://[^\\s<>()\\[\\]\"']*[^\\s<>()\\[\\]\"'.,;:!?]"), DEFINITION = Pattern.compile("^\\[\\^([^\\]\\s]+)\\]:");

    /** What is at the document position: an address, "#fn:name" for a footnote reference, or null. */
    static String at(DocView view, int pos) {
        if (view.positions == null || view.positions.textblocks.isEmpty()) return null;
        var tb = view.positions.at(pos);
        var offset = pos - tb.start;
        var label = DEFINITION.matcher(tb.node.getTextContent());
        if (label.find() && offset <= label.end()) return "#fnback:" + label.group(1);
        var at = 0;
        for (var c : tb.node.getContent().getContent()) {
            var size = c.getNodeSize();
            if (c.isText() && offset >= at && offset <= at + size) {
                var link = LINK.isInSet(c.getMarks());
                if (link != null) return String.valueOf(link.getAttrs().get("href"));
                if (FOOTNOTE.isInSet(c.getMarks()) != null) return "#fn:" + c.getText().substring(2, c.getText().length() - 1);
            }
            at += size;
        }
        var text = tb.node.getTextContent();
        var m = URL.matcher(text);
        while (m.find()) if (offset >= m.start() && offset <= m.end()) return m.group();
        return null;
    }

    /** Follows what is at the position; false when there is nothing there to follow. */
    static boolean activate(DocView view, int pos) {
        var target = at(view, pos);
        if (target == null) return false;
        if (target.startsWith("#fn:")) return footnote(view, target.substring(4), true);
        if (target.startsWith("#fnback:")) return footnote(view, target.substring(8), false);
        view.linkHandler.accept(target);
        return true;
    }

    /** To the definition of the note (forward) or to the first place that refers to it (back). */
    static boolean footnote(DocView view, String id, boolean forward) {
        var label = "[^" + id + "]";
        for (var tb : view.positions.textblocks) {
            var text = tb.node.getTextContent();
            var isDefinition = text.startsWith(label + ":");
            if (forward ? isDefinition : !isDefinition && text.contains(label)) {
                view.model.select(prosemirrorfx.core.pm.Pm.cursor(view.model.state().getDoc(), tb.start + (forward ? 0 : text.indexOf(label))));
                return true;
            }
        }
        return false;
    }

    /** The default link handler: web and mail addresses to the system's browser or mail program; anything else is the host's to handle. */
    static void browse(String address) {
        var a = address.strip().toLowerCase();
        if (!(a.startsWith("http://") || a.startsWith("https://") || a.startsWith("mailto:"))) return;
        Thread.ofVirtual().start(() -> {
            try { java.awt.Desktop.getDesktop().browse(URI.create(address.strip())); } catch (Exception e) { System.err.println("prosemirrorfx: could not open " + address + ": " + e); }
        });
    }

    /** The runs with every bare web address in its own run, styled as a link. Marked links, code and raw text are left alone. */
    static List<Text> split(List<Text> runs) {
        var out = new ArrayList<Text>();
        for (var run : runs) {
            var classes = run.getStyleClass();
            var text = run.getText();
            if (text.indexOf("http") < 0 || classes.contains("code") || classes.contains("raw") || classes.contains("link")) { out.add(run); continue; }
            var m = URL.matcher(text);
            var last = 0;
            while (m.find()) {
                if (m.start() > last) out.add(piece(text.substring(last, m.start()), classes, false));
                out.add(piece(m.group(), classes, true));
                last = m.end();
            }
            if (last == 0) out.add(run); else if (last < text.length()) out.add(piece(text.substring(last), classes, false));
        }
        return out;
    }

    static Text piece(String text, List<String> classes, boolean url) {
        var t = new Text(text);
        t.getStyleClass().addAll(classes);
        if (url) t.getStyleClass().add("pm-autolink");
        return t;
    }

    private Links() {}
}
