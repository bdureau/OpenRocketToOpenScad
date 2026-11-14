package orkToScad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Tool for the user manual (not part of the application): opens the window in each language, converts a sample
 * design, selects a centering ring and saves a screenshot with numbered markers (window-xx.png).
 *
 * <p>Run on the class path (not as a module) so that it can reach the package-private classes:
 * {@code java -cp bin:docs/manual/tools/classes orkToScad.ManualScreenshots <sample.ork> <outDir> en fr ...}
 */
public final class ManualScreenshots {

    static final int WIDTH = 1360;
    static final int HEIGHT = 800;
    /** Must match -Dsun.java2d.uiScale given to the JVM (the screen must be WIDTH*SCALE x HEIGHT*SCALE or larger). */
    static final double SCALE = Double.parseDouble(System.getProperty("sun.java2d.uiScale", "1"));

    public static void main(String[] args) throws Exception {
        Path ork = Paths.get(args[0]).toAbsolutePath();
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        for (int i = 2; i < args.length; i++) {
            shoot(args[i], ork, outDir);
        }
        System.exit(0);
    }

    private static void shoot(String lang, Path ork, Path outDir) throws Exception {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "rockets");
        Files.createDirectories(dir);
        Path input = dir.resolve(ork.getFileName());
        Files.copy(ork, input, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String stem = input.getFileName().toString().replaceFirst("\\.ork$", "");
        Path conv = dir.resolve(stem + "_out");
        JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                I18n.setLanguage(lang);
                Locale.setDefault(I18n.locale());
                JComponent.setDefaultLocale(I18n.locale());
                Constructor<MainWindow> c = MainWindow.class.getDeclaredConstructor();
                c.setAccessible(true);
                MainWindow w = c.newInstance();
                w.setSize(WIDTH, HEIGHT);
                w.setLocation(0, 0);
                w.setVisible(true);
                call(w, "balanceSplits");
                Field switching = MainWindow.class.getDeclaredField("switching");
                switching.setAccessible(true);
                switching.setBoolean(w, true); // no language switch when the combo is set below
                ((JComboBox<?>) get(w, "languageCombo")).setSelectedItem(lang);
                ((javax.swing.JTextField) get(w, "inputField")).setText(input.toString());
                ((javax.swing.JTextField) get(w, "outputField")).setText(conv.toString());
                call(w, "convert");
                holder[0] = w;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        JFrame w = holder[0];
        // wait for the conversion (SwingWorker)
        for (int i = 0; i < 100 && get(w, "lastReport") == null; i++) {
            Thread.sleep(100);
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object panel = get(w, "partsPanel");
                @SuppressWarnings("unchecked")
                List<Part> parts = (List<Part>) get(panel, "parts");
                int ring = 0;
                for (int i = 0; i < parts.size(); i++) {
                    if (parts.get(i).kind == Part.Kind.CENTERINGRING) {
                        ring = i;
                        break;
                    }
                }
                ((PartsPanel) panel).selectPart(ring);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            try {
                save(w, outDir.resolve("window-" + lang + ".png").toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            w.dispose();
        });
    }

    private static void save(JFrame w, File file) throws Exception {
        JRootPane root = w.getRootPane();
        Point p = root.getLocationOnScreen();
        java.awt.image.MultiResolutionImage mr = new java.awt.Robot()
                .createMultiResolutionScreenCapture(new Rectangle(p.x, p.y, root.getWidth(), root.getHeight()));
        java.awt.Image best = null;
        for (java.awt.Image v : mr.getResolutionVariants()) {
            if (best == null || v.getWidth(null) > best.getWidth(null)) {
                best = v;
            }
        }
        BufferedImage img = new BufferedImage(best.getWidth(null), best.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(best, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale((double) img.getWidth() / root.getWidth(), (double) img.getHeight() / root.getHeight());

        // numbered markers
        Component menu = w.getJMenuBar().getMenu(0);
        Rectangle r;
        r = bounds(root, menu);
        marker(g, 1, r.x + r.width + 14, r.y + r.height / 2);
        r = bounds(root, (Component) get(w, "inputField"));
        marker(g, 2, r.x + 16, r.y + r.height);
        r = bounds(root, (Component) get(w, "outputField"));
        marker(g, 3, r.x + 16, r.y + r.height);
        r = bounds(root, (Component) get(w, "stlBox"));
        Rectangle r2 = bounds(root, (Component) get(w, "svgBox"));
        marker(g, 4, (r.x + r2.x + r2.width) / 2, r.y + r.height + 4);
        r = bounds(root, (Component) get(w, "convertButton"));
        marker(g, 5, r.x + r.width / 2, r.y + r.height + 4);
        r = bounds(root, findButton(root, I18n.tr("toolbar.openFolder")));
        marker(g, 6, r.x + r.width / 2, r.y + r.height + 4);
        r = bounds(root, (JComboBox<?>) get(w, "languageCombo"));
        marker(g, 7, r.x + r.width / 2, r.y + r.height + 4);
        r = bounds(root, (Component) get(w, "partsPanel"));
        marker(g, 8, r.x + 2, r.y + 22);
        r = bounds(root, (Component) get(w, "stlPreview"));
        marker(g, 9, r.x + 2, r.y + 22);
        r = bounds(root, (Component) get(w, "svgPreview"));
        marker(g, 10, r.x + 2, r.y + 22);
        r = bounds(root, (Component) get(w, "scadPreview"));
        marker(g, 11, r.x + 2, r.y + 22);
        r = bounds(root, (Component) get(w, "reportLink"));
        marker(g, 12, r.x + r.width + 16, r.y + r.height / 2);
        g.dispose();
        ImageIO.write(img, "png", file);
        System.out.println("written " + file);
    }

    private static void marker(Graphics2D g, int n, int cx, int cy) {
        int d = 22;
        g.setColor(Color.WHITE);
        g.fillOval(cx - d / 2 - 2, cy - d / 2 - 2, d + 4, d + 4);
        g.setColor(new Color(0xD9480F));
        g.fillOval(cx - d / 2, cy - d / 2, d, d);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        String s = String.valueOf(n);
        g.drawString(s, cx - fm.stringWidth(s) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
    }

    private static Rectangle bounds(Component root, Component c) {
        Point p = SwingUtilities.convertPoint(c.getParent(), c.getLocation(), root);
        return new Rectangle(p.x, p.y, c.getWidth(), c.getHeight());
    }

    private static Component findButton(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton) child).getText())) {
                return child;
            }
            if (child instanceof Container) {
                Component found = findButton((Container) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }
}
