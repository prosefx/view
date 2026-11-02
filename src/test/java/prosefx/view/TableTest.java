package prosefx.view;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosefx.core.editor.EditorModel;
import prosefx.markdown.Lossless;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.view.ViewKeysTest.fx;
import static prosefx.view.ViewKeysTest.pulse;

/** Pipe tables drawn as a grid while the text stays as written. Headless, like MarkupTest. */
class TableTest {

    static Stage stage;
    DocView view;

    @BeforeAll static void boot() throws Exception {
        FxBoot.start();
        fx(() -> stage = new Stage());
    }

    void open(String md) {
        fx(() -> {
            view = new DocView(new EditorModel(Lossless.read(md)));
            stage.setScene(new Scene(view, 800, 600));
            stage.show();
        });
        pulse();
        pulse();
    }

    static final String TABLE = "before\n\n| Name | Count |\n|---|---:|\n| apples | 3 |\n| a much longer name | 12 |\n\nafter\n";

    BlockView block(int i) { return fx(() -> view.positions.textblocks.get(i)); }

    List<Table.Pipe> pipes(BlockView tb) { return fx(() -> tb.flow.getChildren().stream().filter(n -> n instanceof Table.Pipe).map(n -> (Table.Pipe) n).toList()); }

    @Test void aPipeTableIsDrawnAsATableAndAPlainParagraphIsNot() {
        open(TABLE);
        assertInstanceOf(Table.TableFlow.class, block(1).flow);
        assertFalse(block(0).flow instanceof Table.TableFlow);
        assertFalse(block(2).flow instanceof Table.TableFlow);
        open("a | b\nno separator here\n");
        assertFalse(block(0).flow instanceof Table.TableFlow);
    }

    @Test void everyCharacterIsStillDrawnOncePipesIncluded() {
        open(TABLE);
        var tb = block(1);
        assertEquals("| Name | Count |\n|---|---:|\n| apples | 3 |\n| a much longer name | 12 |", fx(() -> Motion.drawn(tb)));
        // 4 rows of 3 pipes each, the first of each row opening it
        var pipes = pipes(tb);
        assertEquals(12, pipes.size());
        assertEquals(4, pipes.stream().filter(p -> p.lead).count());
    }

    @Test void theColumnsLineUpOnEveryRow() {
        open(TABLE);
        var tb = block(1);
        var ends = new TreeMap<Integer, List<Double>>();
        fx(() -> { for (var p : pipes(tb)) if (!p.lead) ends.computeIfAbsent(p.col, k -> new ArrayList<>()).add(p.getBoundsInParent().getMinX() + p.edge); return null; });
        assertEquals(2, ends.size());
        for (var xs : ends.values()) {
            assertEquals(4, xs.size());
            for (var x : xs) assertEquals(xs.getFirst(), x, 0.75, "column end on every row: " + xs);
        }
        // the first column is as wide as its longest cell, so the short name's pipe is wider than the long one's
        var first = pipes(tb).stream().filter(p -> !p.lead && p.col == 0).toList();
        assertTrue(fx(() -> first.get(2).getWidth() > first.get(3).getWidth()));
    }

    @Test void aPositionAfterAPipeIsRightOfItSoTheCaretCountsThePipeAsOneCharacter() {
        open(TABLE);
        var tb = block(1);
        var pipe = pipes(tb).get(1);   // closes "Name" on the header row
        var at = "| Name |".length();  // just after it
        var caret = fx(() -> Positions.caretRect(tb, at).getMinX());
        assertEquals(fx(() -> pipe.getBoundsInParent().getMaxX()), caret, 0.75);
    }

    @Test void typingInACellRewritesOnlyThatLineAndTheTableStaysATable() {
        open(TABLE);
        fx(() -> view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), Fixtures.posAfter(view.model().state().getDoc(), "apples"))));
        fx(() -> { view.model().type("!"); return null; });
        pulse();
        assertEquals(TABLE.replace("| apples | 3 |", "| apples! | 3 |"), Lossless.write(fx(() -> view.model().state().getDoc())));
        assertInstanceOf(Table.TableFlow.class, block(1).flow);
    }

    @Test void theFileComesBackByteForByte() {
        var md = "| a | b |\n| :-- | --: |\n| **x** | `y|z` |\n\nno | leading | pipe\n---|---\none | two\n";
        open(md);
        assertEquals(md, Lossless.write(fx(() -> view.model().state().getDoc())));
        // a pipe inside inline code is text, not a column
        assertEquals(3, pipes(block(0)).stream().filter(p -> p.line == 2).count());
        assertFalse(pipes(block(1)).stream().anyMatch(p -> p.lead));
    }

    @Test void tabGoesToTheNextCellAndShiftTabBackSkippingTheSeparatorRow() {
        open("| Name | Count |\n|---|---:|\n| apples | 3 |\n");
        var tb = block(0);
        var text = "| Name | Count |\n|---|---:|\n| apples | 3 |";
        assertEquals(List.of(2, text.indexOf("Count"), text.indexOf("apples"), text.indexOf("3 |")), fx(() -> Table.cellStarts(tb)));
        assertEquals(text.indexOf("Count"), fx(() -> Table.nextCell(tb, 3, 1)));
        assertEquals(text.indexOf("apples"), fx(() -> Table.nextCell(tb, text.indexOf("Count") + 2, 1)));
        assertEquals(-1, fx(() -> Table.nextCell(tb, text.indexOf("3 |"), 1)));
        assertEquals(text.indexOf("Count"), fx(() -> Table.nextCell(tb, text.indexOf("apples") + 1, -1)));
        open("no | lead\n---|---\none | two\n");
        var t2 = block(0);
        assertEquals(List.of(0, "no | ".length(), "no | lead\n---|---\n".length(), "no | lead\n---|---\none | ".length()), fx(() -> Table.cellStarts(t2)));
    }

    void key(javafx.scene.input.KeyCode code) {
        fx(() -> {
            view.requestFocus();
            javafx.event.Event.fireEvent(view, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
            return null;
        });
        pulse();
    }

    @Test void enterAddsAnEmptyRowBelowWithTheCaretInItsFirstCell() {
        open("| a | b |\n|---|---|\n| x | y |\n");
        fx(() -> view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), Fixtures.posAfter(view.model().state().getDoc(), "| x"))));
        key(javafx.scene.input.KeyCode.ENTER);
        fx(() -> { view.model().type("z"); return null; });
        assertEquals("| a | b |\n|---|---|\n| x | y |\n| z |  |\n", Lossless.write(fx(() -> view.model().state().getDoc())));
        assertInstanceOf(Table.TableFlow.class, block(0).flow);
        // on the header the row goes under the separator, not between them
        open("a | b\n---|---\nx | y\n");
        fx(() -> view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), 2)));
        key(javafx.scene.input.KeyCode.ENTER);
        assertEquals("a | b\n---|---\n  |\nx | y\n", Lossless.write(fx(() -> view.model().state().getDoc())));
    }

    void caretAt(int pos) { fx(() -> { view.requestFocus(); view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), pos)); return null; }); pulse(); }

    int head() { return fx(() -> view.model().state().getSelection().getHead()); }

    @Test void upFromTheFirstRowReachesTheHeaderAndDownComesBack() {
        open("| Name | Count |\n|---|---:|\n| apples | 3 |\n| pears | 12 |\n");
        var text = "| Name | Count |\n|---|---:|\n| apples | 3 |";
        // the caret's own x (headless, the drawn caret does not follow a selection set without focus)
        var x = fx(() -> { var tb = block(0); return tb.flow.localToScene(Positions.caretRect(tb, text.indexOf("apples") + 2).getMinX(), 0).getX(); });
        var up = fx(() -> Motion.vertical(view.positions, 1 + text.indexOf("apples") + 2, -1, x)) - 1;
        assertTrue(up < "| Name | Count |".length(), "on the header row, was " + up);
        var off = fx(() -> Motion.vertical(view.positions, 1 + up, 1, x)) - 1;
        assertTrue(off > text.indexOf("| apples") && off < text.length(), "back on the apples row, was " + off);
    }

    @Test void tabInTheLastCellAddsARowAndEnterAtTheVeryEndIsAPlainLine() {
        open("| a | b |\n|---|---|\n| x | y |\n");
        caretAt(1 + "| a | b |\n|---|---|\n| x | y".length());
        key(javafx.scene.input.KeyCode.TAB);
        fx(() -> { view.model().type("z"); return null; });
        assertEquals("| a | b |\n|---|---|\n| x | y |\n| z |  |\n", Lossless.write(fx(() -> view.model().state().getDoc())));
        open("| a | b |\n|---|---|\n| x | y |\n");
        caretAt(1 + "| a | b |\n|---|---|\n| x | y |".length());
        key(javafx.scene.input.KeyCode.ENTER);
        fx(() -> { view.model().type("| p | q |"); return null; });
        assertEquals("| a | b |\n|---|---|\n| x | y |\n| p | q |\n", Lossless.write(fx(() -> view.model().state().getDoc())));
    }

    @Test void theTableAndCodeBlockCommandsWriteMarkdown() {
        open("before\n");
        caretAt(1 + "before".length());
        fx(() -> view.model().run(prosefx.core.commands.Commands.insertTable(2, 3)));
        assertEquals("before\n\n| Column 1 | Column 2 | Column 3 |\n|---|---|---|\n|  |  |  |\n|  |  |  |\n", Lossless.write(fx(() -> view.model().state().getDoc())));
        assertEquals("Column 1", fx(() -> { var s = view.model().state().getSelection(); return view.model().state().getDoc().textBetween(s.getFrom(), s.getTo(), (String) null, (String) null); }));
        pulse();
        assertInstanceOf(Table.TableFlow.class, block(1).flow);
        open("some code\n");
        caretAt(3);
        fx(() -> view.model().run(prosefx.core.commands.Commands::codeBlock));
        assertEquals("```\nsome code\n```\n", Lossless.write(fx(() -> view.model().state().getDoc())));
    }

    @Test void theHeaderRowHasABackground() {
        open(TABLE);
        var bg = fx(() -> view.tableGrid.heads.getFirst());
        assertTrue(fx(() -> bg.isVisible() && bg.getHeight() > 5 && bg.getWidth() > 50));
    }

    @Test void aRightAlignedColumnEndsItsCellsTogetherAndACentredOneCentresThem() {
        open(TABLE);   // Count is ---: (right)
        var tb = block(1);
        // right: every cell's text ends where its closing pipe starts, at the same x on every row
        var closers = fx(() -> pipes(tb).stream().filter(p -> !p.lead && p.col == 1 && p.line != 1).map(p -> p.getBoundsInParent().getMinX()).toList());
        for (var x : closers) assertEquals(closers.getFirst(), x, 0.75, "right-aligned ends: " + closers);
        open("| a | b |\n|:-:|---|\n| x | y |\n| a much longer cell | z |\n");
        var t2 = block(0);
        // centre: the short cell's room is split, so the pipe before it (the opening one) is wider than LEAD
        var opening = fx(() -> pipes(t2).stream().filter(p -> p.lead && p.line == 2).findFirst().orElseThrow().getWidth());
        assertTrue(opening > Table.LEAD + 5, "opening pipe holds half the room: " + opening);
        assertEquals(List.of('c', 'l'), fx(() -> Table.aligns(t2.node)));
        assertEquals(List.of('l', 'c', 'r'), Table.aligns(prosefx.markdown.Lossless.read("a | b | c\n--- | :---: | ---:\n").getFirstChild()));
    }

    // ---- adding and deleting rows and columns

    String apply(String md, String caretAfter, TableEdit.Op op) {
        open(md);
        var tb = fx(() -> view.positions.textblocks.stream().filter(t -> t.flow instanceof Table.TableFlow).findFirst().orElseThrow());
        var pos = fx(() -> Fixtures.posAfter(view.model().state().getDoc(), caretAfter));
        assertTrue(fx(() -> TableEdit.apply(view, tb, pos - tb.start, op)), op + " applies");
        pulse();
        return Lossless.write(fx(() -> view.model().state().getDoc()));
    }

    static final String SMALL = "before\n\n| a | **b** |\n|---|---:|\n| x | y |\n\nafter\n";

    @Test void columnsGoInLeftOrRightOfTheCaretsColumnWithTheSeparatorAndTheFormattingKept() {
        assertEquals("before\n\n| a |  | **b** |\n|---|---|---:|\n| x |  | y |\n\nafter\n", apply(SMALL, "| a", TableEdit.Op.COL_RIGHT));
        assertEquals("before\n\n|  | a | **b** |\n|---|---|---:|\n|  | x | y |\n\nafter\n", apply(SMALL, "| a", TableEdit.Op.COL_LEFT));
        assertEquals("before\n\n| a | **b** |  |\n|---|---:|---|\n| x | y |  |\n\nafter\n", apply(SMALL, "| x | y", TableEdit.Op.COL_RIGHT));
        assertEquals("a | b |  |\n---|---|---|\nx | y |  |\n", apply("a | b\n---|---\nx | y\n", "x | y", TableEdit.Op.COL_RIGHT));
    }

    @Test void aColumnIsDeletedWithItsPipeOnEveryLine() {
        assertEquals("before\n\n| a |\n|---|\n| x |\n\nafter\n", apply(SMALL, "| x | y", TableEdit.Op.DELETE_COL));
        assertEquals("before\n\n| **b** |\n|---:|\n| y |\n\nafter\n", apply(SMALL, "| a", TableEdit.Op.DELETE_COL));
    }

    @Test void rowsGoAboveOrBelowAndNeverBetweenTheHeaderAndTheSeparator() {
        assertEquals("before\n\n| a | **b** |\n|---|---:|\n| x | y |\n|  |  |\n\nafter\n", apply(SMALL, "| x", TableEdit.Op.ROW_BELOW));
        assertEquals("before\n\n| a | **b** |\n|---|---:|\n|  |  |\n| x | y |\n\nafter\n", apply(SMALL, "| x", TableEdit.Op.ROW_ABOVE));
        assertEquals("before\n\n| a | **b** |\n|---|---:|\n|  |  |\n| x | y |\n\nafter\n", apply(SMALL, "| a", TableEdit.Op.ROW_ABOVE));
    }

    @Test void aBodyRowAndTheWholeTableCanBeDeletedButNotTheHeader() {
        assertEquals("before\n\n| a | **b** |\n|---|---:|\n\nafter\n", apply(SMALL, "| x", TableEdit.Op.DELETE_ROW));
        assertEquals("before\n\nafter\n", apply(SMALL, "| x", TableEdit.Op.DELETE_TABLE));
        open(SMALL);
        var tb = block(1);
        var header = fx(() -> Fixtures.posAfter(view.model().state().getDoc(), "| a")) - tb.start;
        assertFalse(fx(() -> TableEdit.allowed(tb, header, TableEdit.Op.DELETE_ROW)));
        open("| only |\n|---|\n| one |\n");
        assertFalse(fx(() -> TableEdit.allowed(block(0), 3, TableEdit.Op.DELETE_COL)));
    }

    @Test void undoTakesAColumnBackInOneStep() {
        apply(SMALL, "| a", TableEdit.Op.COL_RIGHT);
        fx(() -> view.model().run(prosefx.core.commands.Commands.undo));
        assertEquals(SMALL, Lossless.write(fx(() -> view.model().state().getDoc())));
    }

    @Test void theMenuListsEveryActionAndGreysOutWhatCannotApply() {
        open(SMALL);
        var tb = block(1);
        var header = fx(() -> Fixtures.posAfter(view.model().state().getDoc(), "| a")) - tb.start;
        var items = fx(() -> TableMenu.menu(view, tb, header).getItems().stream().filter(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem)).toList());
        assertEquals(TableEdit.Op.values().length, items.size());
        assertTrue(fx(() -> items.get(TableEdit.Op.DELETE_ROW.ordinal()).isDisable()));
        assertFalse(fx(() -> items.get(TableEdit.Op.COL_RIGHT.ordinal()).isDisable()));
    }

    // ---- a table wider than the page

    /** Pulses until the condition holds on the FX thread, at most 20. */
    void settle(java.util.function.BooleanSupplier ok) {
        // ViewKeysTest.pulse waits on its own stage's scene, not this one: poll instead, for up to 3 s
        for (var i = 0; i < 60 && !fx(() -> ok.getAsBoolean()); i++) { pulse(); try { Thread.sleep(50); } catch (InterruptedException e) { throw new RuntimeException(e); } }
    }

    static final String WIDE = "| " + "word ".repeat(30) + "| " + "more ".repeat(30) + "|\n|---|---|\n| a | b |\n";

    @Test void aWideTableKeepsEachRowOnOneLineAndScrollsSideways() {
        open(WIDE);
        var tb = block(0);
        assertInstanceOf(Table.TableScroll.class, tb.fx);
        // three rows (the separator a hairline): three lines, nothing wrapped
        assertEquals(3, fx(() -> tb.flow.getLayoutInfo().getTextLines(false).size()));
        var s = (Table.TableScroll) tb.fx;
        assertTrue(fx(() -> tb.flow.getWidth() > s.getWidth()), "the table is wider than its pane");
        assertTrue(fx(() -> s.overflows(-1)));
        // the caret at the end of the long header brings it into view: the pane scrolls right
        fx(() -> { view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), Fixtures.posAfter(view.model().state().getDoc(), "more more |"))); return null; });
        settle(() -> s.getHvalue() > 0.5);
        assertTrue(fx(() -> s.getHvalue()) > 0.5, "scrolled to the caret: " + fx(() -> s.getHvalue()));
        // and back to the first cell
        fx(() -> { view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), 2)); return null; });
        settle(() -> s.getHvalue() < 0.1);
        assertTrue(fx(() -> s.getHvalue()) < 0.1, "scrolled back: " + fx(() -> s.getHvalue()));
    }

    @Test void aNarrowTableHasNoScrollBarRoom() {
        open(TABLE);
        var s = (Table.TableScroll) block(1).fx;
        assertFalse(fx(() -> s.overflows(-1)));
        assertEquals(fx(() -> block(1).flow.getHeight()), fx(() -> s.getHeight()), 1.0);
    }

    @Test void theGridIsDrawn() {
        open(TABLE);
        var grid = fx(() -> view.tableGrid.pool);
        assertEquals(1, grid.size());
        // the header top, the top of each body row (the first is the line under the header), the bottom: 4 horizontal; left edge and 2 column ends: 3 vertical
        assertEquals(2 * (4 + 3), fx(() -> grid.getFirst().getElements().size()));
    }
}
