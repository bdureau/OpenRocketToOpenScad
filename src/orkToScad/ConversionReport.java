package orkToScad;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects what a conversion produced, so problems are reported instead of being swallowed. */
public final class ConversionReport {

    private final List<Path> generated = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    void generated(Path file) {
        generated.add(file);
    }

    void warn(String message) {
        warnings.add(message);
    }

    void error(String message) {
        errors.add(message);
    }

    public List<Path> getGenerated() {
        return Collections.unmodifiableList(generated);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(generated.size()).append(" file(s) generated");
        if (!warnings.isEmpty()) {
            sb.append(", ").append(warnings.size()).append(" warning(s)");
        }
        if (!errors.isEmpty()) {
            sb.append(", ").append(errors.size()).append(" error(s)");
        }
        sb.append('.');
        for (String e : errors) {
            sb.append("\nERROR: ").append(e);
        }
        for (String w : warnings) {
            sb.append("\nWARNING: ").append(w);
        }
        return sb.toString();
    }
}
