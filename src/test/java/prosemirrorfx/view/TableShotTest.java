package prosemirrorfx.view;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosemirrorfx.core.editor.EditorModel;
import prosemirrorfx.markdown.Lossless;
import static prosemirrorfx.view.ViewKeysTest.fx;
import static prosemirrorfx.view.ViewKeysTest.pulse;

/** A picture of a table, written only when -Dshot=<file.png> is given; for a person to look at. */
class TableShotTest {
    @Test void shot() throws Exception {
        var file = System.getProperty("shot");
        Assumptions.assumeTrue(file != null);
        FxBoot.start();
        var md = "Some text before the table.\n\n| Feature | State | Notes |\n|---|:---:|---|\n| Tables | **drawn** | as a grid, text unchanged |\n| Highlight | ^^done^^ | typed or read |\n| A much longer feature name | ok | `code|pipe` stays |\n\nno | leading | pipe\n---|---|---\none | two | three\n\nAfter the table.\n";
        DocView[] v = new DocView[1];
        fx(() -> { var s = new Stage(); v[0] = new DocView(new EditorModel(Lossless.read(md))); s.setScene(new Scene(v[0], 760, 420)); s.show(); return null; });
        pulse(); pulse(); pulse();
        var img = fx(() -> v[0].snapshot(null, null));
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var r = img.getPixelReader();
        for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) out.setRGB(x, y, r.getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", new java.io.File(file));
    }
}
