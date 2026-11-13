package orkToScad;

import org.w3c.dom.Element;

/** Walks the OpenRocket XML tree and hands every supported component to the {@link ScadGenerator}. */
public final class NodeProcessor {

    private final ScadGenerator generator;

    public NodeProcessor(ScadGenerator generator) {
        this.generator = generator;
    }

    public void process(Element element) {
        for (Element child : OrkValues.elements(element)) {
            switch (child.getNodeName()) {
                case "nosecone":
                    generator.noseCone(child);
                    break;
                case "bodytube":
                    generator.bodyTube(child);
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
                case "centeringring":
                    generator.centeringRing(child);
                    break;
                case "bulkhead":
                    generator.bulkhead(child);
                    break;
                case "innertube":
                    generator.innerTube(child);
                    break;
                default:
                    break;
            }
            process(child);
        }
    }
}
