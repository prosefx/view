package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.BoundingBox;
import javafx.scene.layout.*;

import static prosemirrorfx.core.schema.EditorSchema.HIGHLIGHT;

/** A marker-pen band behind each ^^highlight^^ span, drawn after layout like the code boxes and styled by CSS (.pm-highlight-box). */
final class SpanBoxes {

    final Pane back;
    final List<Region> pool = new ArrayList<>();

    SpanBoxes(Pane back) { this.back = back; }

    void paint(Positions positions) {
        var slot = 0;
        for (var tb : positions.textblocks) {
            if (!MarkLayer.shown(tb.flow)) continue;
            var offset = 0;
            for (var c : tb.node.getContent().getContent()) {
                var size = c.getNodeSize();
                if (c.isText() && HIGHLIGHT.isInSet(c.getMarks()) != null)
                    for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(offset, offset + size, true)) {
                        // in a table that scrolls sideways, only the part that can be seen
                        var o = Table.within(back.sceneToLocal(tb.flow.localToScene(new BoundingBox(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight()))), Table.visible(tb, back));
                        if (o == null) continue;
                        var box = box(slot++);
                        box.setVisible(true);
                        box.resizeRelocate(o.getMinX() - 1, o.getMinY() + 1, o.getWidth() + 2, o.getHeight() - 2);
                    }
                offset += size;
            }
        }
        for (var k = slot; k < pool.size(); k++) pool.get(k).setVisible(false);
    }

    Region box(int slot) {
        while (pool.size() <= slot) {
            var r = new Region();
            r.getStyleClass().add("pm-highlight-box");
            r.setManaged(false);
            pool.add(r);
            back.getChildren().add(r);
        }
        return pool.get(slot);
    }
}
