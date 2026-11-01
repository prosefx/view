package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import java.util.regex.Pattern;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import static prosemirrorfx.core.pm.Pm.attr;
import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * A pipe table as the lossless reader keeps it: an ordinary paragraph whose lines are the rows, the second line the
 * `|---|---|` separator. The text stays exactly as written, so the file comes back byte for byte and editing a cell
 * rewrites only its line. The view draws it as a grid: each separating `|` is drawn as a Pipe, an empty region that is
 * still one character (so positions do not move) and whose width pads its cell out to the column's width; the header
 * row is bold, the separator row shrinks to a hairline, and TableGrid draws the lines behind.
 */
final class Table {

    static final Pattern SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$");

    /** A paragraph of at least two lines whose first line has a pipe and whose second is a separator row with one. */
    static boolean is(Node n) {
        if (n.getType() != PARAGRAPH || Boolean.TRUE.equals(attr(n, "verbatim"))) return false;
        var lines = Code.lines(n);
        return lines.size() >= 2 && lines.get(0).contains("|") && lines.get(1).contains("|") && SEPARATOR.matcher(lines.get(1)).matches();
    }

    /** One `|` of the table, drawn as space; which line and column it closes, or lead when it opens its line. */
    static final class Pipe extends Region {
        final int line, col;
        final boolean lead;
        /** Where in this pipe its column ends (fit sets it): the grid line goes at its left plus edge. */
        double edge;
        Pipe(int line, int col, boolean lead) {
            this.line = line;
            this.col = col;
            this.lead = lead;
            getStyleClass().add("pm-table-pipe");
            // unsnapped, like the text around it: snapping each pipe separately put a column end a pixel off on some rows
            setSnapToPixel(false);
            setMinHeight(0);
            setPrefHeight(0);
        }
    }

    /** The runs of the table paragraph with every separating pipe taken out of its Text and put in as a Pipe. */
    static List<javafx.scene.Node> runs(List<Text> texts) {
        var out = new ArrayList<javafx.scene.Node>();
        var line = 0;
        var col = 0;
        var blankSoFar = true;
        for (var t : texts) {
            var s = t.getText();
            if (s.equals("\n")) {
                // the break ending the separator row is as small as the row, or the row keeps a full line's height
                if (line == 1) t.getStyleClass().add("pm-table-sep");
                out.add(t);
                line++;
                col = 0;
                blankSoFar = true;
                continue;
            }
            var classes = new ArrayList<>(t.getStyleClass());
            if (line == 0) classes.add("pm-table-head");
            if (line == 1) classes.add("pm-table-sep");
            var code = classes.contains("code");
            var from = 0;
            for (var i = 0; i < s.length(); i++) {
                var c = s.charAt(i);
                if (c != '|' || code || (i > 0 && s.charAt(i - 1) == '\\')) {
                    if (!Character.isWhitespace(c)) blankSoFar = false;
                    continue;
                }
                if (i > from) out.add(piece(s.substring(from, i), classes));
                var lead = blankSoFar && col == 0;
                out.add(new Pipe(line, lead ? -1 : col, lead));
                if (!lead) col++;
                blankSoFar = false;
                from = i + 1;
            }
            if (from < s.length() || s.isEmpty()) out.add(from == 0 ? restyle(t, classes) : piece(s.substring(from), classes));
        }
        return out;
    }

    static Text piece(String s, List<String> classes) {
        var t = new Text(s);
        t.getStyleClass().setAll(classes);
        return t;
    }

    static Text restyle(Text t, List<String> classes) {
        t.getStyleClass().setAll(classes);
        return t;
    }

    /** The width of the opening pipe, and the room after a column's widest cell. */
    static final double LEAD = 6, PAD = 14, RIGHT_GAP = 4;

    /**
     * Sets each pipe's width so that every column ends at the same x on every line: a column is as wide as its widest
     * cell (the separator row does not count) plus PAD. Called before the flow measures or lays itself out.
     */
    static void fit(List<javafx.scene.Node> children) { fit(children, List.of()); }

    /**
     * As above, with each column aligned as its separator cell says (l, c or r, see aligns): a cell's spare room goes
     * after it (left), before it (right) or half each side (centre). The room before a cell is part of the pipe before
     * it, so a pipe's width is the room after its own cell plus the room before the next one; its edge says where in it
     * the column ends, for the grid lines.
     */
    static void fit(List<javafx.scene.Node> children, List<Character> aligns) {
        // each line's cells in order: the pipe that closes each cell, and the cell's text width
        var lines = new ArrayList<List<Pipe>>();
        var seg = new HashMap<Pipe, Double>();
        var lead = new HashMap<Integer, Pipe>();
        var widest = new ArrayList<Double>();
        var w = 0.0;
        lines.add(new ArrayList<>());
        for (var n : children) {
            if (n instanceof Text t) {
                if (t.getText().equals("\n")) { w = 0; lines.add(new ArrayList<>()); }
                else w += t.getLayoutBounds().getWidth();
            } else if (n instanceof Pipe p) {
                if (p.lead) lead.put(p.line, p);
                else {
                    seg.put(p, w);
                    lines.getLast().add(p);
                    if (p.line != 1) {
                        while (widest.size() <= p.col) widest.add(0.0);
                        widest.set(p.col, Math.max(widest.get(p.col), w));
                    }
                }
                w = 0;
            }
        }
        // a row without an opening pipe, in a table where other rows have one, starts its first column LEAD earlier
        var mixed = !lead.isEmpty();
        for (var l = 0; l < lines.size(); l++) {
            var cells = lines.get(l);
            double[] before = new double[cells.size()], after = new double[cells.size()];
            for (var k = 0; k < cells.size(); k++) {
                var p = cells.get(k);
                var spare = (p.col < widest.size() ? widest.get(p.col) : seg.get(p)) - seg.get(p);
                var a = l == 1 || p.col >= aligns.size() ? 'l' : aligns.get(p.col);
                // the PAD of room after a cell moves with it: before a right-aligned cell, half each side of a centred one
                before[k] = a == 'r' ? spare + PAD - RIGHT_GAP : a == 'c' ? (spare + PAD) / 2 : 0;
                after[k] = spare + PAD - before[k];
            }
            var opening = lead.get(l);
            if (opening != null) size(opening, LEAD + (cells.isEmpty() ? 0 : before[0]), 0);
            for (var k = 0; k < cells.size(); k++) {
                var p = cells.get(k);
                var extra = mixed && p.col == 0 && opening == null ? LEAD : 0;
                // without an opening pipe the first cell has nothing before it to hold its room: it stays left
                if (k == 0 && opening == null) { after[0] += before[0]; before[0] = 0; }
                size(p, after[k] + extra + (k + 1 < cells.size() ? before[k + 1] : 0), after[k] + extra);
            }
        }
    }

    static void size(Pipe p, double width, double edge) {
        width = Math.max(1, width);
        p.edge = edge;
        if (p.getPrefWidth() != width) { p.setMinWidth(width); p.setPrefWidth(width); p.setMaxWidth(width); }
    }

    /** Each column's alignment from the separator row: c for :---:, r for ---:, l otherwise. */
    static List<Character> aligns(Node table) {
        var sep = Code.lines(table).get(1).strip();
        if (sep.startsWith("|")) sep = sep.substring(1);
        if (sep.endsWith("|")) sep = sep.substring(0, sep.length() - 1);
        var out = new ArrayList<Character>();
        for (var cell : sep.split("\\|", -1)) {
            var c = cell.strip();
            out.add(c.startsWith(":") && c.endsWith(":") && c.length() > 1 ? 'c' : c.endsWith(":") ? 'r' : 'l');
        }
        return out;
    }

    /**
     * Where each cell's text starts, as offsets in the table's paragraph, row by row (the separator row has none): after
     * each pipe that is not the last thing on its line, past one space, and at the start of a row with no opening pipe.
     */
    static List<Integer> cellStarts(BlockView tb) {
        var drawn = Motion.drawn(tb);
        var pipes = new TreeSet<Integer>();
        var offset = 0;
        for (var n : tb.flow.getChildren()) {
            if (offset >= drawn.length()) break;
            if (n instanceof Pipe) { pipes.add(offset); offset++; }
            else if (n instanceof Text t) offset += t.getText().length();
        }
        var out = new ArrayList<Integer>();
        var lineStart = 0;
        var line = 0;
        while (lineStart <= drawn.length()) {
            var end = drawn.indexOf('\n', lineStart);
            if (end < 0) end = drawn.length();
            if (line != 1) {
                var first = lineStart;
                while (first < end && drawn.charAt(first) == ' ') first++;
                if (!pipes.contains(first)) out.add(lineStart);
                for (var p : pipes.subSet(lineStart, end)) {
                    if (drawn.substring(p + 1, end).isBlank()) continue;
                    out.add(p + 1 < end && drawn.charAt(p + 1) == ' ' ? p + 2 : p + 1);
                }
            }
            lineStart = end + 1;
            line++;
        }
        return out;
    }

    /** The start of the next (dir 1) or previous (dir -1) cell from offset, or -1 at the table's end or start. */
    static int nextCell(BlockView tb, int offset, int dir) {
        var starts = cellStarts(tb);
        if (dir > 0) { for (var s : starts) if (s > offset) return s; return -1; }
        // back: the start of the cell before the one the caret is in
        var current = -1;
        for (var k = starts.size() - 1; k >= 0; k--) if (starts.get(k) <= offset) { current = k; break; }
        return current > 0 ? starts.get(current - 1) : -1;
    }

    /** True when offset is on the separator row (the second line). */
    static boolean onSeparator(BlockView tb, int offset) {
        var lines = Code.lines(tb.node);
        var start = lines.getFirst().length() + 1;
        return lines.size() > 1 && offset >= start && offset <= start + lines.get(1).length();
    }

    /** An empty row to insert: at offset (the end of a line, before which a line break goes), its text, and where in it the caret goes. */
    record Row(int at, String text, int caret) {}

    /**
     * Enter in a table: an empty row below the caret's row (below the separator when the caret is on the header or the
     * separator), with as many cells as the header and written the way the header is: with an opening pipe or without.
     */
    static Row newRow(BlockView tb, int offset) {
        var lines = Code.lines(tb.node);
        var line = 0;
        var end = lines.getFirst().length();
        while (line < lines.size() - 1 && (offset > end || line < 1)) { line++; end += 1 + lines.get(line).length(); }
        var header = lines.getFirst().strip();
        var cells = (int) cellStarts(tb).stream().filter(s -> s <= lines.getFirst().length()).count();
        var lead = header.startsWith("|");
        var text = lead ? "|" + "  |".repeat(cells) : String.join(" | ", Collections.nCopies(cells, " ")).stripTrailing();
        return new Row(end, text, lead ? 2 : 0);
    }

    /** The flow of a table: pipe widths are fitted before every measurement and layout. */
    static final class TableFlow extends Flow {
        List<Character> aligns = List.of();
        TableFlow() { setSnapToPixel(false); }
        @Override protected double computePrefHeight(double width) { fit(getChildren(), aligns); return super.computePrefHeight(width); }
        @Override protected double computePrefWidth(double height) { fit(getChildren(), aligns); return super.computePrefWidth(height); }
        @Override protected void layoutChildren() { fit(getChildren(), aligns); super.layoutChildren(); }
    }

    private Table() {}
}
