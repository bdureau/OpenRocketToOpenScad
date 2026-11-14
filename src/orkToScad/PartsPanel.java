package orkToScad;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Left column: the parts of the rocket, grouped by kind, with badges for the files available. */
final class PartsPanel extends UiKit.RoundedPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<Object> model = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(model);
    private final JLabel count = UiKit.label("", UiKit.BASE, UiKit.MUTED);
    private final JTextField filter = new UiKit.HintField(I18n.tr("parts.filter"));
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JPanel dropZone;
    private transient List<Part> parts = new ArrayList<>();
    private transient Consumer<Part> onSelect = p -> { };
    private int lastIndex = -1;

    PartsPanel() {
        super(new BorderLayout());
        setPreferredSize(new Dimension(290, 400));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        header.add(UiKit.label(I18n.tr("parts.title"), UiKit.BOLD.deriveFont(14f), UiKit.TEXT));
        header.add(UiKit.gap(8));
        header.add(count);
        header.add(Box.createHorizontalGlue());
        filter.setFont(UiKit.BASE.deriveFont(12f));
        filter.setColumns(10);
        filter.setMaximumSize(new Dimension(130, 28));
        filter.setToolTipText(I18n.tr("parts.filter.accessible"));
        filter.getAccessibleContext().setAccessibleName(I18n.tr("parts.filter.accessible"));
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });
        header.add(filter);
        add(header, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.setBorder(BorderFactory.createEmptyBorder(0, 6, 10, 6));
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            Object v = list.getSelectedValue();
            int index = list.getSelectedIndex();
            if (v instanceof String) { // a group title: skip it in the direction of the move
                int i = index < lastIndex && index > 0 ? index - 1 : index + 1;
                if (i >= model.size() || !(model.get(i) instanceof Part)) {
                    i = index + 1;
                }
                if (i < model.size()) {
                    list.setSelectedIndex(i);
                } else {
                    list.clearSelection();
                }
                return;
            }
            lastIndex = index;
            onSelect.accept((Part) v);
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiKit.PANEL);

        dropZone = new DropZone();
        body.setOpaque(false);
        body.add(dropZone, "empty");
        body.add(scroll, "list");
        add(body, BorderLayout.CENTER);
        setParts(List.of());
    }

    void setOnSelect(Consumer<Part> onSelect) {
        this.onSelect = onSelect;
    }

    /** The component that accepts dropped .ork files when the list is empty. */
    JComponent dropZone() {
        return dropZone;
    }

    void setParts(List<Part> parts) {
        this.parts = new ArrayList<>(parts);
        filter.setText("");
        filter.setEnabled(!parts.isEmpty());
        count.setText(parts.isEmpty() ? "" : String.valueOf(parts.size()));
        refill();
        cards.show(body, parts.isEmpty() ? "empty" : "list");
        selectFirst();
    }

    /** Index of the selected part in the full list, or -1. */
    int selectedPartIndex() {
        Object v = list.getSelectedValue();
        return v instanceof Part ? parts.indexOf(v) : -1;
    }

    /** Selects a part by its index in the full list (the first part if out of range). */
    void selectPart(int index) {
        if (index < 0 || index >= parts.size()) {
            selectFirst();
            return;
        }
        list.setSelectedValue(parts.get(index), true);
    }

    void selectFirst() {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i) instanceof Part) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(0);
                return;
            }
        }
        onSelect.accept(null);
    }

    private void refill() {
        Object selected = list.getSelectedValue();
        String f = filter.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        String group = null;
        for (Part p : parts) {
            if (!f.isEmpty() && !(p.title + " " + p.subtitle).toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            if (!p.kind.groupKey.equals(group)) {
                group = p.kind.groupKey;
                model.addElement(I18n.tr(group));
            }
            model.addElement(p);
        }
        if (selected != null && model.contains(selected)) {
            list.setSelectedValue(selected, true);
        }
    }

    /** Draws a group title or a part row (title, subtitle, copies, badges). */
    private static final class Renderer extends JComponent implements ListCellRenderer<Object> {
        private static final long serialVersionUID = 1L;
        private transient Object value;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object v, int index, boolean sel, boolean focus) {
            this.value = v;
            this.selected = sel && v instanceof Part;
            setPreferredSize(new Dimension(200, v instanceof String ? 30 : 46));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UiKit.smooth(g0);
            int w = getWidth(), h = getHeight();
            if (value instanceof String) {
                g.setFont(UiKit.SMALL.deriveFont(java.awt.Font.BOLD));
                g.setColor(UiKit.MUTED);
                g.drawString(((String) value).toUpperCase(I18n.locale()), 10, h - 8);
                g.dispose();
                return;
            }
            Part p = (Part) value;
            if (selected) {
                g.setColor(UiKit.SELECTION);
                g.fill(new RoundRectangle2D.Double(0, 1, w, h - 2, 10, 10));
                g.setColor(UiKit.ACCENT);
                g.fillRect(0, 4, 3, h - 8);
            }
            // badges, right-aligned
            int x = w - 8;
            List<Object[]> chips = new ArrayList<>();
            if (p.has(".stl")) {
                chips.add(new Object[] { "STL", UiKit.CHIP_STL });
            }
            if (p.has(".svg")) {
                chips.add(new Object[] { "SVG", UiKit.CHIP_SVG });
            }
            if (p.has(".scad")) {
                chips.add(new Object[] { "SCAD", UiKit.CHIP_SCAD });
            }
            FontMetrics cfm = g.getFontMetrics(UiKit.SMALL.deriveFont(java.awt.Font.BOLD, 10f));
            for (int i = chips.size() - 1; i >= 0; i--) {
                x -= cfm.stringWidth((String) chips.get(i)[0]) + 12;
                UiKit.paintChip(g, x, (h - 16) / 2, (String) chips.get(i)[0], (Color[]) chips.get(i)[1]);
                x -= 4;
            }
            int textW = x - 12 - 4;
            g.setFont(selected ? UiKit.BOLD : UiKit.BASE.deriveFont(java.awt.Font.PLAIN));
            g.setColor(UiKit.TEXT);
            String title = p.title;
            FontMetrics fm = g.getFontMetrics();
            String copies = p.copies > 1 ? "  \u00d7 " + p.copies : "";
            title = ellipsize(title, fm, textW - g.getFontMetrics(UiKit.BASE).stringWidth(copies));
            g.drawString(title, 12, 20);
            if (!copies.isEmpty()) {
                g.setFont(UiKit.BASE);
                g.setColor(UiKit.MUTED);
                g.drawString(copies, 12 + fm.stringWidth(title), 20);
            }
            g.setFont(UiKit.SMALL);
            g.setColor(selected ? new Color(0x3F4651) : UiKit.MUTED);
            g.drawString(ellipsize(p.subtitle, g.getFontMetrics(), textW), 12, 36);
            g.dispose();
        }

        private static String ellipsize(String s, FontMetrics fm, int width) {
            if (fm.stringWidth(s) <= width) {
                return s;
            }
            while (s.length() > 1 && fm.stringWidth(s + "\u2026") > width) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "\u2026";
        }
    }

    /** Dashed area shown before any conversion. */
    private static final class DropZone extends JPanel {
        private static final long serialVersionUID = 1L;

        DropZone() {
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            JLabel icon = new JLabel(UiKit.icon(UiKit.Glyph.DROP, UiKit.ACCENT, 36));
            icon.setAlignmentX(CENTER_ALIGNMENT);
            col.add(icon);
            col.add(Box.createVerticalStrut(10));
            JLabel t = UiKit.label(I18n.tr("parts.drop.title"), UiKit.BOLD.deriveFont(14f), new Color(0x3F4651));
            t.setAlignmentX(CENTER_ALIGNMENT);
            col.add(t);
            col.add(Box.createVerticalStrut(8));
            JLabel m = UiKit.label("<html><div style='text-align:center;width:190px'>"
                    + I18n.tr("parts.drop.text", I18n.tr("toolbar.browse"), I18n.tr("toolbar.convert"),
                            I18n.tr("toolbar.openFolder"))
                    + "</div></html>", UiKit.BASE, UiKit.MUTED);
            m.setAlignmentX(CENTER_ALIGNMENT);
            col.add(m);
            add(col);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UiKit.smooth(g0);
            g.setColor(UiKit.INPUT_BORDER);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 6f, 5f },
                    0f));
            g.draw(new RoundRectangle2D.Double(13, 1, getWidth() - 26, getHeight() - 14, 14, 14));
            g.dispose();
        }
    }
}
