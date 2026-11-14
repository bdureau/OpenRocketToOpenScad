package orkToScad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * 2D preview of an SVG cut outline: the part at its real proportions on a millimetre grid, with its
 * overall dimensions (diameters for rings), a 10 mm scale bar, zoom (wheel) and pan (drag).
 *
 * It reads the SVG files written by {@link SvgWriter}: {@code <path d="M L H V Z">} and
 * {@code <circle>} elements, coordinates in millimetres.
 */
final class SvgViewPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Shapes of an SVG file, in millimetres (SVG axes: y down). */
    static final class Drawing {
        final List<Shape> outlines = new ArrayList<>();
        final List<Ellipse2D> circles = new ArrayList<>();

        Rectangle2D bounds() {
            Rectangle2D b = null;
            for (Shape s : outlines) {
                b = b == null ? s.getBounds2D() : b.createUnion(s.getBounds2D());
            }
            for (Ellipse2D c : circles) {
                b = b == null ? c.getBounds2D() : b.createUnion(c.getBounds2D());
            }
            return b == null ? new Rectangle2D.Double() : b;
        }
    }

    private static final Pattern PATH = Pattern.compile("<path\\b[^>]*?\\sd=\"([^\"]*)\"");
    private static final Pattern CIRCLE = Pattern.compile("<circle\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("\\b(cx|cy|r)=\"([-0-9.eE]+)\"");
    private static final Pattern TOKEN = Pattern.compile("[MLHVZmlhvz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    static Drawing load(Path file) throws IOException {
        String svg = Files.readString(file, StandardCharsets.UTF_8);
        Drawing d = new Drawing();
        Matcher m = PATH.matcher(svg);
        while (m.find()) {
            d.outlines.add(parsePath(m.group(1)));
        }
        m = CIRCLE.matcher(svg);
        while (m.find()) {
            double cx = 0, cy = 0, r = -1;
            Matcher a = ATTR.matcher(m.group(1));
            while (a.find()) {
                double v = Double.parseDouble(a.group(2));
                switch (a.group(1)) {
                    case "cx":
                        cx = v;
                        break;
                    case "cy":
                        cy = v;
                        break;
                    default:
                        r = v;
                        break;
                }
            }
            if (r > 0) {
                d.circles.add(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }
        }
        if (d.outlines.isEmpty() && d.circles.isEmpty()) {
            throw new I18n.LocalizedIOException(new I18n.Msg("svg.noShape", file.getFileName()));
        }
        return d;
    }

    /** Absolute and relative M, L, H, V, Z commands (what ORKtoSCAD writes). */
    private static Path2D parsePath(String data) throws IOException {
        Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        Matcher t = TOKEN.matcher(data);
        List<String> tokens = new ArrayList<>();
        while (t.find()) {
            tokens.add(t.group());
        }
        char cmd = 'M';
        double x = 0, y = 0, startX = 0, startY = 0;
        int i = 0;
        try {
            while (i < tokens.size()) {
                String tok = tokens.get(i);
                if (Character.isLetter(tok.charAt(0))) {
                    cmd = tok.charAt(0);
                    i++;
                    if (cmd == 'Z' || cmd == 'z') {
                        p.closePath();
                        x = startX;
                        y = startY;
                    }
                    continue;
                }
                boolean rel = Character.isLowerCase(cmd);
                switch (Character.toUpperCase(cmd)) {
                    case 'M': {
                        double nx = Double.parseDouble(tokens.get(i++)), ny = Double.parseDouble(tokens.get(i++));
                        x = rel ? x + nx : nx;
                        y = rel ? y + ny : ny;
                        p.moveTo(x, y);
                        startX = x;
                        startY = y;
                        cmd = rel ? 'l' : 'L'; // further pairs are line-tos
                        break;
                    }
                    case 'L': {
                        double nx = Double.parseDouble(tokens.get(i++)), ny = Double.parseDouble(tokens.get(i++));
                        x = rel ? x + nx : nx;
                        y = rel ? y + ny : ny;
                        p.lineTo(x, y);
                        break;
                    }
                    case 'H': {
                        double nx = Double.parseDouble(tokens.get(i++));
                        x = rel ? x + nx : nx;
                        p.lineTo(x, y);
                        break;
                    }
                    case 'V': {
                        double ny = Double.parseDouble(tokens.get(i++));
                        y = rel ? y + ny : ny;
                        p.lineTo(x, y);
                        break;
                    }
                    default:
                        i++;
                        break;
                }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            throw new I18n.LocalizedIOException(new I18n.Msg("svg.badPath"), e);
        }
        return p;
    }

    // ------------------------------------------------------------------ view

    private transient Drawing drawing;
    private int copies = 1;
    private boolean grid = true;
    private double zoom = 1;
    private double panX;
    private double panY;

    SvgViewPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(500, 300));
        MouseAdapter mouse = new MouseAdapter() {
            private Point2D last;

            @Override
            public void mousePressed(MouseEvent e) {
                last = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                panX += e.getX() - last.getX();
                panY += e.getY() - last.getY();
                last = e.getPoint();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom = Math.max(0.2, Math.min(40, zoom * Math.pow(1.15, -e.getPreciseWheelRotation())));
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    fit();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setDrawing(Drawing d, int copies) {
        this.drawing = d;
        this.copies = copies;
        fit();
    }

    void setGrid(boolean grid) {
        this.grid = grid;
        repaint();
    }

    /** Back to "whole part visible". */
    void fit() {
        zoom = 1;
        panX = 0;
        panY = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (drawing == null) {
            return;
        }
        Graphics2D g = UiKit.smooth(g0);
        int w = getWidth(), h = getHeight();
        Rectangle2D b = drawing.bounds();
        double margin = 56;
        double scale = Math.min((w - 2 * margin) / Math.max(b.getWidth(), 1e-6),
                (h - 2 * margin - 16) / Math.max(b.getHeight(), 1e-6)) * zoom;
        double ox = w / 2.0 - b.getCenterX() * scale + panX;
        double oy = h / 2.0 + 8 - b.getCenterY() * scale + panY;
        AffineTransform toScreen = new AffineTransform(scale, 0, 0, scale, ox, oy);

        if (grid) {
            paintGrid(g, w, h, scale, ox, oy);
        }

        g.setColor(UiKit.CUT_RED);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Shape s : drawing.outlines) {
            g.draw(toScreen.createTransformedShape(s));
        }
        for (Ellipse2D c : drawing.circles) {
            g.draw(toScreen.createTransformedShape(c));
        }

        g.setFont(UiKit.BASE.deriveFont(12f));
        Rectangle2D sb = toScreen.createTransformedShape(b).getBounds2D();
        if (!drawing.circles.isEmpty()) {
            paintDiameters(g, toScreen, sb);
        } else {
            paintBoxDimensions(g, b, sb);
        }
        paintScaleBar(g, h, scale);
        paintInfo(g, b);
        g.dispose();
    }

    private void paintGrid(Graphics2D g, int w, int h, double scale, double ox, double oy) {
        double step = scale >= 8 ? 1 : scale >= 1.5 ? 10 : 50; // mm between lines
        g.setStroke(new BasicStroke(1f));
        double x0 = Math.floor(-ox / scale / step) * step;
        for (double x = x0; x * scale + ox < w; x += step) {
            g.setColor(Math.round(x / step) % (step == 1 ? 10 : 5) == 0 ? new Color(0xE2E5EA) : new Color(0xF1F2F5));
            double sx = x * scale + ox;
            g.draw(new Line2D.Double(sx, 0, sx, h));
        }
        double y0 = Math.floor(-oy / scale / step) * step;
        for (double y = y0; y * scale + oy < h; y += step) {
            g.setColor(Math.round(y / step) % (step == 1 ? 10 : 5) == 0 ? new Color(0xE2E5EA) : new Color(0xF1F2F5));
            double sy = y * scale + oy;
            g.draw(new Line2D.Double(0, sy, w, sy));
        }
    }

    private void paintDiameters(Graphics2D g, AffineTransform toScreen, Rectangle2D sb) {
        Color dim = new Color(0x8A919C);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 3f }, 0f));
        g.setColor(dim);
        g.draw(new Line2D.Double(sb.getMinX() - 12, sb.getCenterY(), sb.getMaxX() + 12, sb.getCenterY()));
        g.draw(new Line2D.Double(sb.getCenterX(), sb.getMinY() - 12, sb.getCenterX(), sb.getMaxY() + 12));
        g.setColor(new Color(0x3F4651));
        List<Ellipse2D> sorted = new ArrayList<>(drawing.circles);
        sorted.sort((a, b) -> Double.compare(b.getWidth(), a.getWidth()));
        for (int i = 0; i < sorted.size(); i++) {
            Ellipse2D c = sorted.get(i);
            Rectangle2D s = toScreen.createTransformedShape(c).getBounds2D();
            String text = "\u00d8 " + mm(c.getWidth());
            if (i == 0) {
                g.drawString(text, (float) s.getMaxX() + 8, (float) s.getCenterY() - 5);
            } else {
                g.drawString(text, (float) s.getCenterX() + 6, (float) s.getMinY() - 6);
            }
        }
    }

    private void paintBoxDimensions(Graphics2D g, Rectangle2D b, Rectangle2D sb) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(0x8A919C));
        double y = sb.getMaxY() + 16;
        g.draw(new Line2D.Double(sb.getMinX(), y, sb.getMaxX(), y));
        g.draw(new Line2D.Double(sb.getMinX(), y - 5, sb.getMinX(), y + 5));
        g.draw(new Line2D.Double(sb.getMaxX(), y - 5, sb.getMaxX(), y + 5));
        double x = sb.getMaxX() + 16;
        g.draw(new Line2D.Double(x, sb.getMinY(), x, sb.getMaxY()));
        g.draw(new Line2D.Double(x - 5, sb.getMinY(), x + 5, sb.getMinY()));
        g.draw(new Line2D.Double(x - 5, sb.getMaxY(), x + 5, sb.getMaxY()));
        g.setColor(new Color(0x3F4651));
        FontMetrics fm = g.getFontMetrics();
        String wText = mm(b.getWidth()) + " " + I18n.tr("unit.mm");
        g.drawString(wText, (float) (sb.getCenterX() - fm.stringWidth(wText) / 2.0), (float) y + 16);
        g.drawString(mm(b.getHeight()) + " " + I18n.tr("unit.mm"), (float) x + 8, (float) sb.getCenterY() + 4);
    }

    private void paintScaleBar(Graphics2D g, int h, double scale) {
        double mm = scale * 10 < 30 ? 50 : 10;
        g.setColor(new Color(0x3F4651));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(14, h - 16, 14 + mm * scale, h - 16));
        g.setFont(UiKit.SMALL);
        g.drawString((int) mm + " " + I18n.tr("unit.mm"), (float) (20 + mm * scale), h - 12);
    }

    private void paintInfo(Graphics2D g, Rectangle2D b) {
        String shapes = !drawing.circles.isEmpty() ? I18n.plural("count.circles", drawing.circles.size())
                : I18n.plural("count.outlines", drawing.outlines.size());
        String text = I18n.tr("svg.info", mm(b.getWidth()), mm(b.getHeight()), shapes)
                + (copies > 1 ? I18n.tr("svg.copies", copies) : "");
        g.setFont(UiKit.BASE.deriveFont(12f));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.fillRect(10, 8, fm.stringWidth(text) + 8, 20);
        g.setColor(new Color(0x3F4651));
        g.drawString(text, 14, 22);
    }

    /** Millimetres with the language's decimal separator, without useless zeros. */
    static String mm(double v) {
        return I18n.mm(v);
    }
}
