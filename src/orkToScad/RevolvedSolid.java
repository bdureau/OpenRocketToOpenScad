package orkToScad;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds solids of revolution and writes them as binary STL, without OpenSCAD.
 *
 * Every part handled here (tubes, rings, couplers, nose cones, transitions, rail buttons) is a 2D half-profile turned around
 * the Z axis. The profile is built with {@link Area} boolean operations (x = radius, y = height,
 * in mm), then revolved with the same smoothness as the .scad files ($fn = 200).
 */
public final class RevolvedSolid {

    /** Segments around the axis, same as $fn in the generated .scad files. */
    static final int SEGMENTS = 200;

    /** Max deviation (mm) when flattening the arcs created by the wall offset. */
    private static final double FLATNESS = 0.01;

    private RevolvedSolid() {
    }

    // ------------------------------------------------------------------ profiles

    /** Tube or ring: radii ri..ro, from z = 0 to z = length. ri = 0 gives a solid cylinder. */
    public static Area tubeProfile(double innerRadius, double outerRadius, double length) {
        double ri = Math.max(0, innerRadius);
        return new Area(new Rectangle2D.Double(ri, 0, outerRadius - ri, length));
    }

    /** Shoulder of a nose cone or transition (a short tube that slides into the body tube). */
    public static final class Shoulder {
        final double radius;
        final double length;
        final double thickness;
        final boolean capped;

        public Shoulder(double radius, double length, double thickness, boolean capped) {
            this.radius = radius;
            this.length = length;
            this.thickness = thickness;
            this.capped = capped;
        }

        boolean present() {
            return radius > 0 && length > 0 && thickness > 0;
        }
    }

    /**
     * Half-profile of a nose cone or transition: aft end at z = 0, fore end at z = length,
     * aft shoulder below z = 0, fore shoulder above z = length.
     * Same construction as {@code openscad/noseCone.scad} and {@code openscad/transition.scad}.
     *
     * @param wall wall thickness measured perpendicular to the surface; 0 = solid part
     * @param fore fore shoulder, or null
     * @param aft  aft shoulder, or null
     */
    public static Area bodyProfile(BodyShape shape, double wall, Shoulder fore, Shoulder aft) {
        double length = shape.length();
        double rf = shape.foreRadius();
        double ra = shape.aftRadius();
        int steps = BodyShape.STEPS;

        // Full silhouette on both sides of the axis. It is extended beyond the open ends so that the
        // wall offset leaves them open (as in the templates).
        Path2D.Double sil = new Path2D.Double();
        if (rf > 0) {
            sil.moveTo(rf, length + wall + 1);
            sil.lineTo(shape.radius(shape.xAt(0)), length - shape.xAt(0));
        } else {
            sil.moveTo(shape.radius(shape.xAt(0)), length - shape.xAt(0));
        }
        for (int i = 1; i <= steps; i++) {
            double x = shape.xAt(i);
            sil.lineTo(shape.radius(x), length - x);
        }
        sil.lineTo(ra, -wall - 1);
        sil.lineTo(-ra, -wall - 1);
        for (int i = steps; i >= 1; i--) {
            double x = shape.xAt(i);
            sil.lineTo(-shape.radius(x), length - x);
        }
        if (rf > 0) {
            sil.lineTo(-shape.radius(shape.xAt(0)), length - shape.xAt(0));
            sil.lineTo(-rf, length + wall + 1);
        }
        sil.closePath();
        Area solid = new Area(sil);

        // Hollow part: keep what is within 'wall' of the outline, i.e. solid minus offset(r = -wall).
        Area body = new Area(solid);
        if (wall > 0) {
            BasicStroke band = new BasicStroke((float) (2 * wall), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            body.intersect(new Area(band.createStrokedShape(sil)));
        }
        body.intersect(new Area(new Rectangle2D.Double(0, 0, shape.maxRadius() + 1, length)));

        if (aft != null && aft.present()) {
            double rIn = Math.max(0, aft.radius - aft.thickness);
            body.add(new Area(new Rectangle2D.Double(rIn, -aft.length, Math.min(aft.thickness, aft.radius), aft.length)));
            if (aft.capped) {
                body.add(new Area(new Rectangle2D.Double(0, -aft.length, aft.radius, Math.min(aft.thickness, aft.length))));
            }
            if (wall > 0) { // joint between the shoulder and the wall
                Area joint = new Area(new Rectangle2D.Double(rIn, 0, ra, Math.min(aft.thickness, length)));
                joint.intersect(solid);
                body.add(joint);
            }
        }
        if (fore != null && fore.present()) {
            double rIn = Math.max(0, fore.radius - fore.thickness);
            double t = Math.min(fore.thickness, fore.length);
            body.add(new Area(new Rectangle2D.Double(rIn, length, Math.min(fore.thickness, fore.radius), fore.length)));
            if (fore.capped) {
                body.add(new Area(new Rectangle2D.Double(0, length + fore.length - t, fore.radius, t)));
            }
            if (wall > 0) {
                double h = Math.min(fore.thickness, length);
                Area joint = new Area(new Rectangle2D.Double(rIn, length - h, rf, h));
                joint.intersect(solid);
                body.add(joint);
            }
        }
        return body;
    }

    /**
     * Rail button seen from the side, standing on its base: base (outer diameter), waist (inner
     * diameter), flange (outer diameter). The screw is not modelled.
     */
    public static Area railButtonProfile(double outerDiameter, double innerDiameter, double totalHeight,
            double baseHeight, double flangeHeight) {
        double ro = outerDiameter / 2;
        double ri = innerDiameter / 2;
        double waist = totalHeight - baseHeight - flangeHeight;
        Area a = new Area(new Rectangle2D.Double(0, 0, ri, totalHeight));
        a.add(new Area(new Rectangle2D.Double(0, 0, ro, baseHeight)));
        a.add(new Area(new Rectangle2D.Double(0, baseHeight + Math.max(0, waist), ro, flangeHeight)));
        return a;
    }

    // ------------------------------------------------------------------ STL

    /** Revolves the profile around the Z axis and writes a binary STL file (units: mm). */
    public static void writeStl(Area profile, Path file, String solidName) throws IOException {
        List<float[]> triangles = revolve(loops(profile));
        if (triangles.isEmpty()) {
            throw new IOException("empty geometry");
        }
        // Make normals point outwards: the enclosed volume must be positive.
        if (signedVolume(triangles) < 0) {
            for (float[] t : triangles) {
                for (int k = 0; k < 3; k++) {
                    float tmp = t[3 + k];
                    t[3 + k] = t[6 + k];
                    t[6 + k] = tmp;
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            byte[] header = new byte[80];
            byte[] name = ("ORKtoSCAD " + solidName).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, header, 0, Math.min(name.length, 80));
            out.write(header);
            buf.putInt(0, triangles.size());
            out.write(buf.array(), 0, 4);
            for (float[] t : triangles) {
                buf.clear();
                float[] n = normal(t);
                buf.putFloat(n[0]).putFloat(n[1]).putFloat(n[2]);
                for (int k = 0; k < 9; k++) {
                    buf.putFloat(t[k]);
                }
                buf.putShort((short) 0);
                out.write(buf.array(), 0, 50);
            }
        }
    }

    /** Closed outlines of the profile (outer boundaries and holes), as (x, y) point lists. */
    private static List<List<double[]>> loops(Area area) {
        List<List<double[]>> loops = new ArrayList<>();
        List<double[]> current = null;
        double[] c = new double[6];
        for (PathIterator it = area.getPathIterator(null, FLATNESS); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO:
                    current = new ArrayList<>();
                    current.add(new double[] { Math.max(0, c[0]), c[1] });
                    break;
                case PathIterator.SEG_LINETO: {
                    double[] p = { Math.max(0, c[0]), c[1] };
                    double[] last = current.get(current.size() - 1);
                    if (Math.abs(p[0] - last[0]) > 1e-9 || Math.abs(p[1] - last[1]) > 1e-9) {
                        current.add(p);
                    }
                    break;
                }
                case PathIterator.SEG_CLOSE:
                    if (current != null && current.size() >= 3) {
                        loops.add(current);
                    }
                    current = null;
                    break;
                default:
                    break; // no curves after flattening
            }
        }
        return loops;
    }

    /** Turns each edge of each loop into a band of triangles around the Z axis. */
    private static List<float[]> revolve(List<List<double[]>> loops) {
        double[] cos = new double[SEGMENTS + 1];
        double[] sin = new double[SEGMENTS + 1];
        for (int k = 0; k <= SEGMENTS; k++) {
            double a = 2 * Math.PI * (k % SEGMENTS) / SEGMENTS;
            cos[k] = Math.cos(a);
            sin[k] = Math.sin(a);
        }
        final double onAxis = 1e-9;
        List<float[]> tris = new ArrayList<>();
        for (List<double[]> loop : loops) {
            int n = loop.size();
            for (int i = 0; i < n; i++) {
                double[] p = loop.get(i);
                double[] q = loop.get((i + 1) % n);
                boolean pAxis = p[0] <= onAxis;
                boolean qAxis = q[0] <= onAxis;
                if (pAxis && qAxis) {
                    continue; // edge lying on the axis: no surface
                }
                for (int k = 0; k < SEGMENTS; k++) {
                    float[] p0 = point(p, cos[k], sin[k]);
                    float[] p1 = point(p, cos[k + 1], sin[k + 1]);
                    float[] q0 = point(q, cos[k], sin[k]);
                    float[] q1 = point(q, cos[k + 1], sin[k + 1]);
                    if (!qAxis) {
                        tris.add(tri(p0, q0, q1));
                    }
                    if (!pAxis) {
                        tris.add(tri(p0, qAxis ? q0 : q1, p1));
                    }
                }
            }
        }
        return tris;
    }

    private static float[] point(double[] rz, double cos, double sin) {
        return new float[] { (float) (rz[0] * cos), (float) (rz[0] * sin), (float) rz[1] };
    }

    private static float[] tri(float[] a, float[] b, float[] c) {
        return new float[] { a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2] };
    }

    private static double signedVolume(List<float[]> tris) {
        double v = 0;
        for (float[] t : tris) {
            v += t[0] * ((double) t[4] * t[8] - (double) t[5] * t[7])
                    - t[1] * ((double) t[3] * t[8] - (double) t[5] * t[6])
                    + t[2] * ((double) t[3] * t[7] - (double) t[4] * t[6]);
        }
        return v / 6;
    }

    private static float[] normal(float[] t) {
        double ux = t[3] - t[0], uy = t[4] - t[1], uz = t[5] - t[2];
        double vx = t[6] - t[0], vy = t[7] - t[1], vz = t[8] - t[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len == 0 ? new float[3] : new float[] { (float) (nx / len), (float) (ny / len), (float) (nz / len) };
    }
}
