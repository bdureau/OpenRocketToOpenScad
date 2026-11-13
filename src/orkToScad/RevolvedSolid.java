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
 * Every part handled here (tubes, rings, couplers, nose cones) is a 2D half-profile turned around
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

    /**
     * Nose cone half-profile: base at z = 0, tip at z = length, shoulder below z = 0.
     * Same construction as {@code openscad/noseCone.scad}.
     *
     * @param wall wall thickness measured perpendicular to the surface; 0 = solid nose cone
     */
    public static Area noseConeProfile(NoseConeShape shape, double length, double radius, double wall,
            double shoulderRadius, double shoulderLength, double shoulderThickness, boolean capped) {

        // Full silhouette on both sides of the axis, extended below the base (as in the template).
        Path2D.Double sil = new Path2D.Double();
        sil.moveTo(shape.radius(shape.xAt(0)), length - shape.xAt(0));
        for (int i = 1; i <= NoseConeShape.STEPS; i++) {
            double x = shape.xAt(i);
            sil.lineTo(shape.radius(x), length - x);
        }
        sil.lineTo(radius, -wall - 1);
        sil.lineTo(-radius, -wall - 1);
        for (int i = NoseConeShape.STEPS; i >= 1; i--) {
            double x = shape.xAt(i);
            sil.lineTo(-shape.radius(x), length - x);
        }
        sil.closePath();
        Area solid = new Area(sil);

        // Hollow cone: keep what is within 'wall' of the outline, i.e. solid minus offset(r = -wall).
        Area cone = new Area(solid);
        if (wall > 0) {
            BasicStroke band = new BasicStroke((float) (2 * wall), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            cone.intersect(new Area(band.createStrokedShape(sil)));
        }
        cone.intersect(new Area(new Rectangle2D.Double(0, 0, radius + 1, length)));

        if (shoulderRadius > 0 && shoulderLength > 0 && shoulderThickness > 0) {
            double rsIn = Math.max(0, shoulderRadius - shoulderThickness);
            cone.add(new Area(new Rectangle2D.Double(rsIn, -shoulderLength,
                    Math.min(shoulderThickness, shoulderRadius), shoulderLength)));
            if (capped) {
                cone.add(new Area(new Rectangle2D.Double(0, -shoulderLength, shoulderRadius,
                        Math.min(shoulderThickness, shoulderLength))));
            }
            if (wall > 0) { // joint between the shoulder and the cone wall
                Area joint = new Area(new Rectangle2D.Double(rsIn, 0, radius, Math.min(shoulderThickness, length)));
                joint.intersect(solid);
                cone.add(joint);
            }
        }
        return cone;
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
