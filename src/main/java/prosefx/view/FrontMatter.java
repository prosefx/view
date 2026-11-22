package prosefx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import java.util.regex.Pattern;
import javafx.scene.text.Text;

import static prosefx.core.pm.Pm.attr;
import static prosefx.core.schema.EditorSchema.*;

/**
 * YAML front matter as the lossless reader keeps it: the first block of the document, one verbatim paragraph whose lines are
 * text separated by hard breaks, `---` first and `---` (or `...`) last. The text stays exactly as written, so the file comes
 * back byte for byte and a line is edited like any text. The view only styles it as a property block: each `key:` quiet and
 * bold, the value in the text colour, the two `---` lines small. Nothing is added or taken out, so positions do not move.
 */
final class FrontMatter {

    static final Pattern KEY = Pattern.compile("^([^\\s:#\\-][^:]*:)(.*)$", Pattern.DOTALL);

    /** A verbatim paragraph that opens with a `---` line. The reader makes one only at the very start of a file; the caller checks it is the document's own first block. */
    static boolean is(Node n) {
        if (n.getType() != PARAGRAPH || !Boolean.TRUE.equals(attr(n, "verbatim"))) return false;
        var lines = Code.lines(n);
        return lines.size() >= 2 && lines.getFirst().strip().equals("---") && delimiter(lines.getLast());
    }

    static boolean delimiter(String line) { return line.strip().equals("---") || line.strip().equals("..."); }

    /** The runs of the block: every character of the document in one Text or a few, in order, with the style classes of its part. */
    static List<Text> runs(Node block) {
        var out = new ArrayList<Text>();
        var last = Code.lines(block).size() - 1;
        var line = 0;
        var atStart = true;
        for (var kid : block.getContent().getContent()) {
            if (!kid.isText()) {
                out.add(DocRenderer.text(kid));
                if (kid.getType() == HARD_BREAK) { line++; atStart = true; } else atStart = false;
                continue;
            }
            var marks = kid.getMarks().stream().map(m -> m.getType().getName()).toList();
            var s = kid.getText();
            if (atStart && (line == 0 || line == last) && delimiter(s)) out.add(styled(s, marks, "pm-front-delim"));
            else if (atStart && s.stripLeading().startsWith("#")) out.add(styled(s, marks, "pm-front-comment"));
            else {
                var key = atStart ? KEY.matcher(s) : null;
                if (key != null && key.matches()) {
                    out.add(styled(key.group(1), marks, "pm-front-key"));
                    if (!key.group(2).isEmpty()) out.add(styled(key.group(2), marks, "pm-front-value"));
                } else out.add(styled(s, marks, "pm-front-value"));
            }
            atStart = false;
        }
        if (out.isEmpty()) out.add(DocRenderer.text("", List.of()));
        return out;
    }

    private static Text styled(String s, List<String> marks, String cls) {
        var t = DocRenderer.text(s, marks);
        t.getStyleClass().add(cls);
        return t;
    }

    private FrontMatter() {}
}
