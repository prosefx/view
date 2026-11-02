package prosefx.view;

import com.atlassian.prosemirror.model.Node;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import javafx.scene.image.*;
import javafx.scene.text.Text;

/**
 * Pictures for `![alt](path)`. The text stays in the document exactly as written (so the file is unchanged); the picture
 * is drawn after the paragraph's own characters, in the same flow, under a line break. Those extra children come after
 * every document character, so positions are not disturbed (the grey dictation text is added the same way). A relative
 * path is read against the folder the host set with DocView.setBaseDirectory; a web address is fetched only when the host
 * allowed it with setRemoteImages, because opening a file should not tell a stranger's server that someone read it.
 */
final class Images {

    static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(\\s*(<[^>]*>|[^)\\s]+)(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");

    /** The destinations of the images written in the paragraph's text, in order. */
    static List<String> destinations(String text) {
        var out = new ArrayList<String>();
        var m = IMAGE.matcher(text);
        while (m.find()) {
            var d = m.group(1);
            out.add(d.startsWith("<") ? d.substring(1, d.length() - 1) : d);
        }
        return out;
    }

    /** True when the paragraph is nothing but image syntax (and spaces): its source line is then drawn small and quiet. */
    static boolean only(String text) { return !text.isBlank() && IMAGE.matcher(text).replaceAll("").isBlank(); }

    /** Draws the paragraph's images once the flow is on a scene, where the DocView that holds the folder can be found. */
    static void attach(Flow flow, Node textblock) {
        var text = textblock.getTextContent();
        if (text.indexOf("![") < 0) return;
        var found = destinations(text);
        if (found.isEmpty()) return;
        if (only(text)) flow.getStyleClass().add("pm-image-only");
        flow.sceneProperty().addListener((o, was, scene) -> {
            if (scene == null || flow.getProperties().containsKey("pm-images")) return;
            flow.getProperties().put("pm-images", true);
            var view = view(flow);
            if (view != null) found.forEach(d -> show(flow, view, d));
        });
    }

    static DocView view(javafx.scene.Node n) {
        for (; n != null; n = n.getParent()) if (n instanceof DocView v) return v;
        return null;
    }

    static void show(Flow flow, DocView view, String destination) {
        var url = url(destination, view.baseDirectory, view.remoteImages);
        if (url == null) return;
        var image = new Image(url, true);
        var picture = new ImageView(image);
        picture.setPreserveRatio(true);
        picture.setSmooth(true);
        var gap = new Text("\n");
        gap.getStyleClass().add("pm-text");
        var fit = (Runnable) () -> picture.setFitWidth(image.getWidth() <= 0 ? 0 : Math.min(image.getWidth(), Math.max(60, flow.getWidth() - 12)));
        image.progressProperty().addListener((o, was, now) -> { if (now.doubleValue() >= 1) fit.run(); });
        image.errorProperty().addListener((o, was, bad) -> { if (bad) flow.getChildren().removeAll(gap, picture); });
        flow.widthProperty().addListener((o, was, now) -> fit.run());
        flow.getChildren().addAll(gap, picture);
        fit.run();
    }

    /** A loadable address for a markdown destination, or null: unresolved relative path, remote not allowed, no such file. */
    static String url(String destination, Path base, boolean remote) {
        var d = destination.strip();
        if (d.regionMatches(true, 0, "http://", 0, 7) || d.regionMatches(true, 0, "https://", 0, 8)) return remote ? d : null;
        if (d.regionMatches(true, 0, "data:", 0, 5)) return null;
        try {
            var plain = d.startsWith("file:") ? Path.of(java.net.URI.create(d)) : null;
            if (plain == null) {
                var decoded = URLDecoder.decode(d.replace("+", "%2B"), StandardCharsets.UTF_8);
                var p = Path.of(decoded);
                plain = p.isAbsolute() ? p : base == null ? null : base.resolve(p);
            }
            return plain != null && Files.isRegularFile(plain) ? plain.toUri().toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Images() {}
}
