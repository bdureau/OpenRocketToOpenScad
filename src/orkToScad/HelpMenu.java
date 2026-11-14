package orkToScad;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** The "Help" menu: user manual (PDF in the current language) and "About" box. */
final class HelpMenu {

    /** Folder of the PDF manuals, in the jar and next to it ({@code manual/ORKtoSCAD-manual-fr.pdf}...). */
    static final String MANUAL_DIR = "manual";

    private HelpMenu() {
    }

    static JMenuBar menuBar(JFrame owner) {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(UiKit.PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiKit.BORDER));
        JMenu help = new JMenu(I18n.tr("menu.help"));
        help.setFont(UiKit.BASE);

        JMenuItem manual = new JMenuItem(I18n.tr("menu.manual"));
        manual.setFont(UiKit.BASE);
        manual.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        manual.addActionListener(e -> openManual(owner));

        JMenuItem about = new JMenuItem(I18n.tr("menu.about"));
        about.setFont(UiKit.BASE);
        about.addActionListener(e -> showAbout(owner));

        help.add(manual);
        help.addSeparator();
        help.add(about);
        bar.add(help);
        return bar;
    }

    // ------------------------------------------------------------------ manual

    static String manualFileName(String lang) {
        return "ORKtoSCAD-manual-" + lang + ".pdf";
    }

    /** Opens the manual of the current language (English if there is none) with the system's PDF viewer. */
    static void openManual(JFrame owner) {
        String lang = I18n.language();
        new Thread(() -> {
            try {
                Path pdf = findManual(lang);
                if (pdf == null && !"en".equals(lang)) {
                    pdf = findManual("en");
                }
                if (pdf == null) {
                    fail(owner, I18n.tr("manual.notFound", MANUAL_DIR + "/" + manualFileName(lang)));
                    return;
                }
                openWithSystem(pdf);
            } catch (IOException | RuntimeException e) {
                fail(owner, I18n.tr("manual.cannotOpen", String.valueOf(e.getMessage())));
            }
        }, "open-manual").start();
    }

    /**
     * Returns the manual as a file: next to the jar, in the current directory (Eclipse), or copied out of the
     * jar into the temporary folder (a PDF viewer cannot open a file inside a jar).
     */
    private static Path findManual(String lang) throws IOException {
        String name = manualFileName(lang);
        Path jarDir = FileUtils.jarDirectory();
        if (jarDir != null) {
            Path p = jarDir.resolve(MANUAL_DIR).resolve(name);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        Path p = Paths.get(MANUAL_DIR).resolve(name);
        if (Files.isRegularFile(p)) {
            return p.toAbsolutePath();
        }
        try (InputStream in = HelpMenu.class.getResourceAsStream("/" + MANUAL_DIR + "/" + name)) {
            if (in == null) {
                return null;
            }
            byte[] data = in.readAllBytes();
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "ORKtoSCAD");
            Files.createDirectories(dir);
            Path out = dir.resolve(name.replace(".pdf", "-" + Main.VERSION + ".pdf"));
            if (Files.isRegularFile(out) && Files.size(out) == data.length) {
                return out; // already extracted (and maybe still open in the viewer)
            }
            Path tmp = Files.createTempFile(dir, "manual", ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException locked) {
                Files.deleteIfExists(tmp);
                if (!Files.isRegularFile(out)) {
                    throw locked;
                }
            }
            return out;
        }
    }

    /** Opens the file with its default program: Java's Desktop first, then the system's own "open" command. */
    private static void openWithSystem(Path file) throws IOException {
        IOException desktopError = null;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(file.toFile());
                return;
            } catch (IOException | RuntimeException e) {
                desktopError = e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd = os.contains("mac") ? new String[] { "open", file.toString() }
                : os.contains("win") ? new String[] { "rundll32", "url.dll,FileProtocolHandler", file.toString() }
                : new String[] { "xdg-open", file.toString() };
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (p.waitFor() == 0) {
                return;
            }
        } catch (IOException e) {
            // command not available
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        throw desktopError != null ? desktopError : new IOException(file.toString());
    }

    private static void fail(JFrame owner, String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(owner, message, I18n.tr("menu.manual"),
                JOptionPane.WARNING_MESSAGE));
    }

    // ------------------------------------------------------------------ about

    static void showAbout(JFrame owner) {
        JDialog d = new JDialog(owner, I18n.tr("about.title"), true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(UiKit.PANEL);
        content.setBorder(BorderFactory.createEmptyBorder(20, 22, 14, 22));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;

        JLabel logo = new JLabel();
        Image icon = icon(96);
        if (icon != null) {
            logo.setIcon(new ImageIcon(icon));
        }
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 6;
        c.insets = new Insets(0, 0, 0, 20);
        content.add(logo, c);

        c.gridx = 1;
        c.gridheight = 1;
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        content.add(UiKit.label("ORKtoSCAD", UiKit.BASE.deriveFont(java.awt.Font.BOLD, 22f), UiKit.TEXT), c);

        c.gridy = 1;
        c.insets = new Insets(2, 0, 12, 0);
        content.add(UiKit.label(I18n.tr("about.version", Main.VERSION), UiKit.BASE, UiKit.MUTED), c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 14, 0);
        JLabel summary = new JLabel("<html><div style='width:330px'>" + escape(I18n.tr("about.summary"))
                + "</div></html>");
        summary.setFont(UiKit.BASE);
        summary.setForeground(UiKit.TEXT);
        content.add(summary, c);

        c.gridy = 3;
        c.insets = new Insets(0, 0, 4, 0);
        content.add(row(I18n.tr("about.author"), UiKit.label(Main.AUTHOR, UiKit.BOLD, UiKit.TEXT)), c);

        c.gridy = 4;
        JButton mail = UiKit.link(Main.EMAIL);
        mail.setFont(UiKit.BASE);
        mail.setToolTipText(I18n.tr("about.mail.tip"));
        mail.addActionListener(e -> sendMail(d));
        content.add(row(I18n.tr("about.contact"), mail), c);

        c.gridy = 5;
        c.insets = new Insets(10, 0, 0, 0);
        content.add(UiKit.label(I18n.tr("about.java", System.getProperty("java.version", "?")),
                UiKit.SMALL, UiKit.SUBTLE), c);

        JButton close = new JButton(I18n.tr("about.close"));
        close.setFont(UiKit.BASE);
        close.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setBackground(UiKit.WINDOW);
        buttons.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiKit.BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        buttons.add(close);

        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(content, BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(close);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setMinimumSize(new Dimension(d.getWidth(), d.getHeight()));
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static JComponent row(String label, JComponent value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        JLabel l = UiKit.label(label, UiKit.BASE, UiKit.MUTED);
        l.setPreferredSize(new Dimension(Math.max(80, l.getPreferredSize().width + 10), l.getPreferredSize().height));
        p.add(l);
        p.add(value);
        return p;
    }

    /** Opens the mail program; if there is none, copies the address to the clipboard. */
    private static void sendMail(JDialog owner) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                String subject = URLEncoder.encode("ORKtoSCAD " + Main.VERSION, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                Desktop.getDesktop().mail(new URI("mailto:" + Main.EMAIL + "?subject=" + subject));
                return;
            }
        } catch (Exception e) {
            // no mail program: fall back to the clipboard
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Main.EMAIL), null);
            JOptionPane.showMessageDialog(owner, I18n.tr("about.copied", Main.EMAIL), I18n.tr("about.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(owner, Main.EMAIL, I18n.tr("about.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** The window icon in several sizes (title bar, task bar, Alt+Tab); empty if res/icon.png is missing. */
    static java.util.List<Image> windowIcons() {
        java.util.List<Image> icons = new java.util.ArrayList<>();
        try (InputStream in = HelpMenu.class.getResourceAsStream("res/icon.png")) {
            if (in == null) {
                return icons;
            }
            java.awt.image.BufferedImage src = ImageIO.read(in);
            for (int size : new int[] { 16, 20, 24, 32, 40, 48, 64, 128 }) {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = img.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(src.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();
                icons.add(img);
            }
        } catch (IOException e) {
            // no icon
        }
        return icons;
    }

    /** The application icon (res/icon.png), scaled; null if missing. */
    static Image icon(int size) {
        try (InputStream in = HelpMenu.class.getResourceAsStream("res/icon.png")) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in).getScaledInstance(size, size, Image.SCALE_SMOOTH);
        } catch (IOException e) {
            return null;
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
