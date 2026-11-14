package orkToScad;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2D geometry of the flat parts (fins with their tab), shared by the .scad and .svg outputs.
 *
 * Fin coordinates, in mm: x along the root chord from the fin's leading edge, y along the span
 * (fin above y = 0, tab below).
 */
public final class FlatParts {

    /** Segments of the full ellipse used for elliptical fins (same as $fn=100 in OpenSCAD). */
    static final int ELLIPSE_SEGMENTS = 100;

    private FlatParts() {
    }

    /** Fin tab: rectangle [front, front + length] x [-height, 0]. */
    public static final class Tab {
        final double front;
        final double length;
        final double height;

        Tab(double front, double length, double height) {
            this.front = front;
            this.length = length;
            this.height = height;
        }

        boolean present() {
            return length > 0 && height > 0;
        }
    }

    /** One fin: outline polygon (root on y = 0) and optional tab. */
    public static final class Fin {
        final List<double[]> outline;
        final Tab tab;

        Fin(List<double[]> outline, Tab tab) {
            this.outline = outline;
            this.tab = tab;
        }

        /** Fin and tab as one closed shape (the cut line). */
        public Area area() {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(outline.get(0)[0], outline.get(0)[1]);
            for (int i = 1; i < outline.size(); i++) {
                p.lineTo(outline.get(i)[0], outline.get(i)[1]);
            }
            p.closePath();
            Area a = new Area(p);
            if (tab != null && tab.present()) {
                a.add(new Area(new Rectangle2D.Double(tab.front, -tab.height, tab.length, tab.height)));
            }
            return a;
        }
    }

    /**
     * Position of the tab's front edge from the fin's leading edge, as computed by OpenRocket
     * ({@code AxialMethod.getAsPosition}). {@code relativeTo} accepts the old names (front, center, end)
     * and the new ones (top, middle, bottom).
     */
    public static double tabFront(String relativeTo, double offset, double tabLength, double finLength) {
        String r = relativeTo == null ? "" : relativeTo.trim().toLowerCase(Locale.ROOT);
        if (r.contains("center") || r.equals("middle")) {
            return offset + (finLength - tabLength) / 2;
        }
        if (r.contains("end") || r.equals("bottom")) {
            return offset + (finLength - tabLength);
        }
        return offset; // "front", "top" (and OpenRocket's behaviour for unknown values)
    }

    public static Fin trapezoid(double root, double tip, double sweep, double height, Tab tab) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] { 0, 0 });
        pts.add(new double[] { root, 0 });
        pts.add(new double[] { sweep + tip, height });
        pts.add(new double[] { sweep, height });
        return new Fin(pts, tab);
    }

    /** Half ellipse: same vertices as OpenSCAD's scale([1, 2h/root]) circle(d = root, $fn = 100), upper half. */
    public static Fin elliptical(double root, double height, Tab tab) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= ELLIPSE_SEGMENTS / 2; i++) {
            double a = 2 * Math.PI * i / ELLIPSE_SEGMENTS;
            double y = i == ELLIPSE_SEGMENTS / 2 ? 0 : height * Math.sin(a);
            pts.add(new double[] { root / 2 + root / 2 * Math.cos(a), y });
        }
        return new Fin(pts, tab);
    }

    /** Free-form fin from OpenRocket's points (mm), root from the first to the last point. */
    public static Fin freeform(List<double[]> points, Tab tab) {
        return new Fin(new ArrayList<>(points), tab);
    }
}
