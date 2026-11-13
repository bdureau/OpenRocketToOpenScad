package orkToScad;

import static orkToScad.FileUtils.fmt;
import static orkToScad.OrkValues.elements;
import static orkToScad.OrkValues.mm;
import static orkToScad.OrkValues.mmOr;
import static orkToScad.OrkValues.text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

/**
 * Generates one or more .scad files per OpenRocket component and, optionally, STL files for the
 * parts that are printed in 3D (nose cones, body tubes, inner tubes, tube couplers, centering rings).
 *
 * Nose cones use the single template {@code openscad/noseCone.scad} (rotate_extrude, OpenRocket
 * formulas and shape parameter). Other parts keep the OpenSCAD code of the previous version. Notes:
 * <ul>
 * <li>numbers are always written with '.' as decimal separator;</li>
 * <li>"auto" dimensions are resolved (see {@link OrkValues});</li>
 * <li>a component whose required dimensions are missing or invalid is skipped with a warning
 *     instead of being written with zeros;</li>
 * <li>I/O errors are recorded in the {@link ConversionReport} instead of being printed and ignored.</li>
 * </ul>
 */
public final class ScadGenerator {

    /** Nose cone shapes supported (OpenRocket name to output file name suffix). */
    private static final Map<String, String> NOSE_CONE_TEMPLATES = Map.of(
            "conical", "Conical",
            "ogive", "Ogive",
            "ellipsoid", "Ellipsoid",
            "powerseries", "PowerSeries",
            "parabolicseries", "ParabolicSeries",
            "haackseries", "HaackSeries");

    private final Path outDir;
    private final ConversionReport report;
    private final boolean stl;

    private int bulkheadCount;
    private int tubeCouplerCount;
    private int bodyTubeCount;
    private int innerTubeCount;
    private int freeformfinsetCount;
    private int ellipticalfinsetCount;
    private int trapezoidfinsetCount;
    private int centeringringCount;

    /**
     * @param stl also write a ready-to-print STL file next to the .scad file for nose cones,
     *            body tubes, inner tubes, tube couplers and centering rings
     */
    public ScadGenerator(Path outDir, ConversionReport report, boolean stl) {
        this.outDir = outDir;
        this.report = report;
        this.stl = stl;
    }

    // ------------------------------------------------------------------ components

    public void bulkhead(Element e) {
        int n = ++bulkheadCount;
        double r = OrkValues.outerRadiusInsideParent(e, "outerradius");
        double length = mm(e, "length");
        if (!require(e, "outerradius", r) || !require(e, "length", length)) {
            return;
        }
        write("bulkhead-cnc" + n + ".scad", "circle(r=" + fmt(r) + ", $fn=200);\n");
        write("bulkhead-3d" + n + ".scad", "cylinder(r=" + fmt(r) + ", h=" + fmt(length) + ", $fn=200);\n");
    }

    public void tubeCoupler(Element e) {
        int n = ++tubeCouplerCount;
        double r = OrkValues.outerRadiusInsideParent(e, "outerradius");
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "outerradius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("tube-coupler-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "tube-coupler-3d" + n + ".stl", RevolvedSolid.tubeProfile(r - thickness, r, length));
    }

    public void bodyTube(Element e) {
        int n = ++bodyTubeCount;
        double r = OrkValues.bodyTubeRadius(e);
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "radius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("body-tube-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "body-tube-3d" + n + ".stl", RevolvedSolid.tubeProfile(r - thickness, r, length));
    }

    public void innerTube(Element e) {
        int n = ++innerTubeCount;
        double r = mm(e, "outerradius");
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "outerradius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("inner-tube-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "inner-tube-3d" + n + ".stl", RevolvedSolid.tubeProfile(r - thickness, r, length));
    }

    public void centeringRing(Element e) {
        int n = ++centeringringCount;
        double outer = OrkValues.outerRadiusInsideParent(e, "outerradius");
        double length = mm(e, "length");
        if (!require(e, "outerradius", outer)) {
            return;
        }
        double inner = OrkValues.centeringRingInnerRadius(e, outer);
        if (!require(e, "innerradius", inner) || !require(e, "length", length)) {
            return;
        }
        if (inner >= outer) {
            warn(e, "inner radius (" + fmt(inner) + " mm) is not smaller than outer radius (" + fmt(outer)
                    + " mm), skipped");
            return;
        }
        String cnc = "difference() {\n"
                + "circle(r=" + fmt(outer) + ", $fn=200);\n"
                + "circle(r=" + fmt(inner) + ", $fn=200);\n"
                + "}\n";
        String solid = "difference() {\n"
                + "cylinder(r=" + fmt(outer) + ",h=" + fmt(length) + ", $fn=200);\n"
                + "cylinder(r=" + fmt(inner) + ",h=" + fmt(length) + ", $fn=200);\n"
                + "}\n";
        write("centeringring-CNC" + n + ".scad", cnc);
        write("centeringring-3d" + n + ".scad", solid);
        writeStl(e, "centeringring-3d" + n + ".stl", RevolvedSolid.tubeProfile(inner, outer, length));
    }

    public void noseCone(Element e) {
        String shape = text(e, "shape");
        if (shape == null) {
            shape = "ogive";
        }
        String type = NOSE_CONE_TEMPLATES.get(shape);
        if (type == null) {
            warn(e, "nose cone shape '" + shape + "' is not supported, skipped");
            return;
        }
        double length = mm(e, "length");
        double aftRadius = OrkValues.noseConeAftRadius(e);
        // "filled" = solid nose cone, written as a wall thickness of 0
        boolean filled = "filled".equals(text(e, "thickness"));
        double thickness = filled ? 0.0 : mm(e, "thickness");
        double shoulderRadius = OrkValues.noseConeShoulderRadius(e);
        double shoulderLength = mmOr(e, "aftshoulderlength", 0.0);
        double shoulderThickness = mmOr(e, "aftshoulderthickness", 0.0);
        boolean capped = "true".equals(text(e, "aftshouldercapped"));
        double param = parseOr(text(e, "shapeparameter"), Double.NaN);
        if (!require(e, "length", length) || !require(e, "aftradius", aftRadius)
                || !require(e, "thickness", thickness) || !require(e, "aftshoulderradius", shoulderRadius)
                || !require(e, "aftshoulderlength", shoulderLength)
                || !require(e, "aftshoulderthickness", shoulderThickness)) {
            return;
        }
        if (length <= 0 || aftRadius <= 0) {
            warn(e, "length and aft radius must be positive, skipped");
            return;
        }
        NoseConeShape profile = new NoseConeShape(shape, length, aftRadius, param);

        String content;
        try {
            content = FileUtils.loadTemplate("noseCone.scad");
        } catch (IOException ex) {
            report.error(ex.getMessage());
            return;
        }
        content = fill(content, "##shape##", shape);
        content = fill(content, "##aftradius##", fmt(aftRadius));
        content = fill(content, "##length##", fmt(length));
        content = fill(content, "##thickness##", fmt(thickness));
        content = fill(content, "##shapeparameter##", fmtParam(profile.parameter()));
        content = fill(content, "##aftshoulderradius##", fmt(shoulderRadius));
        content = fill(content, "##aftshoulderlength##", fmt(shoulderLength));
        content = fill(content, "##aftshoulderthickness##", fmt(shoulderThickness));
        content = fill(content, "##aftshouldercapped##", capped ? "true" : "false");
        write("noseCone-" + type + "3D.scad", content);

        // Same rounding as the .scad file, so both describe exactly the same part.
        writeStl(e, "noseCone-" + type + "3D.stl", RevolvedSolid.noseConeProfile(
                new NoseConeShape(shape, round2(length), round2(aftRadius), Double.parseDouble(fmtParam(profile.parameter()))),
                round2(length), round2(aftRadius), round2(thickness),
                round2(shoulderRadius), round2(shoulderLength), round2(shoulderThickness), capped));
    }

    public void freeformFinset(Element e) {
        int n = ++freeformfinsetCount;
        Element finpoints = OrkValues.child(e, "finpoints");
        List<Element> points = finpoints == null ? List.of() : elements(finpoints);
        if (points.size() < 3) {
            warn(e, "free form fin set needs at least 3 points, skipped");
            return;
        }
        StringBuilder sb = new StringBuilder("polygon(points=[");
        float firstX = 0f;
        for (int i = 0; i < points.size(); i++) {
            Element p = points.get(i);
            double x = mm(p.getAttribute("x"));
            double y = mm(p.getAttribute("y"));
            if (Double.isNaN(x) || Double.isNaN(y)) {
                warn(e, "invalid fin point #" + (i + 1) + ", skipped");
                return;
            }
            if (i == 0) {
                firstX = (float) x;
            }
            sb.append('[').append((float) x).append(',').append((float) y).append(']');
            if (i < points.size() - 1) {
                sb.append(',');
            }
        }
        sb.append("]);\n");

        Tab tab = Tab.read(e);
        if (tab == null) {
            return;
        }
        if (tab.centered) {
            sb.append("translate([").append(-firstX / 2 + tab.position + tab.length / 2).append(",0,0])");
        }
        sb.append("polygon(points=[[0,0],[0,-").append(tab.height).append("],[").append(tab.length)
                .append(",-").append(tab.height).append("],[").append(tab.length).append(",-0]]);\n");
        write("freeformfinset" + n + ".scad", sb.toString());
    }

    public void ellipticalFinset(Element e) {
        int n = ++ellipticalfinsetCount;
        float root = (float) mm(e, "rootchord");
        float height = (float) mm(e, "height");
        if (!require(e, "rootchord", root) || !require(e, "height", height)) {
            return;
        }
        if (root <= 0f) {
            warn(e, "root chord must be positive, skipped");
            return;
        }
        Tab tab = Tab.read(e);
        if (tab == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("difference() {\n");
        sb.append("scale([1,").append(2 * height / root).append("])circle(d=").append(root).append(",$fn=100);\n");
        sb.append("translate([0,").append(height / 2).append(",0])square([").append(root).append(',').append(height)
                .append("],center =true);\n");
        sb.append("}\n");
        if (tab.centered) {
            sb.append("translate([").append(-root / 2 + tab.position + tab.length / 2).append(",0,0])");
        }
        sb.append("polygon(points=[[0,0],[0,").append(tab.height).append("],[").append(tab.length).append(',')
                .append(tab.height).append("],[").append(tab.length).append(",0]]);\n");
        write("ellipticalfinset" + n + ".scad", sb.toString());
    }

    public void trapezoidFinset(Element e) {
        int n = ++trapezoidfinsetCount;
        float root = (float) mm(e, "rootchord");
        float tip = (float) mm(e, "tipchord");
        float sweep = (float) mm(e, "sweeplength");
        float height = (float) mm(e, "height");
        if (!require(e, "rootchord", root) || !require(e, "tipchord", tip) || !require(e, "sweeplength", sweep)
                || !require(e, "height", height)) {
            return;
        }
        Tab tab = Tab.read(e);
        if (tab == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("polygon(points=[[0,0],[").append(root).append(",0],[").append(tip + sweep).append(',')
                .append(height).append("],[").append(sweep).append(',').append(height).append("]]);\n");
        if (tab.centered) {
            sb.append("translate([").append(root / 2 - tab.length / 2 - tab.position).append(",0,0])");
        }
        float p = tab.position;
        sb.append("polygon(points=[[").append(p).append(",0],[").append(p).append(",-").append(tab.height)
                .append("],[").append(tab.length + p).append(",-").append(tab.height).append("],[")
                .append(tab.length + p).append(",-0]]);\n");
        write("trapezoidfinset" + n + ".scad", sb.toString());
    }

    // ------------------------------------------------------------------ helpers

    /** Fin tab (optional in OpenRocket: absent values mean "no tab"). */
    private static final class Tab {
        float height;
        float length;
        float position;
        boolean centered;

        /** Returns null (after the caller is warned through NaN checks) only if a value is present but invalid. */
        static Tab read(Element finset) {
            Tab t = new Tab();
            double h = mmOr(finset, "tabheight", 0.0);
            double l = mmOr(finset, "tablength", 0.0);
            double p = mmOr(finset, "tabposition", 0.0);
            if (Double.isNaN(h) || Double.isNaN(l) || Double.isNaN(p)) {
                return null;
            }
            t.height = (float) h;
            t.length = (float) l;
            t.position = (float) p;
            Element pos = OrkValues.child(finset, "tabposition");
            String rel = pos == null ? "" : pos.getAttribute("relativeto"); // "" when attribute is absent
            t.centered = rel.equals("center") || rel.equals("middle");
            return t;
        }
    }

    private static String tube(double r, double thickness, double length) {
        return "difference() {\n"
                + "cylinder(r=" + fmt(r) + ", h=" + fmt(length) + ", $fn=200);\n"
                + "cylinder(r=" + fmt(r - thickness) + ", h=" + fmt(length) + ", $fn=200);\n"
                + "}";
    }

    /** Shape parameters are small dimensionless numbers: keep more decimals than for lengths. */
    private static String fmtParam(double p) {
        return new java.text.DecimalFormat("0.0####", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT))
                .format(p);
    }

    private static double round2(double v) {
        return Double.parseDouble(fmt(v));
    }

    private static double parseOr(String raw, double def) {
        if (raw == null) {
            return def;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** Writes the STL version of a part (if enabled). Errors are reported, never thrown. */
    private void writeStl(Element e, String fileName, java.awt.geom.Area profile) {
        if (!stl) {
            return;
        }
        Path file = outDir.resolve(fileName);
        try {
            RevolvedSolid.writeStl(profile, file, fileName);
            report.generated(file);
        } catch (IOException | RuntimeException ex) {
            report.error("Cannot write " + file + ": " + ex.getMessage());
        }
    }

    private static String fill(String template, String placeholder, String value) {
        return template.replace(placeholder, value); // literal replacement, no regex
    }

    private boolean require(Element e, String what, double value) {
        if (Double.isNaN(value)) {
            String raw = text(e, what);
            String reason = raw == null ? "is missing"
                    : OrkValues.isAuto(raw) ? "is 'auto' and could not be resolved" : "is invalid ('" + raw + "')";
            warn(e, what + " " + reason + ", skipped");
            return false;
        }
        return true;
    }

    private void warn(Element e, String message) {
        String name = text(e, "name");
        report.warn("<" + e.getNodeName() + ">" + (name == null ? "" : " \"" + name + "\"") + ": " + message);
    }

    private void write(String fileName, String content) {
        Path file = outDir.resolve(fileName);
        try {
            FileUtils.writeFile(file, content);
            report.generated(file);
        } catch (IOException ex) {
            report.error("Cannot write " + file + ": " + ex.getMessage());
        }
    }
}
