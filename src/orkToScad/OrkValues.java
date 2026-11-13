package orkToScad;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Helpers to read dimensions from OpenRocket component elements and to resolve
 * values that OpenRocket stores as {@code auto}.
 *
 * All lengths in an .ork file are in metres; every method here returns millimetres.
 * A value that is missing, unparsable or cannot be resolved is returned as {@link Double#NaN}
 * so callers can refuse to generate a part instead of silently writing zeros.
 *
 * How {@code auto} values are resolved (same rules OpenRocket uses, simplified):
 * <ul>
 * <li>Body tube radius: aft radius of the previous external component, otherwise fore radius of the next one.</li>
 * <li>Nose cone aft radius / transition radii: radius of the adjacent external component.</li>
 * <li>Tube coupler, bulkhead, centering ring outer radius: inner radius of the tube that contains it.</li>
 * <li>Centering ring inner radius: largest outer radius of the inner tubes mounted in the same tube
 *     (0, i.e. a solid disk, if there is none).</li>
 * <li>Nose cone shoulder radius: inner radius of the component that follows the nose cone.</li>
 * </ul>
 * Newer OpenRocket versions write {@code auto 0.0125} (keyword followed by the computed value);
 * in that case the stored value is used directly.
 */
public final class OrkValues {

    private static final Set<String> EXTERNAL = Set.of("nosecone", "bodytube", "transition");

    private OrkValues() {
    }

    // ------------------------------------------------------------------ basic access

    /** First direct child element with the given name, or null. */
    public static Element child(Element e, String name) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    /** Trimmed text of a direct child element, or null if absent. */
    public static String text(Element e, String name) {
        Element c = child(e, name);
        return c == null ? null : c.getTextContent().trim();
    }

    public static boolean isAuto(String raw) {
        return raw != null && raw.startsWith("auto");
    }

    /**
     * Converts a raw .ork length (metres) to millimetres. Handles "auto &lt;value&gt;".
     * Returns NaN for null, plain "auto" or unparsable text.
     */
    public static double mm(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.trim();
        if (s.startsWith("auto")) {
            s = s.substring(4).trim();
            if (s.isEmpty()) {
                return Double.NaN;
            }
        }
        try {
            double v = Double.parseDouble(s) * 1000.0;
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /** Length in mm of a direct child, or NaN. */
    public static double mm(Element e, String name) {
        return mm(text(e, name));
    }

    /** Length in mm of a direct child, or {@code def} when the child is absent. NaN if present but invalid. */
    public static double mmOr(Element e, String name, double def) {
        String raw = text(e, name);
        return raw == null ? def : mm(raw);
    }

    /** List of child elements of a node. */
    public static List<Element> elements(Node parent) {
        List<Element> out = new ArrayList<>();
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) list.item(i));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ auto resolution

    /** Outer radius of a body tube (mm), resolving "auto". */
    public static double bodyTubeRadius(Element bodyTube) {
        return bodyTubeRadius(bodyTube, new HashSet<>());
    }

    /** Aft (bottom) radius of a nose cone (mm), resolving "auto". */
    public static double noseConeAftRadius(Element noseCone) {
        return aftRadius(noseCone, new HashSet<>());
    }

    /** Shoulder radius of a nose cone (mm), resolving "auto". */
    public static double noseConeShoulderRadius(Element noseCone) {
        String raw = text(noseCone, "aftshoulderradius");
        if (raw == null) {
            return 0.0; // no shoulder
        }
        double v = mm(raw);
        if (!Double.isNaN(v) || !isAuto(raw)) {
            return v;
        }
        Element next = neighbour(noseCone, +1);
        return next == null ? Double.NaN : innerRadiusOf(next, new HashSet<>());
    }

    /** Outer radius of a component that sits inside a tube (coupler, bulkhead, centering ring). */
    public static double outerRadiusInsideParent(Element comp, String tag) {
        String raw = text(comp, tag);
        double v = mm(raw);
        if (!Double.isNaN(v) || !isAuto(raw)) {
            return v;
        }
        Element parent = parentComponent(comp);
        return parent == null ? Double.NaN : innerRadiusOf(parent, new HashSet<>());
    }

    /**
     * Inner radius of a centering ring, resolving "auto" like OpenRocket: largest outer radius of the
     * inner tubes mounted in the same parent that overlap the ring along the rocket axis and fit inside it.
     */
    public static double centeringRingInnerRadius(Element ring, double ringOuterRadius) {
        String raw = text(ring, "innerradius");
        double v = mm(raw);
        if (!Double.isNaN(v) || !isAuto(raw)) {
            return v;
        }
        double[] ringSpan = axialSpan(ring);
        double max = 0.0;
        for (Element sibling : elements(ring.getParentNode())) {
            if (!sibling.getNodeName().equals("innertube")) {
                continue;
            }
            double r = mm(sibling, "outerradius");
            if (Double.isNaN(r) || !(r < ringOuterRadius)) {
                continue;
            }
            double[] tubeSpan = axialSpan(sibling);
            boolean overlaps = ringSpan == null || tubeSpan == null
                    || (tubeSpan[0] < ringSpan[1] && ringSpan[0] < tubeSpan[1]);
            if (overlaps) {
                max = Math.max(max, r);
            }
        }
        return max;
    }

    /**
     * Axial extent {top, bottom} (mm) of a component relative to the top of its parent,
     * or null when it cannot be determined.
     */
    private static double[] axialSpan(Element comp) {
        Element posEl = child(comp, "position");
        double len = mm(comp, "length");
        Element parent = parentComponent(comp);
        if (posEl == null || Double.isNaN(len) || parent == null) {
            return null;
        }
        double pos = mm(posEl.getTextContent());
        double parentLen = mm(parent, "length");
        if (Double.isNaN(pos)) {
            return null;
        }
        double top;
        switch (posEl.getAttribute("type")) {
            case "top":
                top = pos;
                break;
            case "bottom":
                top = parentLen - len + pos;
                break;
            case "middle":
                top = (parentLen - len) / 2 + pos;
                break;
            default: // "absolute", "after": not needed for parts inside a tube
                return null;
        }
        return Double.isNaN(top) ? null : new double[] { top, top + len };
    }

    // ------------------------------------------------------------------ internals

    private static double bodyTubeRadius(Element bt, Set<Element> visiting) {
        String raw = text(bt, "radius");
        if (raw == null) {
            raw = text(bt, "outerradius"); // tolerate alternative tag name
        }
        double v = mm(raw);
        if (!Double.isNaN(v) || !isAuto(raw)) {
            return v;
        }
        if (!visiting.add(bt)) {
            return Double.NaN; // cycle of auto values
        }
        Element prev = neighbour(bt, -1);
        double r = prev == null ? Double.NaN : aftRadius(prev, visiting);
        if (Double.isNaN(r)) {
            Element next = neighbour(bt, +1);
            r = next == null ? Double.NaN : foreRadius(next, visiting);
        }
        return r;
    }

    private static double foreRadius(Element c, Set<Element> visiting) {
        switch (c.getNodeName()) {
            case "nosecone":
                return 0.0;
            case "bodytube":
                return bodyTubeRadius(c, visiting);
            case "transition":
                return transitionRadius(c, "foreradius", -1, visiting);
            default:
                return Double.NaN;
        }
    }

    private static double aftRadius(Element c, Set<Element> visiting) {
        switch (c.getNodeName()) {
            case "nosecone":
                return transitionRadius(c, "aftradius", +1, visiting);
            case "bodytube":
                return bodyTubeRadius(c, visiting);
            case "transition":
                return transitionRadius(c, "aftradius", +1, visiting);
            default:
                return Double.NaN;
        }
    }

    /** Nose cones and transitions: an auto radius takes the radius of the neighbour on that side. */
    private static double transitionRadius(Element c, String tag, int direction, Set<Element> visiting) {
        String raw = text(c, tag);
        double v = mm(raw);
        if (!Double.isNaN(v) || !isAuto(raw)) {
            return v;
        }
        if (!visiting.add(c)) {
            return Double.NaN;
        }
        Element n = neighbour(c, direction);
        if (n == null) {
            return Double.NaN;
        }
        return direction > 0 ? foreRadius(n, visiting) : aftRadius(n, visiting);
    }

    /** Inner radius of a tube-like component, used for parts mounted inside it. */
    private static double innerRadiusOf(Element c, Set<Element> visiting) {
        switch (c.getNodeName()) {
            case "bodytube":
                return bodyTubeRadius(c, visiting) - mmOr(c, "thickness", 0.0);
            case "innertube":
                return mm(c, "outerradius") - mmOr(c, "thickness", 0.0);
            case "tubecoupler": {
                double outer = outerRadiusInsideParent(c, "outerradius");
                return outer - mmOr(c, "thickness", 0.0);
            }
            case "nosecone": {
                double shoulder = noseConeShoulderRadius(c);
                return shoulder - mmOr(c, "aftshoulderthickness", 0.0);
            }
            default:
                return Double.NaN;
        }
    }

    /** The component whose {@code <subcomponents>} contains {@code comp}, or null. */
    private static Element parentComponent(Element comp) {
        Node sub = comp.getParentNode();
        if (sub == null || !sub.getNodeName().equals("subcomponents")) {
            return null;
        }
        Node p = sub.getParentNode();
        return p instanceof Element ? (Element) p : null;
    }

    /**
     * Previous (direction -1) or next (+1) external component (nose cone, body tube, transition)
     * along the rocket, crossing stage boundaries.
     */
    private static Element neighbour(Element comp, int direction) {
        List<Element> chain = externalChain(comp);
        int i = chain.indexOf(comp);
        if (i < 0) {
            return null;
        }
        int j = i + direction;
        return (j >= 0 && j < chain.size()) ? chain.get(j) : null;
    }

    /** All external components of the rocket in document (= nose-to-tail) order. */
    private static List<Element> externalChain(Element anyComp) {
        List<Element> out = new ArrayList<>();
        Element root = anyComp.getOwnerDocument().getDocumentElement();
        collectExternal(root, out);
        return out;
    }

    private static void collectExternal(Element e, List<Element> out) {
        for (Element c : elements(e)) {
            if (EXTERNAL.contains(c.getNodeName()) && isInStage(c)) {
                out.add(c);
            }
            collectExternal(c, out);
        }
    }

    /** Direct child of a stage's subcomponents (i.e. part of the outer airframe). */
    private static boolean isInStage(Element c) {
        Node sub = c.getParentNode();
        return sub != null && sub.getNodeName().equals("subcomponents")
                && sub.getParentNode() != null && sub.getParentNode().getNodeName().equals("stage");
    }
}
