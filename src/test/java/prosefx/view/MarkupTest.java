package prosefx.view;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosefx.core.editor.EditorModel;
import prosefx.markdown.Lossless;

import static org.junit.jupiter.api.Assertions.*;
import static prosefx.view.ViewKeysTest.fx;
import static prosefx.view.ViewKeysTest.pulse;

/** What the view draws for the Markdown extras: comment glyphs, ^^highlight^^ bands, ~~strike~~. Headless, like ViewKeysTest. */
class MarkupTest {

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

    java.util.List<Text> runs(int block) {
        return fx(() -> view.positions.textblocks.get(block).flow.getChildren().stream().map(t -> (Text) t).toList());
    }

    @Test void aUserCommentShowsAnAngleInPlaceOfTheTwoCharacters() {
        open(">> got it?\n");
        var runs = runs(0);
        assertEquals("❯​", runs.getFirst().getText());
        assertTrue(runs.getFirst().getStyleClass().contains("pm-comment-user"));
        assertEquals(" got it?", runs.get(1).getText());
        assertEquals(">> got it?".length(), fx(() -> Motion.drawn(view.positions.textblocks.getFirst()).length()));
    }

    @Test void anAssistantReplyShowsABulletWhateverItsEmphasisAndStamp() {
        for (var src : new String[]{">> ^^AI^^: yes\n", ">> **AI**: yes\n", ">> AI (2026-01-15 09:30 UTC): yes\n", "\t>> ai: yes\n"}) {
            open(src);
            assertEquals("●​", runs(0).getFirst().getText(), src);
        }
    }

    @Test void theCaretAndTheDocumentStillCountEveryCharacter() {
        open(">> abc\n");
        var tb = fx(() -> view.positions.textblocks.getFirst());
        assertEquals(">> abc".length(), tb.flow.getChildren().stream().mapToInt(t -> ((Text) t).getText().length()).sum());
    }

    @Test void aHighlightedSpanGetsABandBehindIt() {
        open("a ^^marked^^ b\n");
        var bands = fx(() -> view.back.getChildren().stream().filter(n -> n.getStyleClass().contains("pm-highlight-box") && n.isVisible()).map(n -> (Region) n).toList());
        assertEquals(1, bands.size());
        assertTrue(bands.getFirst().getWidth() > 20);
    }

    @Test void noBandWithoutAHighlight() {
        open("plain text\n");
        assertEquals(0, fx(() -> view.back.getChildren().stream().filter(n -> n.getStyleClass().contains("pm-highlight-box") && n.isVisible()).count()));
    }

    @Test void strikeThroughIsAStyleOfTheRun() {
        open("x ~~gone~~ y\n");
        assertTrue(runs(0).stream().anyMatch(t -> t.getText().equals("gone") && t.getStyleClass().contains("strike")));
    }

    // ---- images

    static java.nio.file.Path folder;

    static java.nio.file.Path picture(String name) throws Exception {
        folder = java.nio.file.Files.createTempDirectory("pmfx-img");
        var img = new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = folder.resolve(name);
        java.nio.file.Files.createDirectories(out.getParent());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        return out;
    }

    java.util.List<javafx.scene.image.ImageView> pictures() {
        return fx(() -> view.positions.textblocks.stream().flatMap(t -> t.flow.getChildren().stream()).filter(n -> n instanceof javafx.scene.image.ImageView).map(n -> (javafx.scene.image.ImageView) n).toList());
    }

    void openIn(java.nio.file.Path dir, String md) {
        fx(() -> {
            view = new DocView(new EditorModel(Lossless.read(md)));
            view.setBaseDirectory(dir);
            stage.setScene(new Scene(view, 800, 600));
            stage.show();
        });
        pulse();
        for (var k = 0; k < 20 && pictures().isEmpty(); k++) { try { Thread.sleep(50); } catch (InterruptedException e) { } pulse(); }
    }

    @Test void aRelativeImageIsDrawnUnderItsSourceLine() throws Exception {
        picture("pics/a b.png");
        openIn(folder, "![shot](pics/a%20b.png)\n");
        assertEquals(1, pictures().size());
        assertEquals("![shot](pics/a%20b.png)", fx(() -> view.positions.textblocks.getFirst().node.getTextContent()));
        assertEquals("![shot](pics/a%20b.png)".length(), fx(() -> Motion.drawn(view.positions.textblocks.getFirst()).length()));
        assertTrue(fx(() -> view.positions.textblocks.getFirst().flow.getStyleClass().contains("pm-image-only")));
    }

    @Test void aMissingFileOrAWebAddressShowsNoPicture() throws Exception {
        picture("x.png");
        openIn(folder, "![a](nothing.png)\n\n![b](https://example.invalid/p.png)\n");
        assertEquals(0, pictures().size());
    }

    @Test void theImageSyntaxIsFoundWithATitleAndAngleBrackets() {
        assertEquals(java.util.List.of("a.png", "b c.png"), Images.destinations("x ![one](a.png \"t\") y ![two](<b c.png>)"));
        assertTrue(Images.only("![x](a.png)  "));
        assertFalse(Images.only("see ![x](a.png)"));
    }

    // ---- rule, quote, task boxes

    java.util.List<String> classesOf(int block) { return fx(() -> view.positions.textblocks.get(block).flow.getStyleClass().stream().toList()); }

    @Test void aRuleAQuoteAndTaskBoxesGetALook() {
        open("above\n\n---\n\n> said\n\n- [ ] open\n- [x] done\n");
        assertTrue(classesOf(1).contains("pm-rule"));
        assertTrue(classesOf(2).contains("pm-quote"));
        assertEquals("☐​​", runs(3).getFirst().getText());
        assertEquals("☑​​", runs(4).getFirst().getText());
        assertEquals(" open", runs(3).get(1).getText());
        assertEquals("[x] done".length(), fx(() -> Motion.drawn(view.positions.textblocks.get(4)).length()));
    }

    @Test void aCommentIsNotAQuoteAndAPlainParagraphIsNeither() {
        open(">> remark\n\nnothing special\n\n[ ] not in a list\n");
        assertFalse(classesOf(0).contains("pm-quote"));
        assertFalse(classesOf(1).contains("pm-quote") || classesOf(1).contains("pm-rule"));
        assertEquals("[ ] not in a list", runs(2).getFirst().getText());
    }

    // ---- edit source

    String areaText() { return fx(() -> view.source.area.getText()); }

    @Test void sourceModeShowsTheTextAndAnUneditedReturnKeepsTheDocument() {
        var md = "# Title\r\n\r\n>> got it?\r\n\r\n- [ ] a ^^b^^\r\n";
        open(md);
        var before = fx(() -> view.model().state().getDoc());
        fx(() -> view.setSourceMode(true));
        pulse();
        assertTrue(view.sourceMode());
        assertEquals(md.replace("\r\n", "\n"), areaText());
        assertFalse(fx(() -> view.scroll.isVisible()));
        fx(() -> view.setSourceMode(false));
        pulse();
        assertFalse(view.sourceMode());
        assertSame(before, fx(() -> view.model().state().getDoc()));
        assertTrue(fx(() -> view.scroll.isVisible()));
    }

    @Test void anEditInSourceModeComesBackAsTheDocumentWithTheFilesLineEnding() {
        open("one\r\n\r\ntwo\r\n");
        fx(() -> view.setSourceMode(true));
        fx(() -> view.source.area.setText("one\n\n^^two^^ changed\n"));
        fx(() -> view.setSourceMode(false));
        pulse();
        assertEquals("one\r\n\r\n^^two^^ changed\r\n", Lossless.write(fx(() -> view.model().state().getDoc())));
        assertTrue(runs(1).stream().anyMatch(t -> t.getText().equals("two") && t.getStyleClass().contains("highlight")));
    }

    @Test void theToolbarHasTheButtonAndKeysAreLeftToTheTextArea() {
        open("abc\n");
        var buttons = fx(() -> Toolbar.of(view).lookupAll(".pm-tool").size());
        assertTrue(buttons >= EditorAction.values().length - 2);
        fx(() -> view.setSourceMode(true));
        var doc = fx(() -> view.model().state().getDoc());
        fx(() -> view.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_TYPED, "z", "z", javafx.scene.input.KeyCode.UNDEFINED, false, false, false, false)));
        assertSame(doc, fx(() -> view.model().state().getDoc()));
    }

    // ---- jumping between comments

    int head() { return fx(() -> view.model().state().getSelection().getHead()); }

    @Test void jumpingVisitsEachCommentAndWrapsAround() {
        open("intro\n\n>> one\n\ntext\n\n>> two\n\n\t>> ^^AI^^: reply\n\nend\n");
        fx(() -> view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), 1)));
        var seen = new java.util.ArrayList<String>();
        for (var k = 0; k < 4; k++) {
            assertTrue(fx(() -> view.jumpComment(1)));
            seen.add(fx(() -> view.model().state().getDoc().resolve(view.model().state().getSelection().getHead()).getParent().getTextContent()));
        }
        assertEquals(java.util.List.of(">> one", ">> two", ">> ^^AI^^: reply".replace("^^", ""), ">> one"), seen);
        assertTrue(fx(() -> view.jumpComment(-1)));
        assertEquals(">> ^^AI^^: reply".replace("^^", ""), fx(() -> view.model().state().getDoc().resolve(view.model().state().getSelection().getHead()).getParent().getTextContent()));
    }

    @Test void noCommentsNothingToJumpTo() {
        open("plain\n\nonly text\n");
        var before = head();
        assertFalse(fx(() -> view.jumpComment(1)));
        assertEquals(before, head());
    }

    // ---- task boxes, links, footnotes

    void caretAfter(String snippet) {
        fx(() -> view.model().select(prosefx.core.pm.Pm.cursor(view.model().state().getDoc(), Fixtures.posAfter(view.model().state().getDoc(), snippet))));
    }

    String written() { return Lossless.write(fx(() -> view.model().state().getDoc())); }

    @Test void ctrlEnterSwitchesATaskBoxAndUndoPutsItBack() {
        open("- [ ] open\n- [x] done\n");
        caretAfter("open");
        assertTrue(fx(() -> view.activate()));
        assertEquals("- [x] open\n- [x] done\n", written());
        caretAfter("done");
        assertTrue(fx(() -> view.activate()));
        assertEquals("- [x] open\n- [ ] done\n", written());
        fx(() -> view.model().run(prosefx.core.commands.Commands.undo));
        assertEquals("- [x] open\n- [x] done\n", written());
    }

    @Test void ctrlEnterDoesNothingOutsideATaskOrALink() {
        open("plain\n\n- not a task\n");
        caretAfter("plain");
        assertFalse(fx(() -> view.activate()));
    }

    @Test void aClickOnTheBoxSwitchesItButAClickOnTheTextOnlyPlacesTheCaret() {
        open("- [ ] open item\n");
        var tb = fx(() -> view.positions.textblocks.getFirst());
        var box = fx(() -> tb.flow.localToScene(tb.flow.getLayoutInfo().getSelectionGeometry(0, 3, true).getFirst().getMinX() + 3, tb.flow.getLayoutInfo().getSelectionGeometry(0, 3, true).getFirst().getMinY() + 4));
        var text = fx(() -> tb.flow.localToScene(tb.flow.getLayoutInfo().getSelectionGeometry(8, 12, true).getFirst().getMinX() + 3, tb.flow.getLayoutInfo().getSelectionGeometry(8, 12, true).getFirst().getMinY() + 4));
        click(text.getX(), text.getY(), false);
        assertEquals("- [ ] open item\n", written());
        click(box.getX(), box.getY(), false);
        assertEquals("- [x] open item\n", written());
    }

    void click(double x, double y, boolean ctrl) {
        fx(() -> javafx.event.Event.fireEvent(view.page, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED, x, y, x, y,
            javafx.scene.input.MouseButton.PRIMARY, 1, false, ctrl, false, false, true, false, false, true, false, true, null)));
        pulse();
    }

    @Test void aBareAddressIsStyledAsALinkAndCtrlEnterHandsItToTheHandler() {
        open("see https://example.org/a?b=1, then more\n");
        assertTrue(runs(0).stream().anyMatch(t -> t.getText().equals("https://example.org/a?b=1") && t.getStyleClass().contains("pm-autolink")));
        var got = new java.util.ArrayList<String>();
        fx(() -> view.setLinkHandler(got::add));
        caretAfter("https://example.org/a");
        assertTrue(fx(() -> view.activate()));
        assertEquals(java.util.List.of("https://example.org/a?b=1"), got);
    }

    @Test void aMarkedLinkAndARelativePathGoToTheHandlerAsWritten() {
        open("a [site](https://example.org) and [notes](other/notes.md) end\n");
        var got = new java.util.ArrayList<String>();
        fx(() -> view.setLinkHandler(got::add));
        caretAfter("sit");
        fx(() -> view.activate());
        caretAfter("note");
        fx(() -> view.activate());
        assertEquals(java.util.List.of("https://example.org", "other/notes.md"), got);
    }

    @Test void aFootnoteReferenceJumpsToItsDefinitionAndTheLabelJumpsBack() {
        open("A claim[^1] stands.\n\nMore.\n\n[^1]: The source.\n");
        assertTrue(runs(0).stream().anyMatch(t -> t.getText().equals("[^1]") && t.getStyleClass().contains("footnote")));
        assertTrue(fx(() -> view.positions.textblocks.getLast().flow.getStyleClass().contains("pm-footnote-def")));
        caretAfter("[^1");
        assertTrue(fx(() -> view.activate()));
        assertEquals("[^1]: The source.", fx(() -> view.model().state().getDoc().resolve(view.model().state().getSelection().getHead()).getParent().getTextContent()));
        assertTrue(fx(() -> view.activate()));
        assertEquals("A claim[^1] stands.", fx(() -> view.model().state().getDoc().resolve(view.model().state().getSelection().getHead()).getParent().getTextContent()));
    }
}
