package orkToScad;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Minimal 3D viewer for an {@link StlMesh}: software z-buffer rendering with Java2D only (no library).
 *
 * Mouse: left-drag rotates, right/middle-drag pans, wheel zooms, double-click resets the view.
 * The rocket axis (Z) is displayed vertically. In cutaway mode the half of the part facing the viewer
 * (at the default angle) is removed to show wall thicknesses. Surfaces facing the part's axis
 * (inside of tubes and hollow nose cones) are drawn in blue, outer surfaces in orange.
 */
public final class StlViewPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final double DEFAULT_YAW = Math.toRadians(-30);
    private static final double DEFAULT_PITCH = Math.toRadians(20);

    private static final int[] OUTER = { 235, 145, 45 };
    private static final int[] INNER = { 95, 125, 175 };

    private transient StlMesh mesh;
    private String title = "";
    private String message = "No part selected";
    private boolean cutaway;

    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double zoom = 1.0;
    private double panX;
    private double panY;

    private transient BufferedImage image;
    private transient float[] zbuf;

    public StlViewPanel() {
        setPreferredSize(new java.awt.Dimension(800, 600));
        MouseAdapter mouse = new MouseAdapter() {
            private int lastX;
            private int lastY;

            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastX;
                int dy = e.getY() - lastY;
                lastX = e.getX();
                lastY = e.getY();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    yaw += dx * 0.01;
                    pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, pitch + dy * 0.01));
                } else {
                    panX += dx;
                    panY += dy;
                }
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom = Math.max(0.1, Math.min(50, zoom * Math.pow(1.15, -e.getPreciseWheelRotation())));
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    resetView();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    /** Shows a mesh (null to show only {@code message}). */
    public void setMesh(StlMesh mesh, String title, String message) {
        this.mesh = mesh;
        this.title = title == null ? "" : title;
        this.message = message;
        resetView();
    }

    public void setCutaway(boolean cutaway) {
        this.cutaway = cutaway;
        repaint();
    }

    public void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        g.setPaint(new GradientPaint(0, 0, new Color(250, 250, 252), 0, h, new Color(205, 210, 220)));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (mesh == null) {
            g.setColor(Color.DARK_GRAY);
            g.setFont(getFont().deriveFont(Font.PLAIN, 14f));
            g.drawString(message == null ? "" : message, 20, 30);
            return;
        }

        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            zbuf = new float[w * h];
        }
        Arrays.fill(((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData(), 0);
        Arrays.fill(zbuf, Float.MAX_VALUE);
        render(w, h);
        g.drawImage(image, 0, 0, null);
        drawOverlay(g, w, h);
    }

    private void render(int w, int h) {
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
        float[] v = mesh.vertices;
        double cx = (mesh.min[0] + mesh.max[0]) / 2;
        double cy = (mesh.min[1] + mesh.max[1]) / 2;
        double cz = (mesh.min[2] + mesh.max[2]) / 2;
        double diag = Math.sqrt(sq(mesh.size(0)) + sq(mesh.size(1)) + sq(mesh.size(2)));
        double scale = 0.9 * Math.min(w, h) / Math.max(diag, 1e-6) * zoom;
        double ox = w / 2.0 + panX;
        double oy = h / 2.0 + panY;

        double cyw = Math.cos(yaw), syw = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        // Light coming from the viewer's side, upper left (view space: x right, d depth, u up).
        double lx = -0.35, ld = -0.8, lu = 0.5;
        double ll = Math.sqrt(lx * lx + ld * ld + lu * lu);
        lx /= ll;
        ld /= ll;
        lu /= ll;

        double[] sx = new double[3], sy = new double[3], sd = new double[3];
        for (int t = 0; t < v.length; t += 9) {
            if (cutaway && v[t + 1] < cy && v[t + 4] < cy && v[t + 7] < cy) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                double x = v[t + 3 * k] - cx, y = v[t + 3 * k + 1] - cy, z = v[t + 3 * k + 2] - cz;
                double x1 = x * cyw - y * syw;
                double y1 = x * syw + y * cyw;
                double depth = y1 * cp - z * sp;
                double up = y1 * sp + z * cp;
                sx[k] = ox + x1 * scale;
                sy[k] = oy - up * scale;
                sd[k] = depth;
            }
            // Face normal in model space (right-hand rule, outward for a correct STL), then same rotation.
            double ux = v[t + 3] - v[t], uy = v[t + 4] - v[t + 1], uz = v[t + 5] - v[t + 2];
            double wx = v[t + 6] - v[t], wy = v[t + 7] - v[t + 1], wz = v[t + 8] - v[t + 2];
            double mx = uy * wz - uz * wy, my = uz * wx - ux * wz, mz = ux * wy - uy * wx;
            double ny1 = mx * syw + my * cyw;
            double nx = mx * cyw - my * syw;
            double nd = ny1 * cp - mz * sp;
            double nu = ny1 * sp + mz * cp;
            double nl = Math.sqrt(nx * nx + nd * nd + nu * nu);
            if (nl == 0) {
                continue;
            }
            nx /= nl;
            nd /= nl;
            nu /= nl;
            if (nd > 0) { // two-sided lighting: light the side we are looking at
                nx = -nx;
                nd = -nd;
                nu = -nu;
            }
            double diffuse = Math.max(0, nx * lx + nd * ld + nu * lu);
            double intensity = 0.28 + 0.72 * diffuse;
            // Inner surfaces (normal pointing towards the Z axis of the part) in blue, outer ones in orange.
            double rx = (v[t] + v[t + 3] + v[t + 6]) / 3 - cx;
            double ry = (v[t + 1] + v[t + 4] + v[t + 7]) / 3 - cy;
            double rl = Math.sqrt(rx * rx + ry * ry);
            double ml = Math.sqrt(mx * mx + my * my + mz * mz);
            boolean inner = rl > 1e-6 && (mx * rx + my * ry) / (rl * ml) < -0.2;
            int[] base = inner ? INNER : OUTER;
            int rgb = 0xFF000000 | (shade(base[0], intensity) << 16) | (shade(base[1], intensity) << 8)
                    | shade(base[2], intensity);
            raster(pixels, w, h, sx, sy, sd, rgb);
        }
    }

    private void raster(int[] pixels, int w, int h, double[] x, double[] y, double[] d, int rgb) {
        double area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0]);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x[0], Math.min(x[1], x[2]))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x[0], Math.max(x[1], x[2]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y[0], Math.min(y[1], y[2]))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y[0], Math.max(y[1], y[2]))));
        double inv = 1.0 / area;
        for (int py = minY; py <= maxY; py++) {
            double fy = py + 0.5;
            for (int px = minX; px <= maxX; px++) {
                double fx = px + 0.5;
                double w0 = ((x[1] - fx) * (y[2] - fy) - (x[2] - fx) * (y[1] - fy)) * inv;
                double w1 = ((x[2] - fx) * (y[0] - fy) - (x[0] - fx) * (y[2] - fy)) * inv;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) {
                    continue;
                }
                float depth = (float) (w0 * d[0] + w1 * d[1] + w2 * d[2]);
                int i = py * w + px;
                if (depth < zbuf[i]) {
                    zbuf[i] = depth;
                    pixels[i] = rgb;
                }
            }
        }
    }

    private void drawOverlay(Graphics2D g, int w, int h) {
        g.setColor(new Color(40, 40, 40));
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.drawString(title, 12, 22);
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g.drawString(String.format(Locale.ROOT, "Size: %.2f x %.2f x %.2f mm (X x Y x Z)   %,d triangles",
                mesh.size(0), mesh.size(1), mesh.size(2), mesh.triangleCount()), 12, 40);
        g.setColor(new Color(70, 70, 70));
        g.drawString("Left-drag: rotate   Wheel: zoom   Right-drag: pan   Double-click: reset view", 12, h - 12);
    }

    private static int shade(int c, double intensity) {
        return Math.min(255, (int) Math.round(c * intensity));
    }

    private static double sq(double v) {
        return v * v;
    }
}
