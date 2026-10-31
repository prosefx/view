package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import java.util.regex.Pattern;
import javafx.scene.text.Text;

/**
 * How the Markdown that is kept as plain paragraph text is drawn: a horizontal rule (`---`), a quote (`> `), a task box
 * (`[ ]` / `[x]` at the start of a list item), and the comment `>>`. Only the look changes; the text stays as written. A
 * marker that is drawn as a glyph is replaced by that glyph followed by zero-width characters, so the flow still has one
 * drawn character for every document position.
 */
final class Decor {

    static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$"), QUOTE = Pattern.compile("^>(?!>)"), TASK = Pattern.compile("^\\[([ xX])\\](?=\\s|$)");

    /** The drawn runs of a plain paragraph, with its style classes added to the flow. */
    static List<Text> apply(Flow flow, Node node, boolean topLevel, List<Text> runs) {
        var text = node.getTextContent();
        if (topLevel && RULE.matcher(text).matches()) flow.getStyleClass().add("pm-rule");
        else if (QUOTE.matcher(text).find()) flow.getStyleClass().add("pm-quote");
        else if (topLevel && Links.DEFINITION.matcher(text).find()) flow.getStyleClass().add("pm-footnote-def");
        else if (!topLevel) {
            var task = TASK.matcher(text);
            if (task.find()) {
                var done = task.group(1).equalsIgnoreCase("x");
                flow.getStyleClass().addAll("pm-task", done ? "pm-task-done" : "pm-task-open");
                return swap(runs, 3, done ? "☑" : "☐", done ? "pm-task-done" : "pm-task-open", "pm-task-box");
            }
        }
        return runs;
    }

    /** The first n characters of the paragraph drawn as the glyph (plus zero-width characters to make up n); the rest of the first run keeps its styling. */
    static List<Text> swap(List<Text> runs, int n, String glyph, String... classes) {
        var first = runs.getFirst();
        if (first.getText().length() < n) return runs;
        var out = new ArrayList<Text>(runs.subList(1, runs.size()));
        if (first.getText().length() > n) {
            var tail = DocRenderer.text(first.getText().substring(n), List.of());
            tail.getStyleClass().setAll(first.getStyleClass());
            out.addFirst(tail);
        }
        out.addFirst(DocRenderer.text(glyph + "​".repeat(n - glyph.length()), List.of(classes)));
        return out;
    }

    /** A comment's `>>` as its glyph (see Comments.glyph). */
    static List<Text> comment(List<Text> runs, String kind) {
        return runs.getFirst().getText().startsWith(">>") ? swap(runs, 2, Comments.glyph(kind).substring(0, 1), "pm-comment-glyph", kind) : runs;
    }

    private Decor() {}
}
