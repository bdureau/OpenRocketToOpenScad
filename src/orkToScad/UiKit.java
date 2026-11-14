package orkToScad;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Colours, fonts and small components of the main window (plain Swing, no library). */
final class UiKit {

    static final Color WINDOW = new Color(0xEEF0F3);
    static final Color PANEL = Color.WHITE;
    static final Color BORDER = new Color(0xDDE1E6);
    static final Color INPUT_BORDER = new Color(0xC9CED6);
    static final Color TEXT = new Color(0x1F2328);
    static final Color MUTED = new Color(0x5B6370);
    static final Color SUBTLE = new Color(0x8A919C);
    static final Color ACCENT = new Color(0x2563EB);
    static final Color SELECTION = new Color(0xE3EBFD);
    static final Color VIEW_BG = new Color(0xF4F5F7);
    static final Color EMPTY_BG = new Color(0xFAFAFB);
    static final Color OK = new Color(0x15803D);
    static final Color WARN = new Color(0xC2410C);
    static final Color ERROR = new Color(0xB91C1C);
    static final Color CUT_RED = new Color(0xE5372B);
    static final Color STL_ICON = new Color(0xB4560F);
    static final Color SVG_ICON = new Color(0xC0261B);
    static final Color SCAD_ICON = new Color(0x1E40AF);

    /** Badge colours: background, text. */
    static final Color[] CHIP_STL = { new Color(0xFCE7CF), new Color(0x7A400C) };
    static final Color[] CHIP_SVG = { new Color(0xFBDDDA), new Color(0x8F1B12) };
    static final Color[] CHIP_SCAD = { new Color(0xDFE7FB), new Color(0x1E3A8A) };

    static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font BOLD = BASE.deriveFont(Font.BOLD);
    static final Font SMALL = BASE.deriveFont(11f);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static final Font MONO_SMALL = MONO.deriveFont(12f);

    private UiKit() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** White panel with a 1 px border and rounded corners. */
    static class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBackground(PANEL);
            setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(getBackground());
            g2.fill(r);
            g2.dispose();
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.clip(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
            super.paintChildren(g2);
            g2.dispose();
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(BORDER);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 12, 12));
            g2.dispose();
        }
    }

    /** Text field showing a grey hint while it is empty. */
    static class HintField extends javax.swing.JTextField {
        private static final long serialVersionUID = 1L;
        private final String hint;

        HintField(String hint) {
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && isEnabled()) {
                Graphics2D g2 = smooth(g);
                g2.setColor(SUBTLE);
                g2.setFont(getFont());
                java.awt.Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(hint, in.left + 2, y);
                g2.dispose();
            }
        }
    }

    /** Blue, rounded main action button. */
    static class PrimaryButton extends JButton {
        private static final long serialVersionUID = 1L;

        PrimaryButton(String text) {
            super(text);
            setFont(BOLD);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Color bg = !isEnabled() ? new Color(0xE4E7EB)
                    : getModel().isPressed() ? new Color(0x1D4ED8)
                    : getModel().isRollover() ? new Color(0x1E55D6) : ACCENT;
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
            if (isFocusOwner()) {
                g2.setColor(new Color(0x93B4F8));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
            }
            g2.dispose();
            setForeground(isEnabled() ? Color.WHITE : MUTED);
            super.paintComponent(g);
        }
    }

    /** Button that looks like a link. */
    static JButton link(String text) {
        JButton b = new JButton("<html><u>" + text + "</u></html>");
        b.setFont(BASE.deriveFont(12f));
        b.setForeground(new Color(0x1D4ED8));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(b.getPreferredSize());
        return b;
    }

    /** Paints a small badge ("STL", "SVG", "SCAD"); returns its width. */
    static int paintChip(Graphics2D g, int x, int y, String text, Color[] colors) {
        Font f = SMALL.deriveFont(Font.BOLD, 10f);
        FontMetrics fm = g.getFontMetrics(f);
        int w = fm.stringWidth(text) + 12;
        g.setColor(colors[0]);
        g.fill(new RoundRectangle2D.Double(x, y, w, 16, 8, 8));
        g.setColor(colors[1]);
        g.setFont(f);
        g.drawString(text, x + 6, y + 12);
        return w;
    }

    /** Small status dot. */
    static Icon dot(Color c) {
        return new Icon() {
            @Override
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                g2.setColor(c);
                g2.fillOval(x, y + 1, 8, 8);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 8;
            }

            @Override
            public int getIconHeight() {
                return 10;
            }
        };
    }

    /** Line icons of the three boxes, plus status icons. */
    enum Glyph {
        CUBE, SHEET, CODE, CHECK, WARNING, DROP, EMPTY_BOX, GLOBE
    }

    static Icon icon(Glyph glyph, Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = smooth(g);
                g2.translate(x, y);
                double s = size / 16.0;
                g2.scale(s, s);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D p = new Path2D.Double();
                switch (glyph) {
                    case CUBE:
                        p.moveTo(8, 1.5);
                        p.lineTo(14, 4.75);
                        p.lineTo(14, 11.25);
                        p.lineTo(8, 14.5);
                        p.lineTo(2, 11.25);
                        p.lineTo(2, 4.75);
                        p.closePath();
                        p.moveTo(2, 4.75);
                        p.lineTo(8, 8);
                        p.lineTo(14, 4.75);
                        p.moveTo(8, 8);
                        p.lineTo(8, 14.5);
                        g2.draw(p);
                        break;
                    case SHEET:
                        g2.draw(new RoundRectangle2D.Double(2, 2, 12, 12, 3, 3));
                        g2.drawOval(5, 5, 6, 6);
                        break;
                    case CODE:
                        p.moveTo(5.5, 4);
                        p.lineTo(2, 8);
                        p.lineTo(5.5, 12);
                        p.moveTo(10.5, 4);
                        p.lineTo(14, 8);
                        p.lineTo(10.5, 12);
                        g2.draw(p);
                        break;
                    case CHECK:
                        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        p.moveTo(3, 8.5);
                        p.lineTo(6.5, 12);
                        p.lineTo(13, 4);
                        g2.draw(p);
                        break;
                    case WARNING:
                        p.moveTo(8, 2);
                        p.lineTo(14.5, 14);
                        p.lineTo(1.5, 14);
                        p.closePath();
                        g2.draw(p);
                        g2.drawLine(8, 6, 8, 9);
                        g2.drawLine(8, 11, 8, 11);
                        break;
                    case DROP:
                        p.moveTo(8, 2);
                        p.lineTo(8, 10);
                        p.moveTo(4.5, 6.5);
                        p.lineTo(8, 10);
                        p.lineTo(11.5, 6.5);
                        p.moveTo(2, 11);
                        p.lineTo(2, 14);
                        p.lineTo(14, 14);
                        p.lineTo(14, 11);
                        g2.draw(p);
                        break;
                    case GLOBE:
                        g2.drawOval(2, 2, 12, 12);
                        g2.drawOval(5, 2, 6, 12);
                        g2.drawLine(2, 8, 14, 8);
                        break;
                    default:
                        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                                new float[] { 2f, 2f }, 0f));
                        g2.draw(new RoundRectangle2D.Double(2, 2, 12, 12, 2, 2));
                        break;
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** Centered message shown when a box has nothing to display. */
    static JPanel emptyState(Icon icon, String title, String message) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(EMPTY_BG);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        if (icon != null) {
            JLabel i = new JLabel(icon);
            i.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(i);
            col.add(Box.createVerticalStrut(10));
        }
        if (title != null) {
            JLabel t = label(title, BOLD.deriveFont(14f), new Color(0x3F4651));
            t.setAlignmentX(Component.CENTER_ALIGNMENT);
            col.add(t);
            col.add(Box.createVerticalStrut(6));
        }
        JLabel m = label("<html><div style='text-align:center;width:300px'>" + message + "</div></html>", BASE, MUTED);
        m.setHorizontalAlignment(SwingConstants.CENTER);
        m.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(m);
        p.add(col);
        return p;
    }

    /** Fixed-width spacer. */
    static Component gap(int w) {
        return Box.createRigidArea(new Dimension(w, 0));
    }

    /** A panel with a white background and a bottom/top border line. */
    static JPanel bar(int side) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createMatteBorder(side == SwingConstants.TOP ? 1 : 0, 0,
                side == SwingConstants.BOTTOM ? 1 : 0, 0, BORDER));
        return p;
    }
}
