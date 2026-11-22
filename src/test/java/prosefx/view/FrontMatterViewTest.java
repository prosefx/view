package prosefx.view;

import javafx.scene.Scene;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosefx.core.editor.EditorModel;
import prosefx.markdown.Lossless;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.view.ViewKeysTest.fx;
import static prosefx.view.ViewKeysTest.pulse;

/** YAML front matter drawn as a property block, with every character still in place. Headless, like MarkupTest. */
class FrontMatterViewTest {

    static final String MD = "---\nnumber: 1\nnumber_b34: \"0001\"\nshort_name: example\ntags: [a, \"b: c\"]\n# a comment\nlist:\n  - x\n---\n\n# Requirement\n\nBody: not front matter.\n";

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
    }

    List<Text> runs(int block) {
        return fx(() -> view.positions.textblocks.get(block).flow.getChildren().stream().map(t -> (Text) t).toList());
    }

    boolean has(Text t, String cls) { return t.getStyleClass().contains(cls); }

    @Test void theBlockAtTheTopIsAPropertyBlockAndTheBodyIsNot() {
        open(MD);
        var front = fx(() -> view.positions.textblocks.get(0).flow.getStyleClass().contains("pm-front"));
        assertTrue(front);
        assertFalse(fx(() -> view.positions.textblocks.get(1).flow.getStyleClass().contains("pm-front")));
        assertFalse(fx(() -> view.positions.textblocks.get(2).flow.getStyleClass().contains("pm-front")));
    }

    @Test void everyCharacterIsStillThereSoPositionsDoNotMove() {
        open(MD);
        var drawn = runs(0).stream().map(Text::getText).collect(Collectors.joining());
        assertEquals("---\nnumber: 1\nnumber_b34: \"0001\"\nshort_name: example\ntags: [a, \"b: c\"]\n# a comment\nlist:\n  - x\n---", drawn);
    }

    @Test void keysValuesDelimitersAndCommentsAreStyledApart() {
        open(MD);
        var runs = runs(0);
        assertEquals("---", runs.getFirst().getText());
        assertTrue(has(runs.getFirst(), "pm-front-delim"));
        var key = runs.stream().filter(t -> t.getText().equals("short_name:")).findFirst().orElseThrow();
        assertTrue(has(key, "pm-front-key"));
        var value = runs.stream().filter(t -> t.getText().equals(" example")).findFirst().orElseThrow();
        assertTrue(has(value, "pm-front-value"));
        // a colon inside the value does not move the split
        assertTrue(runs.stream().anyMatch(t -> t.getText().equals("tags:") && has(t, "pm-front-key")));
        assertTrue(runs.stream().anyMatch(t -> t.getText().equals(" [a, \"b: c\"]") && has(t, "pm-front-value")));
        assertTrue(runs.stream().anyMatch(t -> t.getText().equals("# a comment") && has(t, "pm-front-comment")));
        // a list item under a key is plain value text
        assertTrue(runs.stream().anyMatch(t -> t.getText().equals("  - x") && has(t, "pm-front-value")));
        assertTrue(has(runs.getLast(), "pm-front-delim"));
    }

    @Test void aFileWithoutFrontMatterIsUnchanged() {
        open("# Title\n\n---\n\nText\n");
        assertFalse(fx(() -> view.positions.textblocks.stream().anyMatch(tb -> tb.flow.getStyleClass().contains("pm-front"))));
    }

    @Test void editingAValueRedrawsTheBlockAndSavesOnlyThatLine() {
        open("---\nk: v\n---\n\ntext\n");
        // the block starts at 0: "---" (3), a break (1), then "k: v"; position 9 is the end of that value
        fx(() -> {
            var model = view.model();
            model.select(prosefx.core.pm.Pm.cursor(model.state().getDoc(), 9));
            model.type("2");
        });
        pulse();
        assertEquals("---\nk: v2\n---", runs(0).stream().map(Text::getText).collect(Collectors.joining()));
        assertTrue(runs(0).stream().anyMatch(t -> t.getText().equals("k:") && has(t, "pm-front-key")));
        assertTrue(runs(0).stream().anyMatch(t -> t.getText().equals(" v2") && has(t, "pm-front-value")));
        assertEquals("---\nk: v2\n---\n\ntext\n", fx(() -> Lossless.write(view.model().state().getDoc())));
    }
}
