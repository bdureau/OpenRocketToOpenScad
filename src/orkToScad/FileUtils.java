package orkToScad;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FileUtils {

    /** Folder (inside the jar, or next to it) holding the OpenSCAD nose cone templates. */
    static final String TEMPLATE_DIR = "openscad";

    private FileUtils() {
    }

    /**
     * Number format for OpenSCAD source. Always uses '.' as decimal separator, whatever the
     * system language (a French/German locale would otherwise produce "12,50", which is a
     * syntax error in OpenSCAD). DecimalFormat is not thread-safe: one instance per call.
     */
    public static String fmt(double value) {
        DecimalFormat df = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String s = df.format(value);
        return s.equals("-0.00") ? "0.00" : s;
    }

    /** Writes a text file in UTF-8 (trailing newline added, as before). */
    public static void writeFile(Path file, String content) throws IOException {
        Files.writeString(file, content + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    /**
     * Loads an OpenSCAD template by file name (e.g. "noseCone-Ogive.scad").
     * Lookup order:
     * <ol>
     * <li>inside the jar / classpath ({@code /openscad/...}),</li>
     * <li>an {@code openscad} folder next to the jar (lets users customise templates),</li>
     * <li>an {@code openscad} folder in the current working directory (running from an IDE).</li>
     * </ol>
     * Paths are built with {@link Path#resolve}, so this works on Windows, Linux and macOS.
     */
    public static String loadTemplate(String fileName) throws IOException {
        try (InputStream in = FileUtils.class.getResourceAsStream("/" + TEMPLATE_DIR + "/" + fileName)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path nextToJar = jarDirectory();
        if (nextToJar != null) {
            Path p = nextToJar.resolve(TEMPLATE_DIR).resolve(fileName);
            if (Files.isRegularFile(p)) {
                return Files.readString(p, StandardCharsets.UTF_8);
            }
        }
        Path p = Paths.get(TEMPLATE_DIR).resolve(fileName);
        if (Files.isRegularFile(p)) {
            return Files.readString(p, StandardCharsets.UTF_8);
        }
        throw new IOException("OpenSCAD template not found: " + TEMPLATE_DIR + "/" + fileName);
    }

    private static Path jarDirectory() {
        try {
            Path location = Paths.get(FileUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
