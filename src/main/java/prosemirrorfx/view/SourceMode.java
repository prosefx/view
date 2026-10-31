package prosemirrorfx.view;

import javafx.beans.property.*;
import javafx.scene.control.TextArea;
import prosemirrorfx.markdown.Lossless;

/**
 * Edit source: the same document as its Markdown text in a plain text area, for when the formatted view is not enough.
 * A control of the editor like the line numbers, so every application that shows a DocView gets it. Going in shows the
 * text as it would be saved, with the caret where it was; coming out reads it back, and when nothing was edited the
 * document is left alone (the undo history, and every byte of the file, are kept). An edited text replaces the document, as
 * opening a file does, which clears the undo history.
 */
final class SourceMode {

    final DocView view;
    final TextArea area = new TextArea();
    final ReadOnlyBooleanWrapper on = new ReadOnlyBooleanWrapper();
    String shown = "";
    boolean crlf;

    SourceMode(DocView view) {
        this.view = view;
        area.getStyleClass().add("pm-source");
        area.setWrapText(true);
        area.setVisible(false);
        area.setManaged(false);
    }

    void set(boolean want) {
        if (want == on.get()) return;
        if (want) enter(); else leave();
    }

    void enter() {
        var text = Lossless.write(view.model.state().getDoc());
        var caret = view.caretSourceOffset();
        crlf = text.contains("\r\n");
        shown = text.replace("\r\n", "\n");
        area.setText(shown);
        area.positionCaret(Math.min(caret, shown.length()));
        if (view.findBar.isVisible()) view.findBar.close();
        view.scroll.setVisible(false);
        area.setManaged(true);
        area.setVisible(true);
        on.set(true);
        area.requestFocus();
    }

    void leave() {
        var text = area.getText();
        var caret = area.getCaretPosition();
        area.setVisible(false);
        area.setManaged(false);
        view.scroll.setVisible(true);
        on.set(false);
        if (!text.equals(shown)) view.model.load(Lossless.read(crlf ? text.replace("\n", "\r\n") : text));
        view.setCaretSourceOffset(Math.min(caret, view.sourceMap().text().length()));
        view.requestFocus();
        view.invalidate();
    }

    boolean active() { return on.get(); }
}
