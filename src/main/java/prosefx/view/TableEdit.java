package prosefx.view;

import com.atlassian.prosemirror.model.Fragment;
import java.util.*;
import javafx.scene.text.Text;
import prosefx.core.pm.Pm;

import static prosefx.core.schema.EditorSchema.*;

/**
 * Adding and deleting rows and columns of a pipe table. Each change is a few small insertions and deletions in the
 * table's own lines (an empty cell, a pipe, a `---`), applied in one transaction, so the formatting in the other cells
 * survives, undo takes it back in one step, and only the lines touched change in the file.
 */
final class TableEdit {

    enum Op {
        ROW_ABOVE("Insert row above"), ROW_BELOW("Insert row below"), DELETE_ROW("Delete row"),
        COL_LEFT("Insert column left"), COL_RIGHT("Insert column right"), DELETE_COL("Delete column"),
        DELETE_TABLE("Delete table");
        final String label;
        Op(String label) { this.label = label; }
    }

    /** One line of the table, as offsets in its paragraph: where it starts and ends, its opening pipe (or -1), the pipes that close its cells, and whether the last of them ends the line. */
    record Line(int start, int end, int lead, List<Integer> closers, boolean trailing) {
        int cells() { return closers.size() + (trailing ? 0 : 1); }
        int spanStart(int k) { return k == 0 ? (lead >= 0 ? lead + 1 : start) : closers.get(k - 1) + 1; }
        int spanEnd(int k) { return k < closers.size() ? closers.get(k) : end; }
    }

    static List<Line> lines(BlockView tb) {
        var drawn = Motion.drawn(tb);
        var pipes = new TreeMap<Integer, Table.Pipe>();
        var offset = 0;
        for (var n : tb.flow.getChildren()) {
            if (offset >= drawn.length()) break;
            if (n instanceof Table.Pipe p) { pipes.put(offset, p); offset++; }
            else if (n instanceof Text t) offset += t.getText().length();
        }
        var out = new ArrayList<Line>();
        var start = 0;
        while (start <= drawn.length()) {
            var end = drawn.indexOf('\n', start);
            if (end < 0) end = drawn.length();
            var lead = -1;
            var closers = new ArrayList<Integer>();
            for (var e : pipes.subMap(start, end).entrySet()) {
                if (e.getValue().lead) lead = e.getKey(); else closers.add(e.getKey());
            }
            var trailing = !closers.isEmpty() && drawn.substring(closers.getLast() + 1, end).isBlank();
            out.add(new Line(start, end, lead, closers, trailing));
            start = end + 1;
        }
        return out;
    }

    /** The row and column of offset: {line, column}. */
    static int[] cellAt(List<Line> lines, int offset) {
        var l = 0;
        while (l < lines.size() - 1 && offset > lines.get(l).end()) l++;
        var line = lines.get(l);
        var col = (int) line.closers().stream().filter(c -> c < offset).count();
        return new int[] {l, Math.min(col, line.cells() - 1)};
    }

    /** Whether op can be applied with the caret at offset (a header has no row above it to delete, a one-column table no column to lose). */
    static boolean allowed(BlockView tb, int offset, Op op) {
        var lines = lines(tb);
        var at = cellAt(lines, offset);
        return switch (op) {
            case DELETE_ROW -> at[0] >= 2;
            case DELETE_COL -> lines.getFirst().cells() > 1;
            default -> true;
        };
    }

    /** A change: replace [from, to) of the paragraph with text (empty: delete). */
    record Edit(int from, int to, String text) {}

    /** Applies op with the caret at offset in the table tb of view; false when it does not apply there. */
    static boolean apply(DocView view, BlockView tb, int offset, Op op) {
        if (!allowed(tb, offset, op)) return false;
        var lines = lines(tb);
        var at = cellAt(lines, offset);
        var tr = view.model.state().getTr();
        switch (op) {
            case DELETE_TABLE -> tr.delete(tb.start - 1, tb.start + Positions.size(tb) + 1);
            case ROW_ABOVE, ROW_BELOW -> {
                // below the separator when the caret is on the header or the separator
                var after = op == Op.ROW_ABOVE ? Math.max(1, at[0] - 1) : Math.max(1, at[0]);
                var row = Table.newRow(tb, 0);
                var pos = tb.start + lines.get(after).end();
                tr.insert(pos, Fragment.Companion.fromArray(List.of(HARD_BREAK.create(Map.of("raw", ""), (Fragment) null, null), SCHEMA.text(row.text(), null))));
                tr.setSelection(Pm.cursor(tr.getDoc(), pos + 1 + row.caret()));
            }
            case DELETE_ROW -> {
                var line = lines.get(at[0]);
                tr.delete(tb.start + line.start() - 1, tb.start + line.end());
            }
            case COL_LEFT, COL_RIGHT -> apply(tr, tb.start, insertColumn(lines, op == Op.COL_LEFT ? at[1] : at[1] + 1));
            case DELETE_COL -> apply(tr, tb.start, deleteColumn(lines, at[1]));
        }
        view.model.dispatch(tr.scrollIntoView());
        return true;
    }

    static void apply(com.atlassian.prosemirror.state.Transaction tr, int base, List<Edit> edits) {
        // from the end backwards, so that the earlier offsets still hold
        edits.stream().sorted(Comparator.comparingInt(Edit::from).reversed()).forEach(e -> {
            if (e.text().isEmpty()) tr.delete(base + e.from(), base + e.to());
            else tr.replaceWith(base + e.from(), base + e.to(), SCHEMA.text(e.text(), null));
        });
    }

    /** A new empty column at index i (0 is before the first) on every line; the separator row gets `---`. A line with no closing pipe gets one after a new last cell: an empty cell at the end of a line is not a cell, and two trailing spaces would be a line break. */
    static List<Edit> insertColumn(List<Line> lines, int i) {
        var out = new ArrayList<Edit>();
        for (var l = 0; l < lines.size(); l++) {
            var line = lines.get(l);
            var sep = l == 1;
            if (i < line.cells()) {
                var at = line.spanStart(i);
                var noLeadFirst = i == 0 && line.lead() < 0;
                out.add(new Edit(at, at, sep ? "---|" : noLeadFirst ? "  | " : "  |"));
            } else out.add(new Edit(line.end(), line.end(), line.trailing() ? (sep ? "---|" : "  |") : (sep ? "|---|" : " |  |")));
        }
        return out;
    }

    /** Column c taken out of every line, with the pipe that separated it. */
    static List<Edit> deleteColumn(List<Line> lines, int c) {
        var out = new ArrayList<Edit>();
        for (var line : lines) {
            if (line.cells() <= 1 || c >= line.cells()) continue;
            if (c < line.closers().size()) out.add(new Edit(line.spanStart(c), line.closers().get(c) + 1, ""));
            else out.add(new Edit(line.closers().get(c - 1), line.end(), ""));
        }
        return out;
    }

    private TableEdit() {}
}
