package orkToScad;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One of the three preview boxes: a header (icon, title, file name, tools) and a body that shows
 * either the preview or a message (for example "no SVG for this part").
 */
final class PreviewBox extends UiKit.RoundedPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel fileLabel = UiKit.label("", UiKit.MONO_SMALL, UiKit.MUTED);
    private final JPanel tools = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JPanel emptyHolder = new JPanel(new BorderLayout());
    private final transient Icon emptyIcon;

    PreviewBox(Icon icon, String title, JComponent content) {
        super(new BorderLayout());
        this.emptyIcon = UiKit.icon(UiKit.Glyph.EMPTY_BOX, UiKit.SUBTLE, 40);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBackground(UiKit.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiKit.BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 10)));
        header.add(new JLabel(icon));
        header.add(UiKit.gap(8));
        header.add(UiKit.label(title, UiKit.BOLD, UiKit.TEXT));
        header.add(UiKit.gap(10));
        header.add(fileLabel);
        header.add(Box.createHorizontalGlue());
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.X_AXIS));
        header.add(tools);
        header.setPreferredSize(new Dimension(10, 44));
        header.setMinimumSize(new Dimension(10, 44));

        body.add(content, "content");
        body.add(emptyHolder, "empty");
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    /** Adds a tool to the right of the header. */
    void addTool(Component c) {
        if (tools.getComponentCount() > 0) {
            tools.add(UiKit.gap(8));
        }
        if (c instanceof JComponent) {
            ((JComponent) c).setOpaque(false);
        }
        tools.add(c);
    }

    /** Adds a footer line under the body. */
    void setFooter(JComponent footer) {
        add(footer, BorderLayout.SOUTH);
    }

    /** Shows the preview, with the file name in the header. */
    void showContent(String fileName) {
        fileLabel.setText(fileName == null ? "" : fileName);
        fileLabel.setFont(UiKit.MONO_SMALL);
        setToolsEnabled(true);
        cards.show(body, "content");
    }

    /** Shows a message instead of the preview. */
    void showEmpty(String title, String message) {
        fileLabel.setText(title == null ? "" : I18n.tr("box.noFile"));
        fileLabel.setFont(UiKit.BASE.deriveFont(12f));
        setToolsEnabled(false);
        emptyHolder.removeAll();
        emptyHolder.add(UiKit.emptyState(title == null ? null : emptyIcon, title, message));
        emptyHolder.revalidate();
        cards.show(body, "empty");
    }

    private void setToolsEnabled(boolean enabled) {
        for (Component c : tools.getComponents()) {
            c.setEnabled(enabled);
            c.setForeground(enabled ? UiKit.TEXT : UiKit.SUBTLE);
        }
    }

    /** Footer label with a status dot. */
    static JLabel footerLabel(String text, Color dot) {
        JLabel l = UiKit.label(text, UiKit.BASE.deriveFont(12f), new Color(0x3F4651));
        l.setIcon(UiKit.dot(dot));
        l.setIconTextGap(8);
        return l;
    }
}
