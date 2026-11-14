package orkToScad;

import static orkToScad.FileUtils.fmt;
import static orkToScad.OrkValues.elements;
import static orkToScad.OrkValues.mm;
import static orkToScad.OrkValues.mmOr;
import static orkToScad.OrkValues.text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

/**
 * Generates one or more .scad files per OpenRocket component and, optionally, STL files for the
 * parts that are printed in 3D (nose cones, transitions, tubes, couplers, rings, engine blocks,
 * launch lugs, rail buttons, tube fins).
 *
 * Nose cones and transitions use the templates {@code openscad/noseCone.scad} and
 * {@code openscad/transition.scad} (rotate_extrude, OpenRocket formulas). Notes:
 * <ul>
 * <li>numbers are always written with '.' as decimal separator;</li>
 * <li>"auto" dimensions are resolved (see {@link OrkValues});</li>
 * <li>a component whose required dimensions are missing or invalid is skipped with a warning
 *     instead of being written with zeros;</li>
 * <li>I/O errors are recorded in the {@link ConversionReport} instead of being printed and ignored.</li>
 * </ul>
 */
public final class ScadGenerator {

    private final Path outDir;
    private final ConversionReport report;
    private final boolean stl;
    private final boolean svg;

    private int bulkheadCount;
    private int tubeCouplerCount;
    private int bodyTubeCount;
    private int innerTubeCount;
    private int freeformfinsetCount;
    private int ellipticalfinsetCount;
    private int trapezoidfinsetCount;
    private int centeringringCount;
    private int transitionCount;
    private int launchLugCount;
    private int railButtonCount;
    private int engineBlockCount;
    private int tubeFinCount;
    /** Nose cones per output file name, so that two nose cones of the same shape do not overwrite each other. */
    private final Map<String, Integer> noseConeNames = new HashMap<>();

    /**
     * @param stl also write a ready-to-print STL file next to the .scad file for the 3D parts
     */
    public ScadGenerator(Path outDir, ConversionReport report, boolean stl) {
        this(outDir, report, stl, true);
    }

    /**
     * @param svg also write SVG cut outlines for the flat parts (fins with their tab, centering rings,
     *            bulkheads)
     */
    public ScadGenerator(Path outDir, ConversionReport report, boolean stl, boolean svg) {
        this.outDir = outDir;
        this.report = report;
        this.stl = stl;
        this.svg = svg;
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
        writeSvg(e, "bulkhead-cnc" + n + ".svg",
                file -> SvgWriter.writeCircles(file, title(e, "bulkhead-cnc" + n), round2(r)));
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
        writeStl(e, "tube-coupler-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
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
        writeStl(e, "body-tube-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
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
        writeStl(e, "inner-tube-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
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
            warn(e, new I18n.Msg("warn.ringRadii", fmt(inner), fmt(outer)));
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
        writeStl(e, "centeringring-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(inner), round2(outer), round2(length)));
        writeSvg(e, "centeringring-CNC" + n + ".svg",
                file -> SvgWriter.writeCircles(file, title(e, "centeringring-CNC" + n), round2(outer), round2(inner)));
    }

    public void noseCone(Element e) {
        String rawShape = text(e, "shape");
        String shape = rawShape == null ? "ogive" : OrShapes.canonical(rawShape);
        if (shape == null) {
            warn(e, new I18n.Msg("warn.shape", rawShape));
            return;
        }
        String type = OrShapes.fileSuffix(shape);
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
            warn(e, new I18n.Msg("warn.noseLength"));
            return;
        }
        NoseConeShape profile = new NoseConeShape(shape, length, aftRadius, param);

        String content;
        try {
            content = FileUtils.loadTemplate("noseCone.scad");
        } catch (IOException ex) {
            report.error(ex);
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
        // First nose cone of a shape keeps the historical name, the next ones get -2, -3...
        String base = "noseCone-" + type + "3D";
        int count = noseConeNames.merge(base, 1, Integer::sum);
        String name = count == 1 ? base : base + "-" + count;
        write(name + ".scad", content);

        // Same rounding as the .scad file, so both describe exactly the same part.
        NoseConeShape rounded = new NoseConeShape(shape, round2(length), round2(aftRadius),
                Double.parseDouble(fmtParam(profile.parameter())));
        writeStl(e, name + ".stl", RevolvedSolid.bodyProfile(rounded, round2(thickness), null,
                new RevolvedSolid.Shoulder(round2(shoulderRadius), round2(shoulderLength), round2(shoulderThickness),
                        capped)));
    }

    public void transition(Element e) {
        int n = ++transitionCount;
        String rawShape = text(e, "shape");
        String shape = rawShape == null ? "conical" : OrShapes.canonical(rawShape);
        if (shape == null) {
            warn(e, new I18n.Msg("warn.shape", rawShape));
            return;
        }
        double length = mm(e, "length");
        double[] radii = OrkValues.transitionRadii(e);
        boolean filled = "filled".equals(text(e, "thickness"));
        double thickness = filled ? 0.0 : mm(e, "thickness");
        boolean clipped = !"false".equals(text(e, "shapeclipped")); // OpenRocket's default is clipped
        double param = parseOr(text(e, "shapeparameter"), Double.NaN);
        double[] fore = shoulder(e, "fore");
        double[] aft = shoulder(e, "aft");
        if (!require(e, "length", length) || !require(e, "foreradius", radii[0]) || !require(e, "aftradius", radii[1])
                || !require(e, "thickness", thickness) || fore == null || aft == null) {
            return;
        }
        if (length <= 0 || (radii[0] <= 0 && radii[1] <= 0)) {
            warn(e, new I18n.Msg("warn.transitionLength"));
            return;
        }
        boolean foreCapped = "true".equals(text(e, "foreshouldercapped"));
        boolean aftCapped = "true".equals(text(e, "aftshouldercapped"));

        // Rounded values: the .scad file and the STL describe exactly the same part.
        TransitionShape profile = new TransitionShape(shape, round2(length), round2(radii[0]), round2(radii[1]),
                param, clipped);
        double p = Double.parseDouble(fmtParam(profile.parameter()));
        profile = new TransitionShape(shape, round2(length), round2(radii[0]), round2(radii[1]), p, clipped);

        String content;
        try {
            content = FileUtils.loadTemplate("transition.scad");
        } catch (IOException ex) {
            report.error(ex);
            return;
        }
        content = fill(content, "##shape##", shape);
        content = fill(content, "##length##", fmt(length));
        content = fill(content, "##foreradius##", fmt(radii[0]));
        content = fill(content, "##aftradius##", fmt(radii[1]));
        content = fill(content, "##thickness##", fmt(thickness));
        content = fill(content, "##shapeparameter##", fmtParam(p));
        content = fill(content, "##shapeclipped##", profile.isClipped() ? "true" : "false");
        content = fill(content, "##foreshoulderradius##", fmt(fore[0]));
        content = fill(content, "##foreshoulderlength##", fmt(fore[1]));
        content = fill(content, "##foreshoulderthickness##", fmt(fore[2]));
        content = fill(content, "##foreshouldercapped##", foreCapped ? "true" : "false");
        content = fill(content, "##aftshoulderradius##", fmt(aft[0]));
        content = fill(content, "##aftshoulderlength##", fmt(aft[1]));
        content = fill(content, "##aftshoulderthickness##", fmt(aft[2]));
        content = fill(content, "##aftshouldercapped##", aftCapped ? "true" : "false");
        write("transition-3d" + n + ".scad", content);

        writeStl(e, "transition-3d" + n + ".stl", RevolvedSolid.bodyProfile(profile, round2(thickness),
                new RevolvedSolid.Shoulder(round2(fore[0]), round2(fore[1]), round2(fore[2]), foreCapped),
                new RevolvedSolid.Shoulder(round2(aft[0]), round2(aft[1]), round2(aft[2]), aftCapped)));
    }

    /** {radius, length, thickness} of a transition shoulder (all 0 if absent), or null if invalid. */
    private double[] shoulder(Element e, String side) {
        double r = mmOr(e, side + "shoulderradius", 0.0);
        double l = mmOr(e, side + "shoulderlength", 0.0);
        double t = mmOr(e, side + "shoulderthickness", 0.0);
        if (!require(e, side + "shoulderradius", r) || !require(e, side + "shoulderlength", l)
                || !require(e, side + "shoulderthickness", t)) {
            return null;
        }
        return new double[] { r, l, t };
    }

    public void launchLug(Element e) {
        int n = ++launchLugCount;
        double r = mm(e, "radius");
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "radius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("launch-lug-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "launch-lug-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
    }

    public void engineBlock(Element e) {
        int n = ++engineBlockCount;
        double r = OrkValues.outerRadiusInsideParent(e, "outerradius");
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "outerradius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("engine-block-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "engine-block-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
    }

    /** One tube of a tube fin set (print it as many times as the fin count). */
    public void tubeFinSet(Element e) {
        int n = ++tubeFinCount;
        double r = OrkValues.tubeFinRadius(e);
        double length = mm(e, "length");
        double thickness = mm(e, "thickness");
        if (!require(e, "radius", r) || !require(e, "length", length) || !require(e, "thickness", thickness)) {
            return;
        }
        write("tube-fin-3d" + n + ".scad", tube(r, thickness, length));
        writeStl(e, "tube-fin-3d" + n + ".stl", RevolvedSolid.tubeProfile(round2(r - thickness), round2(r), round2(length)));
    }

    /**
     * Rail button standing on its base: base and flange of the outer diameter, waist of the inner
     * diameter. Default sizes are OpenRocket's when a value is missing (older files).
     */
    public void railButton(Element e) {
        int n = ++railButtonCount;
        double od = mmOr(e, "outerdiameter", 9.7);
        double id = mmOr(e, "innerdiameter", 8.0);
        double height = mmOr(e, "height", 9.7);
        double base = mmOr(e, "baseheight", 2.0);
        double flange = mmOr(e, "flangeheight", 2.0);
        if (!require(e, "outerdiameter", od) || !require(e, "innerdiameter", id) || !require(e, "height", height)
                || !require(e, "baseheight", base) || !require(e, "flangeheight", flange)) {
            return;
        }
        if (od <= 0 || height <= 0 || id > od) {
            warn(e, new I18n.Msg("warn.railButton"));
            return;
        }
        // Rounded values: the .scad file and the STL describe exactly the same part.
        double ro = round2(od), ri = round2(id), b = round2(base), f = round2(flange);
        double w = Math.max(0, round2(height) - b - f);
        StringBuilder scad = new StringBuilder();
        scad.append("cylinder(d=").append(fmt(ro)).append(", h=").append(fmt(b)).append(", $fn=200);\n");
        if (w > 0) {
            scad.append("translate([0,0,").append(fmt(b)).append("]) cylinder(d=").append(fmt(ri)).append(", h=")
                    .append(fmt(w)).append(", $fn=200);\n");
        }
        scad.append("translate([0,0,").append(fmt(b + w)).append("]) cylinder(d=").append(fmt(ro)).append(", h=")
                .append(fmt(f)).append(", $fn=200);\n");
        write("rail-button-3d" + n + ".scad", scad.toString());
        writeStl(e, "rail-button-3d" + n + ".stl", RevolvedSolid.railButtonProfile(ro, ri, b + w + f, b, f));
    }

    public void freeformFinset(Element e) {
        int n = ++freeformfinsetCount;
        Element finpoints = OrkValues.child(e, "finpoints");
        List<Element> points = finpoints == null ? List.of() : elements(finpoints);
        if (points.size() < 3) {
            warn(e, new I18n.Msg("warn.freeformPoints"));
            return;
        }
        List<double[]> pts = new java.util.ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            double x = mm(points.get(i).getAttribute("x"));
            double y = mm(points.get(i).getAttribute("y"));
            if (Double.isNaN(x) || Double.isNaN(y)) {
                warn(e, new I18n.Msg("warn.finPoint", i + 1));
                return;
            }
            pts.add(new double[] { x, y });
        }
        // The root goes from the first to the last point.
        double front = pts.get(0)[0];
        double finLength = pts.get(pts.size() - 1)[0] - front;
        FlatParts.Tab tab = readTab(e, finLength, front);
        if (tab == null) {
            return;
        }
        writeFin(e, "freeformfinset" + n, FlatParts.freeform(pts, tab));
    }

    public void ellipticalFinset(Element e) {
        int n = ++ellipticalfinsetCount;
        double root = mm(e, "rootchord");
        double height = mm(e, "height");
        if (!require(e, "rootchord", root) || !require(e, "height", height)) {
            return;
        }
        if (root <= 0 || height <= 0) {
            warn(e, new I18n.Msg("warn.finSize"));
            return;
        }
        FlatParts.Tab tab = readTab(e, root, 0);
        if (tab == null) {
            return;
        }
        writeFin(e, "ellipticalfinset" + n, FlatParts.elliptical(root, height, tab));
    }

    public void trapezoidFinset(Element e) {
        int n = ++trapezoidfinsetCount;
        double root = mm(e, "rootchord");
        double tip = mm(e, "tipchord");
        double sweep = mm(e, "sweeplength");
        double height = mm(e, "height");
        if (!require(e, "rootchord", root) || !require(e, "tipchord", tip) || !require(e, "sweeplength", sweep)
                || !require(e, "height", height)) {
            return;
        }
        FlatParts.Tab tab = readTab(e, root, 0);
        if (tab == null) {
            return;
        }
        writeFin(e, "trapezoidfinset" + n, FlatParts.trapezoid(root, tip, sweep, height, tab));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Fin tab, positioned like OpenRocket (see {@link FlatParts#tabFront}). Absent values mean "no tab".
     * Returns null (with a warning) if a value is present but invalid.
     *
     * @param finLength root chord length
     * @param rootFront x of the fin's leading edge at the root
     */
    private FlatParts.Tab readTab(Element finset, double finLength, double rootFront) {
        double h = mmOr(finset, "tabheight", 0.0);
        double l = mmOr(finset, "tablength", 0.0);
        double offset = mmOr(finset, "tabposition", 0.0);
        if (!require(finset, "tabheight", h) || !require(finset, "tablength", l)
                || !require(finset, "tabposition", offset)) {
            return null;
        }
        Element pos = OrkValues.child(finset, "tabposition");
        String rel = pos == null ? "" : pos.getAttribute("relativeto");
        return new FlatParts.Tab(rootFront + FlatParts.tabFront(rel, offset, l, finLength), l, h);
    }

    /** One fin: 2D .scad (fin polygon + tab) and SVG cut outline. */
    private void writeFin(Element e, String baseName, FlatParts.Fin fin) {
        StringBuilder sb = new StringBuilder("// One fin seen from the side, in mm: root chord on y = 0, tab below.\n");
        sb.append("polygon(points=[");
        for (int i = 0; i < fin.outline.size(); i++) {
            double[] p = fin.outline.get(i);
            sb.append('[').append(fmt4(p[0])).append(',').append(fmt4(p[1])).append(']');
            if (i < fin.outline.size() - 1) {
                sb.append(',');
            }
        }
        sb.append("]);\n");
        if (fin.tab.present()) {
            sb.append("translate([").append(fmt4(fin.tab.front)).append(',').append(fmt4(-fin.tab.height))
                    .append("]) square([").append(fmt4(fin.tab.length)).append(',').append(fmt4(fin.tab.height))
                    .append("]);\n");
        }
        write(baseName + ".scad", sb.toString());
        writeSvg(e, baseName + ".svg", file -> SvgWriter.writeOutline(file, title(e, baseName), fin.area()));
    }

    /** Lengths for 2D outlines: up to 4 decimals, '.' as separator, no exponent. */
    private static String fmt4(double v) {
        String s = new java.text.DecimalFormat("0.####", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT))
                .format(v);
        return s.equals("-0") ? "0" : s;
    }

    private static String title(Element e, String baseName) {
        String name = text(e, "name");
        return name == null ? baseName : name + " (" + baseName + ")";
    }

    /** Something that writes one SVG file. */
    private interface SvgJob {
        void run(Path file) throws IOException;
    }

    /** Writes an SVG file (if enabled). Errors are reported, never thrown. */
    private void writeSvg(Element e, String fileName, SvgJob job) {
        if (!svg) {
            return;
        }
        Path file = outDir.resolve(fileName);
        try {
            job.run(file);
            report.generated(file);
        } catch (IOException | RuntimeException ex) {
            report.error(new I18n.Msg("err.write", file, ex.getMessage()));
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
            report.error(new I18n.Msg("err.write", file, ex.getMessage()));
        }
    }

    private static String fill(String template, String placeholder, String value) {
        return template.replace(placeholder, value); // literal replacement, no regex
    }

    private boolean require(Element e, String what, double value) {
        if (Double.isNaN(value)) {
            String raw = text(e, what);
            warn(e, raw == null ? new I18n.Msg("warn.missing", what)
                    : OrkValues.isAuto(raw) ? new I18n.Msg("warn.auto", what) : new I18n.Msg("warn.invalid", what, raw));
            return false;
        }
        return true;
    }

    /** "Centering ring "Ring"": the kind of component (translated) and its OpenRocket name. */
    static I18n.Msg partRef(Element e) {
        String name = text(e, "name");
        I18n.Msg kind = new I18n.Msg("kind." + e.getNodeName());
        return name == null || name.isBlank() ? new I18n.Msg("part.refNoName", kind) : new I18n.Msg("part.ref", kind, name);
    }

    private void warn(Element e, I18n.Msg message) {
        report.warn(new I18n.Msg("warn.format", partRef(e), message));
    }

    private void write(String fileName, String content) {
        Path file = outDir.resolve(fileName);
        try {
            FileUtils.writeFile(file, content);
            report.generated(file);
        } catch (IOException ex) {
            report.error(new I18n.Msg("err.write", file, ex.getMessage()));
        }
    }
}
