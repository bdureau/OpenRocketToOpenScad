package orkToScad;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/**
 * Finds OpenSCAD on this computer, renders a .scad file to an image with it, and opens a file in it.
 * Without OpenSCAD, the window only shows the code.
 */
final class OpenScad {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final long TIMEOUT_SECONDS = 120;

    private OpenScad() {
    }

    /**
     * The OpenSCAD program: the one set by the user if it exists, otherwise the usual install
     * locations and the PATH. Returns null if not found.
     */
    static Path find(String userPath) {
        List<Path> candidates = new ArrayList<>();
        if (userPath != null) {
            candidates.add(Paths.get(userPath));
        }
        if (WINDOWS) {
            for (String env : new String[] { "ProgramFiles", "ProgramFiles(x86)", "ProgramW6432" }) {
                String dir = System.getenv(env);
                if (dir != null) {
                    candidates.add(Paths.get(dir, "OpenSCAD", "openscad.exe"));
                    candidates.add(Paths.get(dir, "OpenSCAD (Nightly)", "openscad.exe"));
                }
            }
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                candidates.add(Paths.get(local, "Programs", "OpenSCAD", "openscad.exe"));
            }
        } else {
            candidates.add(Paths.get("/Applications/OpenSCAD.app/Contents/MacOS/OpenSCAD"));
            candidates.add(Paths.get("/usr/bin/openscad"));
            candidates.add(Paths.get("/usr/local/bin/openscad"));
            candidates.add(Paths.get("/snap/bin/openscad"));
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    candidates.add(Paths.get(dir, WINDOWS ? "openscad.exe" : "openscad"));
                }
            }
        }
        for (Path p : candidates) {
            try {
                if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p.toAbsolutePath();
                }
            } catch (RuntimeException e) {
                // invalid path: ignore
            }
        }
        return null;
    }

    /** On Windows the console version (openscad.com) reports errors; use it for command-line work. */
    private static Path commandLine(Path exe) {
        if (WINDOWS && exe.getFileName().toString().equalsIgnoreCase("openscad.exe")) {
            Path com = exe.resolveSibling("openscad.com");
            if (Files.isRegularFile(com)) {
                return com;
            }
        }
        return exe;
    }

    /** Renders a .scad file to an image of about width x height pixels (blocking, call off the UI thread). */
    static BufferedImage render(Path exe, Path scad, int width, int height) throws IOException {
        Path png = Files.createTempFile("orktoscad-render", ".png");
        Path log = Files.createTempFile("orktoscad-render", ".log");
        try {
            List<String> cmd = List.of(commandLine(exe).toString(), "--render", "--autocenter", "--viewall",
                    "--colorscheme=Cornfield", "--imgsize=" + width + "," + height, "-o", png.toString(),
                    scad.toString());
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean done = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new I18n.LocalizedIOException(new I18n.Msg("scad.render.timeout", TIMEOUT_SECONDS));
            }
            BufferedImage img = Files.size(png) > 0 ? ImageIO.read(png.toFile()) : null;
            if (p.exitValue() != 0 || img == null) {
                String out = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).trim();
                String last = out.isEmpty() ? "code " + p.exitValue() : out.substring(out.lastIndexOf('\n') + 1);
                throw new I18n.LocalizedIOException(new I18n.Msg("scad.render.error", last));
            }
            return img;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new I18n.LocalizedIOException(new I18n.Msg("scad.render.interrupted"), e);
        } finally {
            Files.deleteIfExists(png);
            Files.deleteIfExists(log);
        }
    }

    /** Opens a file in OpenSCAD, or with the program associated to .scad files if OpenSCAD was not found. */
    static void open(Path exe, Path scad) throws IOException {
        if (exe != null) {
            new ProcessBuilder(exe.toString(), scad.toString()).start();
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(scad.toFile());
        } else {
            throw new I18n.LocalizedIOException(new I18n.Msg("scad.notInstalled"));
        }
    }
}
