package orkToScad;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects what a conversion produced, so problems are reported instead of being swallowed. */
public final class ConversionReport {

    private final List<Path> generated = new ArrayList<>();
    /** Messages are kept as {@link I18n.Msg} (or plain text) and translated when they are read. */
    private final List<Object> warnings = new ArrayList<>();
    private final List<Object> errors = new ArrayList<>();
    private final List<Object> notes = new ArrayList<>();
    private final List<PartInfo> parts = new ArrayList<>();

    /** One OpenRocket component and the files generated for it. */
    public static final class PartInfo {
        /** OpenRocket element name, e.g. "centeringring". */
        public final String tag;
        /** Name given in OpenRocket, or null. */
        public final String name;
        /** Extra description (nose cone / transition shape), or null. */
        public final String detail;
        /** Files generated for this component (.scad, .stl, .svg). */
        public final List<Path> files;
        /** How many to make (pods, boosters, instance count, fin count). */
        public final int copies;

        PartInfo(String tag, String name, String detail, List<Path> files, int copies) {
            this.tag = tag;
            this.name = name;
            this.detail = detail;
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.copies = copies;
        }
    }

    void part(PartInfo part) {
        parts.add(part);
    }

    /** Generated parts, in the order of the design. */
    public List<PartInfo> getParts() {
        return Collections.unmodifiableList(parts);
    }

    void generated(Path file) {
        generated.add(file);
    }

    void warn(Object message) {
        warnings.add(message);
    }

    /** Information that is neither a problem nor a generated file. */
    void note(Object message) {
        notes.add(message);
    }

    void error(Object message) {
        errors.add(message);
    }

    /** An exception's message: kept translatable when it carries one. */
    void error(Exception e) {
        errors.add(e instanceof I18n.LocalizedIOException ? ((I18n.LocalizedIOException) e).msg() : e.getMessage());
    }

    public List<String> getNotes() {
        return texts(notes);
    }

    public List<Path> getGenerated() {
        return Collections.unmodifiableList(generated);
    }

    /** Warnings, in the current language. */
    public List<String> getWarnings() {
        return texts(warnings);
    }

    /** Errors, in the current language. */
    public List<String> getErrors() {
        return texts(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    private static List<String> texts(List<Object> messages) {
        List<String> out = new ArrayList<>();
        for (Object m : messages) {
            out.add(String.valueOf(m));
        }
        return Collections.unmodifiableList(out);
    }

    /** Full report, in the current language. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.tr("report.generated", I18n.plural("count.files", generated.size())));
        if (!warnings.isEmpty()) {
            sb.append(", ").append(I18n.plural("count.warnings", warnings.size()));
        }
        if (!errors.isEmpty()) {
            sb.append(", ").append(I18n.plural("count.errors", errors.size()));
        }
        sb.append('.');
        for (String e : getErrors()) {
            sb.append('\n').append(I18n.tr("report.error", e));
        }
        for (String w : getWarnings()) {
            sb.append('\n').append(I18n.tr("report.warning", w));
        }
        for (String n : getNotes()) {
            sb.append('\n').append(I18n.tr("report.note", n));
        }
        return sb.toString();
    }
}
