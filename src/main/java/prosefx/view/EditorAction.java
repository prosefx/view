package prosefx.view;

import java.util.function.*;
import javafx.scene.Node;
import prosefx.core.commands.*;
import prosefx.core.schema.EditorSchema;

import static prosefx.core.commands.Commands.*;
import static prosefx.core.schema.EditorSchema.*;

/**
 * The toolbar's actions, in toolbar order. The editor binds no F-keys: those belong to the host application.
 * Each action names its standard shortcut (bound in EditorKeys) for the tooltip, its icon (icons.tsv), and
 * when it shows pressed or greyed out.
 */
public enum EditorAction {

    UNDO("Undo", "Ctrl+Z", "undo", Commands.undo),
    REDO("Redo", "Ctrl+Y", "redo", Commands.redo),
    STYLE("Paragraph style", "Ctrl+Alt+0..6", null, (BiConsumer<DocView, Node>) BlockMenu::open),
    BOLD("Bold", "Ctrl+B", "format_bold", Commands.bold),
    ITALIC("Italic", "Ctrl+I", "format_italic", Commands.italic),
    STRIKE("Strikethrough (~~text~~)", "", "format_strikethrough", Basic.toggleMark(EditorSchema.STRIKE)),
    HIGHLIGHT("Highlight (^^text^^)", "", "border_color", Basic.toggleMark(EditorSchema.HIGHLIGHT)),
    CODE("Inline code (`text`)", "", "code", Basic.toggleMark(EditorSchema.CODE)),
    CODE_BLOCK("Code block (```)", "", "data_object", (Command) Commands::codeBlock),
    TABLE("Table: insert one, or in a table add and delete rows and columns", "", "table_chart", (BiConsumer<DocView, Node>) (view, from) -> {
        if (TableMenu.tableAtCaret(view) == null) { TablePicker.open(view, from); return; }
        var at = from.localToScreen(from.getLayoutBounds());
        TableMenu.open(view, at.getMinX(), at.getMaxY() + 2);
    }),
    BULLETS("Bulleted list", "Ctrl+Shift+8", "format_list_bulleted", Commands.bulletList),
    NUMBERS("Numbered list", "Ctrl+Shift+7", "format_list_numbered", Commands.orderedList),
    OUTDENT("Decrease indent", "Shift+Tab", "format_indent_decrease", Commands.outdent),
    INDENT("Increase indent", "Tab", "format_indent_increase", Commands.indent),
    CLEAR("Clear formatting", "Ctrl+\\", "format_clear", Commands.clearFormatting),
    COPY_ALL("Copy all", "", "content_copy", (BiConsumer<DocView, Node>) (view, from) -> view.copyAll(CopyFormat.FORMATTED)),
    FIND("Find and replace", "Ctrl+F", "find_replace", (BiConsumer<DocView, Node>) (view, from) -> view.find()),
    CONTENTS("Contents", "", "toc", (BiConsumer<DocView, Node>) (view, from) -> { if (view.toc != null) view.toc.toggle(); }),
    LINES("Line numbers", "", "123", (BiConsumer<DocView, Node>) (view, from) -> view.setLineNumbers(!view.lineNumbers())),
    ACTIVE_LINE("Highlight current line", "", "highlight", (BiConsumer<DocView, Node>) (view, from) -> view.setActiveLine(!view.activeLine())),
    SOURCE("Edit source (the Markdown text)", "", "notes", (BiConsumer<DocView, Node>) (view, from) -> view.setSourceMode(!view.sourceMode()));

    public final String label, shortcut, icon;
    final Command command;
    /** Runs the action; the node is the toolbar button it was clicked on. */
    final BiConsumer<DocView, Node> act;

    EditorAction(String label, String shortcut, String icon, Command command) {
        this.label = label;
        this.shortcut = shortcut;
        this.icon = icon;
        this.command = command;
        this.act = (view, from) -> view.model.run(command);
    }

    EditorAction(String label, String shortcut, String icon, BiConsumer<DocView, Node> act) {
        this.label = label;
        this.shortcut = shortcut;
        this.icon = icon;
        this.command = null;
        this.act = act;
    }

    /** "Bold (Ctrl+B)" */
    public String tooltip() { return shortcut.isEmpty() ? label : label + " (" + shortcut + ")"; }

    /** Offered only when the editor has what it needs: Contents a Toc placed beside it. */
    boolean available(DocView view) { return this != CONTENTS || view.toc != null; }

    /** Toggle buttons: pressed when the caret or selection already has it. */
    boolean toggle() { return switch (this) { case BOLD, ITALIC, STRIKE, HIGHLIGHT, CODE, BULLETS, NUMBERS, CONTENTS, LINES, ACTIVE_LINE, SOURCE -> true; default -> false; }; }

    boolean active(DocView view) {
        var s = view.model.state();
        return switch (this) {
            case BOLD -> markActive(s, STRONG);
            case ITALIC -> markActive(s, EM);
            case STRIKE -> markActive(s, EditorSchema.STRIKE);
            case HIGHLIGHT -> markActive(s, EditorSchema.HIGHLIGHT);
            case CODE -> markActive(s, EditorSchema.CODE);
            case BULLETS -> inList(s, BULLET_LIST);
            case NUMBERS -> inList(s, ORDERED_LIST);
            case CONTENTS -> view.toc != null && view.toc.showing();
            case LINES -> view.lineNumbers();
            case ACTIVE_LINE -> view.activeLine();
            case SOURCE -> view.sourceMode();
            default -> false;
        };
    }

    /** Greyed out when it would do nothing here (nothing to undo, no list to outdent, ...). */
    boolean enabled(DocView view) {
        return switch (this) {
            case UNDO, REDO, OUTDENT, INDENT, CODE_BLOCK -> command.applies(view.model.state());
            default -> true;
        };
    }

    /** Whether the focus goes back to the text after the button is clicked. */
    boolean refocus() { return this != STYLE && this != CONTENTS && this != FIND && this != SOURCE && this != TABLE; }
}
