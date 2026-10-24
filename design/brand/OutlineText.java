import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a line of text into SVG path data, so a logo does not depend on the viewer having the font.
 *
 * Usage: java OutlineText.java <font.ttf> <size> <text>
 *
 * Prints one line: `<advance> <minX> <minY> <maxX> <maxY> <path d>`. The origin is the baseline's
 * left end and y grows downward, as in SVG. TextLayout does the shaping, so Arabic letters join.
 */
public class OutlineText {
    public static void main(String[] args) throws Exception {
        Font base = Font.createFont(Font.TRUETYPE_FONT, new File(args[0]));
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.SIZE, Float.parseFloat(args[1]));
        attrs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attrs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        Font font = base.deriveFont(attrs);

        TextLayout layout = new TextLayout(args[2], font, new FontRenderContext(null, true, true));
        Shape outline = layout.getOutline(null);
        Rectangle2D b = outline.getBounds2D();

        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = outline.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> d.append(String.format(Locale.ROOT, "M%.1f %.1f", c[0], c[1]));
                case PathIterator.SEG_LINETO -> d.append(String.format(Locale.ROOT, "L%.1f %.1f", c[0], c[1]));
                case PathIterator.SEG_QUADTO -> d.append(String.format(Locale.ROOT, "Q%.1f %.1f %.1f %.1f", c[0], c[1], c[2], c[3]));
                case PathIterator.SEG_CUBICTO -> d.append(String.format(Locale.ROOT, "C%.1f %.1f %.1f %.1f %.1f %.1f", c[0], c[1], c[2], c[3], c[4], c[5]));
                case PathIterator.SEG_CLOSE -> d.append("Z");
                default -> { }
            }
        }
        System.out.println(String.format(Locale.ROOT, "%.1f %.1f %.1f %.1f %.1f %s",
                layout.getAdvance(), b.getMinX(), b.getMinY(), b.getMaxX(), b.getMaxY(), d));
    }
}
