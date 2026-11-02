package prosefx.view;

/**
 * A task box (`[ ]` / `[x]` at the start of a list item, drawn as a box by Decor) switched by a click on the box or, from
 * the keyboard, by Ctrl+Enter anywhere in that item's first paragraph. The three characters are replaced, so the file
 * changes by exactly one character and undo puts the box back.
 */
final class Tasks {

    static boolean is(BlockView tb) { return tb.isTextblock() && tb.flow.getStyleClass().contains("pm-task"); }

    /** True when the point is on the box of a task. */
    static boolean onBox(DocView view, double sceneX, double sceneY) {
        if (view.positions == null || view.positions.textblocks.isEmpty()) return false;
        var tb = view.positions.at(view.positions.posAt(sceneX, sceneY));
        if (!is(tb) || !MarkLayer.shown(tb.flow)) return false;
        var local = tb.flow.sceneToLocal(sceneX, sceneY);
        for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(0, 3, true)) if (r.contains(local.getX(), local.getY())) return true;
        return false;
    }

    static void toggle(DocView view, BlockView tb) {
        var done = tb.node.getTextContent().startsWith("[ ]");
        view.model.dispatch(view.model.state().getTr().insertText(done ? "[x]" : "[ ]", tb.start, tb.start + 3));
    }

    private Tasks() {}
}
