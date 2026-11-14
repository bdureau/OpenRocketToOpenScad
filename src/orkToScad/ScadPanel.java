package orkToScad;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
 * Content of the OpenSCAD box: the code of the part's .scad file (coloured, with line numbers) or,
 * when OpenSCAD is installed, an image rendered by OpenSCAD. Rendering runs in the background and
 * is cached per file.
 */
final class ScadPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Pattern KEYWORDS = Pattern.compile("\\b(module|function|if|else|for|let|each|assert|echo"
            + "|true|false|undef|include|use)\\b");
    private static final Pattern BUILTINS = Pattern.compile("\\b(cube|sphere|cylinder|polyhedron|square|circle"
            + "|polygon|text|translate|rotate|scale|mirror|multmatrix|color|offset|hull|minkowski|union|difference"
            + "|intersection|linear_extrude|rotate_extrude|import|projection|concat|pow|sqrt|max|min|sin|cos|acos"
            + "|str|abs|len)\\b");
    private static final Pattern NUMBERS = Pattern.compile("(?<![\\w$])-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    private static final Pattern STRINGS = Pattern.compile("\"[^\"\\n]*\"");
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** Marks a failed render (a retry makes sense), invisible in the HTML message. */
    private static final String FAILED_PREFIX = "<!--failed-->";

    private final JToggleButton codeButton = new Segment(I18n.tr("scad.code"), true);
    private final JToggleButton renderButton = new Segment(I18n.tr("scad.render"), false);
    private final JComboBox<Path> fileCombo = new JComboBox<>();
    private final JButton openButton = new JButton(I18n.tr("scad.open"));
    private final CardLayout cards = new CardLayout();
    private final JTextPane code = new NoWrapTextPane();
    private final RenderView renderView = new RenderView();
    private final JLabel footerStatus = new JLabel();
    private final JButton locateLink = UiKit.link(I18n.tr("scad.locate"));
    private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    private final transient Map<String, BufferedImage> cache = new HashMap<>();

    private transient Path openscad;
    private transient Consumer<String> errorSink = msg -> { };
    private int renderGeneration;

    ScadPanel() {
        super(new BorderLayout());
        code.setEditable(false);
        code.setFont(UiKit.MONO);
        code.setBackground(new Color(0xFBFBFC));
        code.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(code);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setRowHeaderView(new LineNumbers(code));
        scroll.getViewport().setBackground(new Color(0xFBFBFC));

        JPanel body = new JPanel(cards);
        body.add(scroll, "code");
        body.add(renderView, "render");
        add(body, BorderLayout.CENTER);

        ButtonGroup g = new ButtonGroup();
        g.add(codeButton);
        g.add(renderButton);
        codeButton.addActionListener(e -> cards.show(body, "code"));
        renderButton.addActionListener(e -> {
            cards.show(body, "render");
            renderSelected(false);
        });

        fileCombo.setFont(UiKit.MONO_SMALL);
        fileCombo.setVisible(false);
        fileCombo.setPreferredSize(new Dimension(190, 28));
        fileCombo.setMaximumSize(new Dimension(220, 28));
        fileCombo.setMinimumSize(new Dimension(60, 28));
        fileCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel,
                    boolean focus) {
                Object text = value instanceof Path ? ((Path) value).getFileName().toString() : value;
                return super.getListCellRendererComponent(list, text, index, sel, focus);
            }
        });
        fileCombo.addActionListener(e -> showSelected());
        openButton.setFont(UiKit.BASE.deriveFont(12f));
        openButton.addActionListener(e -> {
            Path f = (Path) fileCombo.getSelectedItem();
            if (f != null) {
                try {
                    OpenScad.open(openscad, f);
                } catch (IOException ex) {
                    errorSink.accept(I18n.tr("scad.cannotOpen", f.getFileName(), ex.getMessage()));
                }
            }
        });
        renderView.refresh.addActionListener(e -> renderSelected(true));

        footer.setBackground(UiKit.PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE4E7EB)));
        footer.add(footerStatus);
        footer.add(locateLink);
    }

    /** Header tools, added by the window to the box header. */
    List<JComponent> tools() {
        JPanel segmented = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        segmented.setOpaque(false);
        segmented.add(codeButton);
        segmented.add(renderButton);
        return List.of(segmented, fileCombo, openButton);
    }

    JPanel footer() {
        return footer;
    }

    JButton locateLink() {
        return locateLink;
    }

    void setErrorSink(Consumer<String> sink) {
        this.errorSink = sink;
    }

    /** Sets the OpenSCAD program found (or null) and updates the footer. */
    void setOpenScad(Path exe) {
        this.openscad = exe;
        if (exe != null) {
            footerStatus.setText(I18n.tr("scad.found", exe));
            footerStatus.setIcon(UiKit.dot(UiKit.OK));
            locateLink.setText("<html><u>" + I18n.tr("scad.change") + "</u></html>");
        } else {
            footerStatus.setText(I18n.tr("scad.notFound"));
            footerStatus.setIcon(UiKit.dot(UiKit.WARN));
            locateLink.setText("<html><u>" + I18n.tr("scad.locate") + "</u></html>");
        }
        footerStatus.setFont(UiKit.BASE.deriveFont(12f));
        footerStatus.setForeground(new Color(0x3F4651));
        footerStatus.setIconTextGap(8);
        openButton.setToolTipText(exe == null ? I18n.tr("scad.open.tip") : null);
        cache.clear();
        if (renderButton.isSelected()) {
            renderSelected(false);
        }
    }

    /** Shows the .scad files of a part (first one selected). */
    void setFiles(List<Path> files) {
        fileCombo.removeAllItems();
        for (Path f : files) {
            fileCombo.addItem(f);
        }
        fileCombo.setVisible(!files.isEmpty());
        showSelected();
    }

    private void showSelected() {
        Path f = (Path) fileCombo.getSelectedItem();
        if (f == null) {
            return;
        }
        fileCombo.setToolTipText(f.getFileName().toString());
        try {
            setCode(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            setCode("// " + I18n.tr("scad.cannotRead", f.getFileName(), e.getMessage()));
        }
        if (renderButton.isSelected()) {
            renderSelected(false);
        }
    }

    private void setCode(String text) {
        text = text.replace("\r\n", "\n").stripTrailing();
        code.setText(text);
        StyledDocument doc = code.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, UiKit.TEXT);
        doc.setCharacterAttributes(0, doc.getLength(), base, true);
        color(doc, text, BUILTINS, new Color(0x1D4ED8), false);
        color(doc, text, KEYWORDS, new Color(0x7C3AED), true);
        color(doc, text, NUMBERS, new Color(0x9A3412), false);
        color(doc, text, STRINGS, new Color(0x15803D), false);
        color(doc, text, COMMENTS, UiKit.MUTED, false);
        code.setCaretPosition(0);
    }

    private static void color(StyledDocument doc, String text, Pattern p, Color c, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        Matcher m = p.matcher(text.replace("\r\n", "\n"));
        while (m.find()) {
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), a, true);
        }
    }

    // ------------------------------------------------------------------ rendering

    private void renderSelected(boolean force) {
        Path f = (Path) fileCombo.getSelectedItem();
        int gen = ++renderGeneration;
        if (f == null) {
            renderView.show(null, "", null);
            return;
        }
        if (openscad == null) {
            renderView.show(null, null, I18n.tr("scad.render.unavailable", I18n.tr("scad.code")));
            return;
        }
        String key;
        try {
            key = f + "|" + Files.getLastModifiedTime(f).toMillis() + "|" + Files.size(f);
        } catch (IOException e) {
            renderView.show(null, null, I18n.tr("scad.cannotReadFile", f.getFileName()));
            return;
        }
        if (!force && cache.containsKey(key)) {
            renderView.show(cache.get(key), null, null);
            return;
        }
        renderView.show(null, I18n.tr("scad.render.running"), null);
        int w = Math.max(400, renderView.getWidth());
        int h = Math.max(300, renderView.getHeight());
        Path exe = openscad;
        long start = System.nanoTime();
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                return OpenScad.render(exe, f, w, h);
            }

            @Override
            protected void done() {
                if (gen != renderGeneration) {
                    return; // another file was selected meanwhile
                }
                try {
                    BufferedImage img = get();
                    cache.put(key, img);
                    double s = (System.nanoTime() - start) / 1e9;
                    renderView.show(img, I18n.tr("scad.render.done", I18n.decimal1(s)), null);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    renderView.show(null, null, FAILED_PREFIX + I18n.tr("scad.render.failed", cause.getMessage()));
                }
            }
        }.execute();
    }

    /** Image rendered by OpenSCAD, fitted in the box, or a message. */
    private static final class RenderView extends JPanel {
        private static final long serialVersionUID = 1L;
        private transient BufferedImage image;
        private String message;
        private final JLabel status = UiKit.label("", UiKit.BASE.deriveFont(12f), new Color(0x3F4651));
        final JButton refresh = new JButton(I18n.tr("scad.refresh"));

        RenderView() {
            super(new BorderLayout());
            setBackground(new Color(0xFFFFE5)); // OpenSCAD "Cornfield" background
            JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            top.setOpaque(false);
            refresh.setFont(UiKit.BASE.deriveFont(12f));
            top.add(status);
            top.add(refresh);
            add(top, BorderLayout.NORTH);
        }

        void show(BufferedImage img, String statusText, String errorHtml) {
            image = img;
            message = errorHtml;
            status.setText(statusText == null ? "" : statusText);
            refresh.setVisible(errorHtml == null || errorHtml.startsWith(FAILED_PREFIX));
            setBackground(img != null ? new Color(0xFFFFE5) : UiKit.EMPTY_BG);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = UiKit.smooth(g0);
            if (image != null) {
                double s = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
                int w = (int) (image.getWidth() * s), h = (int) (image.getHeight() * s);
                g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            } else if (message != null) {
                JLabel l = UiKit.label("<html><div style='text-align:center;width:320px'>" + message + "</div></html>",
                        UiKit.BASE, UiKit.MUTED);
                Dimension d = l.getPreferredSize();
                l.setSize(d);
                g.translate((getWidth() - d.width) / 2, (getHeight() - d.height) / 2);
                l.paint(g);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ small components

    /** Half of a two-button segmented control (Code | Rendu). */
    private static final class Segment extends JToggleButton {
        private static final long serialVersionUID = 1L;
        private final boolean left;

        Segment(String text, boolean left) {
            super(text, left);
            this.left = left;
            setFont(UiKit.BASE.deriveFont(java.awt.Font.BOLD, 12f));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UiKit.smooth(g0);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D r = new RoundRectangle2D.Double(left ? 0.5 : -8, 0.5, w + 7.5, h - 1, 10, 10);
            g.clipRect(0, 0, w, h);
            g.setColor(isSelected() ? UiKit.TEXT : UiKit.PANEL);
            g.fill(r);
            g.setColor(UiKit.INPUT_BORDER);
            g.draw(r);
            if (!left) {
                g.drawLine(0, 0, 0, h);
            }
            g.dispose();
            setForeground(isSelected() ? Color.WHITE : UiKit.TEXT);
            super.paintComponent(g0);
        }
    }

    /** Text pane that scrolls horizontally instead of wrapping lines. */
    private static final class NoWrapTextPane extends JTextPane {
        private static final long serialVersionUID = 1L;

        NoWrapTextPane() {
            setEditorKit(new NoWrapKit());
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            // fill the width when the code is narrower than the box, otherwise scroll
            return getParent() == null || getUI().getPreferredSize(this).width <= getParent().getSize().width;
        }
    }

    /** Styled editor kit whose paragraphs are laid out on one line, whatever the width. */
    private static final class NoWrapKit extends StyledEditorKit {
        private static final long serialVersionUID = 1L;

        @Override
        public ViewFactory getViewFactory() {
            return elem -> {
                String kind = elem.getName();
                if (AbstractDocument.ParagraphElementName.equals(kind)) {
                    return new ParagraphView(elem) {
                        @Override
                        public void layout(int width, int height) {
                            super.layout(Short.MAX_VALUE, height);
                        }

                        @Override
                        public float getMinimumSpan(int axis) {
                            return super.getPreferredSpan(axis);
                        }
                    };
                }
                if (AbstractDocument.SectionElementName.equals(kind)) {
                    return new BoxView(elem, View.Y_AXIS);
                }
                return new LabelView(elem);
            };
        }
    }

    /** Line numbers next to the code. */
    private static final class LineNumbers extends JComponent {
        private static final long serialVersionUID = 1L;
        private final JTextPane pane;

        LineNumbers(JTextPane pane) {
            this.pane = pane;
            setFont(UiKit.MONO);
            pane.addPropertyChangeListener("document", e -> repaint());
            pane.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    revalidate();
                    repaint();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    revalidate();
                    repaint();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int lines = pane.getDocument().getDefaultRootElement().getElementCount();
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(String.valueOf(Math.max(99, lines))) + 22;
            return new Dimension(w, pane.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UiKit.smooth(g0);
            g.setColor(new Color(0xFBFBFC));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0xE4E7EB));
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            g.setColor(UiKit.SUBTLE);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            Element root = pane.getDocument().getDefaultRootElement();
            Rectangle clip = g.getClipBounds();
            for (int i = 0; i < root.getElementCount(); i++) {
                try {
                    Rectangle2D r = pane.modelToView2D(root.getElement(i).getStartOffset());
                    if (r == null || r.getMaxY() < clip.y || r.getY() > clip.y + clip.height) {
                        continue;
                    }
                    String n = String.valueOf(i + 1);
                    g.drawString(n, getWidth() - 10 - fm.stringWidth(n), (float) (r.getY() + fm.getAscent()));
                } catch (BadLocationException e) {
                    break;
                }
            }
            g.dispose();
        }
    }
}
