package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;

import static prosemirrorfx.core.schema.EditorSchema.HARD_BREAK;

/**
 * The lines of each pipe table, drawn behind the text after layout like the code boxes: the outer border, a line
 * between rows (a stronger one under the header) and a line at the end of each column, where the first row's pipes
 * end. Styled by CSS (.pm-table-grid), so a Theme restyles it.
 */
final class TableGrid {

    final Pane back;
    final List<Path> pool = new ArrayList<>();

    TableGrid(Pane back) { this.back = back; }

    void paint(Positions positions) {
        var slot = 0;
        for (var tb : positions.textblocks) {
            if (!(tb.flow instanceof Table.TableFlow)) continue;
            var path = path(slot++);
            path.getElements().setAll(lines(tb));
            path.setVisible(true);
        }
        for (var k = slot; k < pool.size(); k++) pool.get(k).setVisible(false);
    }

    List<PathElement> lines(BlockView tb) {
        var flow = tb.flow;
        var info = flow.getLayoutInfo();
        var text = info.getTextLines(false);
        if (text.isEmpty()) return List.of();
        // the top of each row: where the caret stands at its first character; the separator row (row 1) is skipped
        var tops = new ArrayList<Double>();
        var offset = 0;
        tops.add(info.caretInfoAt(0, true).getSegmentAt(0).getMinY());
        var row = 0;
        for (var c : tb.node.getContent().getContent()) {
            offset += c.getNodeSize();
            if (c.getType() == HARD_BREAK && ++row != 1) tops.add(info.caretInfoAt(offset, true).getSegmentAt(0).getMinY());
        }
        var bottom = text.getLast().bounds().getMaxY();
        // the column ends, from the first row's pipes; the left edge is the first row's start
        var xs = new ArrayList<Double>();
        var left = info.caretInfoAt(0, true).getSegmentAt(0).getMinX();
        for (var n : flow.getChildren()) {
            if (!(n instanceof Table.Pipe p) || p.line != 0) continue;
            if (p.lead) left = p.getBoundsInParent().getMinX();
            else xs.add(p.getBoundsInParent().getMaxX());
        }
        var hasLead = flow.getChildren().stream().anyMatch(n -> n instanceof Table.Pipe p && p.line == 0 && p.lead);
        if (!hasLead) left -= Table.LEAD;
        // without a closing pipe the last column ends after the longest row's text
        var right = xs.isEmpty() ? left : xs.getLast();
        if (!Code.lines(tb.node).getFirst().strip().endsWith("|"))
            for (var line : text) right = Math.max(right, line.bounds().getMaxX() + Table.PAD);
        var out = new ArrayList<PathElement>();
        for (var y : tops) line(out, flow, left, y, right, y);
        line(out, flow, left, bottom, right, bottom);
        line(out, flow, left, tops.getFirst(), left, bottom);
        for (var x : xs) line(out, flow, x, tops.getFirst(), x, bottom);
        if (xs.isEmpty() || right > xs.getLast() + 0.5) line(out, flow, right, tops.getFirst(), right, bottom);
        return out;
    }

    void line(List<PathElement> out, javafx.scene.Node flow, double x1, double y1, double x2, double y2) {
        var a = back.sceneToLocal(flow.localToScene(x1, y1));
        var b = back.sceneToLocal(flow.localToScene(x2, y2));
        // half-pixel positions give crisp one-pixel lines
        out.add(new MoveTo(Math.round(a.getX()) + 0.5, Math.round(a.getY()) + 0.5));
        out.add(new LineTo(Math.round(b.getX()) + 0.5, Math.round(b.getY()) + 0.5));
    }

    Path path(int slot) {
        while (pool.size() <= slot) {
            var p = new Path();
            p.getStyleClass().add("pm-table-grid");
            p.setManaged(false);
            pool.add(p);
            back.getChildren().add(p);
        }
        return pool.get(slot);
    }
}
