package prosemirrorfx.view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import prosemirrorfx.core.commands.Commands;

/**
 * The table button's picker, as Word and Google Docs have it: a grid of cells under the button; pointing at a cell
 * chooses that many columns and body rows, a click inserts the table. The keyboard works too: arrows grow or shrink the
 * choice, Enter inserts, Escape closes.
 */
final class TablePicker {

    static final int MAX_ROWS = 8, MAX_COLS = 8;

    static void open(DocView view, Node from) {
        var popup = new Popup();
        popup.setAutoHide(true);
        var grid = new GridPane(3, 3);
        var label = new Label();
        label.getStyleClass().add("pm-table-picker-label");
        var cells = new Region[MAX_ROWS][MAX_COLS];
        int[] pick = {2, 2};
        Runnable show = () -> {
            label.setText(pick[0] + " × " + pick[1] + "  (rows below the header × columns)");
            for (var r = 0; r < MAX_ROWS; r++)
                for (var c = 0; c < MAX_COLS; c++)
                    cells[r][c].pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("chosen"), r < pick[0] && c < pick[1]);
        };
        Runnable insert = () -> {
            popup.hide();
            view.model.run(Commands.insertTable(pick[0], pick[1]));
            view.requestFocus();
        };
        for (var r = 0; r < MAX_ROWS; r++)
            for (var c = 0; c < MAX_COLS; c++) {
                var cell = new Region();
                cell.getStyleClass().add("pm-table-picker-cell");
                cell.setPrefSize(16, 16);
                int rr = r + 1, cc = c + 1;
                cell.setOnMouseEntered(e -> { pick[0] = rr; pick[1] = cc; show.run(); });
                cell.setOnMouseClicked(e -> insert.run());
                cells[r][c] = cell;
                grid.add(cell, c, r);
            }
        var box = new VBox(6, grid, label);
        box.getStyleClass().add("pm-table-picker");
        box.setPadding(new Insets(8));
        box.getStylesheets().add(TablePicker.class.getResource("editor.css").toExternalForm());
        box.setStyle(view.theme().style());
        box.setFocusTraversable(true);
        box.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case UP -> pick[0] = Math.max(1, pick[0] - 1);
                case DOWN -> pick[0] = Math.min(MAX_ROWS, pick[0] + 1);
                case LEFT -> pick[1] = Math.max(1, pick[1] - 1);
                case RIGHT -> pick[1] = Math.min(MAX_COLS, pick[1] + 1);
                case ENTER -> { insert.run(); e.consume(); return; }
                case ESCAPE -> { popup.hide(); view.requestFocus(); e.consume(); return; }
                default -> { return; }
            }
            show.run();
            e.consume();
        });
        show.run();
        popup.getContent().add(box);
        var at = from.localToScreen(from.getLayoutBounds());
        popup.show(from, at.getMinX(), at.getMaxY() + 2);
        box.requestFocus();
    }

    private TablePicker() {}
}
