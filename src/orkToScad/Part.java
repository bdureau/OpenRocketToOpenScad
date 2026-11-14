package orkToScad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One part shown in the window: a component of the rocket and its files (STL, SVG, OpenSCAD).
 * Built from a conversion report (with the names given in OpenRocket) or from the files of an
 * existing output folder (names deduced from the file names).
 */
public final class Part {

    /** Kind of component: group in the list, label, order. */
    enum Kind {
        NOSECONE("nosecone", "noseCone", "group.nosecone"),
        TRANSITION("transition", "transition", "group.transitions"),
        BODYTUBE("bodytube", "body-tube", "group.tubes"),
        TUBECOUPLER("tubecoupler", "tube-coupler", "group.tubes"),
        INNERTUBE("innertube", "inner-tube", "group.tubes"),
        ENGINEBLOCK("engineblock", "engine-block", "group.tubes"),
        CENTERINGRING("centeringring", "centeringring", "group.rings"),
        BULKHEAD("bulkhead", "bulkhead", "group.rings"),
        TRAPEZOIDFINSET("trapezoidfinset", "trapezoidfinset", "group.fins"),
        ELLIPTICALFINSET("ellipticalfinset", "ellipticalfinset", "group.fins"),
        FREEFORMFINSET("freeformfinset", "freeformfinset", "group.fins"),
        TUBEFINSET("tubefinset", "tube-fin", "group.fins"),
        LAUNCHLUG("launchlug", "launch-lug", "group.guides"),
        RAILBUTTON("railbutton", "rail-button", "group.guides");

        final String tag;
        final String filePrefix;
        /** Key of the group title (translated). */
        final String groupKey;

        Kind(String tag, String filePrefix, String groupKey) {
            this.tag = tag;
            this.filePrefix = filePrefix;
            this.groupKey = groupKey;
        }

        /** Name of this kind of part, in the current language. */
        String label() {
            return I18n.tr("kind." + tag);
        }

        static Kind ofTag(String tag) {
            for (Kind k : values()) {
                if (k.tag.equals(tag)) {
                    return k;
                }
            }
            return null;
        }

        /** Group keys in display order. */
        static List<String> groups() {
            List<String> g = new ArrayList<>();
            for (Kind k : values()) {
                if (!g.contains(k.groupKey)) {
                    g.add(k.groupKey);
                }
            }
            return g;
        }
    }

    final Kind kind;
    final String title;
    final String subtitle;
    final List<Path> files;
    final int copies;

    private Part(Kind kind, String title, String subtitle, List<Path> files, int copies) {
        this.kind = kind;
        this.title = title;
        this.subtitle = subtitle;
        this.files = files;
        this.copies = copies;
    }

    Path stl() {
        return first(".stl");
    }

    Path svg() {
        return first(".svg");
    }

    /** OpenSCAD files of the part, the 3D one first. */
    List<Path> scadFiles() {
        return files.stream().filter(f -> name(f).endsWith(".scad"))
                .sorted(Comparator.comparing(f -> name(f).toLowerCase(Locale.ROOT).contains("cnc") ? 1 : 0))
                .collect(Collectors.toList());
    }

    boolean has(String ext) {
        return first(ext) != null;
    }

    private Path first(String ext) {
        for (Path f : files) {
            if (name(f).endsWith(ext)) {
                return f;
            }
        }
        return null;
    }

    private static String name(Path f) {
        return f.getFileName().toString();
    }

    // ------------------------------------------------------------------ from a conversion

    /** Parts of a conversion, with OpenRocket's names; subtitles are numbered per kind ("Bague de centrage 2"). */
    static List<Part> fromReport(ConversionReport report) {
        List<Part> parts = new ArrayList<>();
        Map<Kind, Integer> numbers = new HashMap<>();
        Map<Kind, Long> totals = report.getParts().stream().map(p -> Kind.ofTag(p.tag))
                .filter(k -> k != null).collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        for (ConversionReport.PartInfo info : report.getParts()) {
            Kind kind = Kind.ofTag(info.tag);
            if (kind == null) {
                continue;
            }
            int n = numbers.merge(kind, 1, Integer::sum);
            String subtitle = kind.label();
            if (kind == Kind.NOSECONE || kind == Kind.TRANSITION) {
                String shape = OrShapes.canonical(info.detail);
                subtitle = shape == null ? kind.label() : kind.label() + " \u00b7 " + I18n.tr("shape." + shape);
            }
            if (totals.getOrDefault(kind, 1L) > 1) {
                subtitle += " " + n;
            }
            String title = info.name == null || info.name.isBlank() ? kind.label() + " " + n : info.name;
            parts.add(new Part(kind, title, subtitle, info.files, info.copies));
        }
        return sorted(parts);
    }

    // ------------------------------------------------------------------ from an existing folder

    /** prefix, optional -3d / -cnc / -CNC / 3D, optional -, number */
    private static final Pattern FILE_NAME = Pattern.compile("^(.+?)(-3d|-cnc|-CNC|3D)?-?(\\d*)$");

    /** Parts deduced from the .stl / .svg / .scad files of a folder (names from the file names). */
    static List<Part> fromFolder(Path dir) throws IOException {
        Map<String, List<Path>> byKey = new LinkedHashMap<>();
        Map<String, Kind> kinds = new HashMap<>();
        Map<String, String> numbers = new HashMap<>();
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : s.sorted().collect(Collectors.toList())) {
                String n = name(f);
                int dot = n.lastIndexOf('.');
                if (dot < 0 || !Files.isRegularFile(f)) {
                    continue;
                }
                String ext = n.substring(dot).toLowerCase(Locale.ROOT);
                if (!ext.equals(".stl") && !ext.equals(".svg") && !ext.equals(".scad")) {
                    continue;
                }
                Matcher m = FILE_NAME.matcher(n.substring(0, dot));
                if (!m.matches()) {
                    continue;
                }
                Kind kind = kindOfPrefix(m.group(1));
                if (kind == null) {
                    continue;
                }
                String number = m.group(3).isEmpty() ? "1" : m.group(3);
                String key = m.group(1) + "#" + number;
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
                kinds.put(key, kind);
                numbers.put(key, number);
            }
        }
        List<Part> parts = new ArrayList<>();
        for (Map.Entry<String, List<Path>> e : byKey.entrySet()) {
            Kind kind = kinds.get(e.getKey());
            String prefix = e.getKey().substring(0, e.getKey().indexOf('#'));
            String subtitle = kind == Kind.NOSECONE && prefix.length() > "noseCone-".length()
                    ? I18n.tr("part.shapeFromFile", prefix.substring("noseCone-".length()))
                    : prefix;
            parts.add(new Part(kind, kind.label() + " " + numbers.get(e.getKey()), subtitle, e.getValue(), 1));
        }
        return sorted(parts);
    }

    private static Kind kindOfPrefix(String prefix) {
        if (prefix.startsWith("noseCone")) {
            return Kind.NOSECONE;
        }
        for (Kind k : Kind.values()) {
            if (k.filePrefix.equals(prefix)) {
                return k;
            }
        }
        return null;
    }

    /** Stable sort by group order (parts keep the design order inside a group). */
    private static List<Part> sorted(List<Part> parts) {
        List<String> groups = Kind.groups();
        List<Part> out = new ArrayList<>(parts);
        out.sort(Comparator.comparingInt(p -> groups.indexOf(p.kind.groupKey)));
        return out;
    }
}
