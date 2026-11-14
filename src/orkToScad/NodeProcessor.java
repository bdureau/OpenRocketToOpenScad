package orkToScad;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Walks the OpenRocket XML tree and hands every supported component to the {@link ScadGenerator}.
 *
 * Components inside pod sets and boosters (parallel stages) are generated like the others.
 * Components that are not physical parts to make (parachutes, streamers, shock cords, mass
 * components) are listed in a note, and parts needed several times (pods, rail buttons, fins...)
 * get a note with the number of copies to make.
 */
public final class NodeProcessor {

    /** OpenRocket components that are not parts to print or cut. */
    private static final Set<String> NOT_MADE = Set.of("parachute", "streamer", "shockcord", "masscomponent");

    /** Components with a fin count. */
    private static final Set<String> FIN_SETS = Set.of("trapezoidfinset", "ellipticalfinset", "freeformfinset",
            "tubefinset");

    private final ScadGenerator generator;
    private final ConversionReport report;
    private final Map<String, Integer> ignored = new TreeMap<>();

    public NodeProcessor(ScadGenerator generator, ConversionReport report) {
        this.generator = generator;
        this.report = report;
    }

    /** Processes the whole design, then adds the notes about ignored components. */
    public void processDocument(Element root) {
        process(root);
        if (!ignored.isEmpty()) {
            List<Object> items = new java.util.ArrayList<>();
            ignored.forEach((tag, count) -> items.add(new I18n.Msg("note.item", new I18n.Msg("kind." + tag), count)));
            report.note(new I18n.Msg("note.notGenerated", new ListText(items)));
        }
    }

    private void process(Element element) {
        for (Element child : OrkValues.elements(element)) {
            boolean generated = true;
            int filesBefore = report.getGenerated().size();
            switch (child.getNodeName()) {
                case "nosecone":
                    generator.noseCone(child);
                    break;
                case "bodytube":
                    generator.bodyTube(child);
                    break;
                case "transition":
                    generator.transition(child);
                    break;
                case "tubecoupler":
                    generator.tubeCoupler(child);
                    break;
                case "freeformfinset":
                    generator.freeformFinset(child);
                    break;
                case "ellipticalfinset":
                    generator.ellipticalFinset(child);
                    break;
                case "trapezoidfinset":
                    generator.trapezoidFinset(child);
                    break;
                case "tubefinset":
                    generator.tubeFinSet(child);
                    break;
                case "centeringring":
                    generator.centeringRing(child);
                    break;
                case "bulkhead":
                    generator.bulkhead(child);
                    break;
                case "innertube":
                    generator.innerTube(child);
                    break;
                case "engineblock":
                    generator.engineBlock(child);
                    break;
                case "launchlug":
                    generator.launchLug(child);
                    break;
                case "railbutton":
                    generator.railButton(child);
                    break;
                default:
                    generated = false;
                    if (NOT_MADE.contains(child.getNodeName()) && OrkValues.child(child, "name") != null) {
                        ignored.merge(child.getNodeName(), 1, Integer::sum);
                    }
                    break;
            }
            if (generated) {
                int copies = copies(child);
                noteCopies(child, copies);
                List<Path> files = report.getGenerated().subList(filesBefore, report.getGenerated().size());
                if (!files.isEmpty()) {
                    report.part(new ConversionReport.PartInfo(child.getNodeName(), OrkValues.text(child, "name"),
                            OrkValues.text(child, "shape"), files, copies));
                }
            }
            process(child);
        }
    }

    /** How many times a part must be made (own count x enclosing pods / boosters). */
    private static int copies(Element comp) {
        int copies = ownCount(comp);
        for (Node n = comp.getParentNode(); n instanceof Element; n = n.getParentNode()) {
            String tag = n.getNodeName();
            if (tag.equals("podset") || tag.equals("parallelstage") || tag.equals("boosterset")) {
                copies *= count((Element) n, "instancecount");
            }
        }
        return copies;
    }

    /** Adds a note when a part must be made several times. */
    private void noteCopies(Element comp, int copies) {
        if (copies > 1) {
            report.note(new I18n.Msg(FIN_SETS.contains(comp.getNodeName()) ? "note.fins" : "note.copies",
                    ScadGenerator.partRef(comp), copies));
        }
    }

    /** A list of messages joined with ", ", translated when displayed. */
    private static final class ListText {
        private final List<Object> items;

        ListText(List<Object> items) {
            this.items = items;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : items) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(o);
            }
            return sb.toString();
        }
    }

    private static int ownCount(Element comp) {
        if (FIN_SETS.contains(comp.getNodeName())) {
            int fins = count(comp, "fincount");
            return fins > 1 ? fins : count(comp, "instancecount");
        }
        return count(comp, "instancecount");
    }

    /** Positive integer child value, 1 if absent or invalid. */
    private static int count(Element e, String tag) {
        String raw = OrkValues.text(e, tag);
        if (raw == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
