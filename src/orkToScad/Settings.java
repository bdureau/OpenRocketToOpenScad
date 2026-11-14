package orkToScad;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * User settings (last files, options, OpenSCAD location), kept in {@code .orktoscad.properties}
 * in the user's home folder. Errors are ignored: settings are a convenience.
 */
final class Settings {

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".orktoscad.properties");
    private final Properties props = new Properties();

    Settings() {
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException | IllegalArgumentException e) {
                props.clear();
            }
        }
    }

    String get(String key) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? null : v;
    }

    boolean getBoolean(String key, boolean def) {
        String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    void put(String key, Object value) {
        if (value == null) {
            props.remove(key);
        } else {
            props.setProperty(key, value.toString());
        }
    }

    void save() {
        try (OutputStream out = Files.newOutputStream(FILE)) {
            props.store(out, "ORKtoSCAD settings");
        } catch (IOException e) {
            // not important
        }
    }
}
