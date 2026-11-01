package prosemirrorfx.view;

import javafx.scene.control.*;

/**
 * The table menu: insert a row above or below, a column left or right, delete the row, the column or the table. It
 * opens from the toolbar's table button when the caret is in a table, and on a right-click in a table; arrow keys and
 * Enter work in it like any menu.
 */
final class TableMenu {

    static ContextMenu showing;

    /** The table the caret is in, or null. */
    static BlockView tableAtCaret(DocView view) {
        var head = view.model.state().getSelection().getHead();
        var tb = view.positions.at(head);
        return tb.flow instanceof Table.TableFlow ? tb : null;
    }

    static ContextMenu menu(DocView view, BlockView tb, int offset) {
        var menu = new ContextMenu();
        TableEdit.Op last = null;
        for (var op : TableEdit.Op.values()) {
            // a line between the row items, the column items and deleting the table
            if (last != null && (op == TableEdit.Op.COL_LEFT || op == TableEdit.Op.DELETE_TABLE)) menu.getItems().add(new SeparatorMenuItem());
            var item = new MenuItem(op.label);
            item.setDisable(!TableEdit.allowed(tb, offset, op));
            item.setOnAction(e -> { TableEdit.apply(view, tb, offset, op); view.requestFocus(); });
            menu.getItems().add(item);
            last = op;
        }
        return menu;
    }

    /** Opens the menu for the table at the caret, at a screen point. */
    static void open(DocView view, double screenX, double screenY) {
        var tb = tableAtCaret(view);
        if (tb == null) return;
        if (showing != null) showing.hide();
        showing = menu(view, tb, view.model.state().getSelection().getHead() - tb.start);
        showing.show(view, screenX, screenY);
    }

    private TableMenu() {}
}
