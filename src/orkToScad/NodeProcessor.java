package orkToScad;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NodeProcessor {
    private static int DEPTH_XML = 0;

    public static void printNode(NodeList nodeList, int level, String outPath) {
        level++;

        if (nodeList != null && nodeList.getLength() > 0) {
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                System.out.println("NodeName:" + node.getNodeName());

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    switch (node.getNodeName()) {
                        case "nosecone":
                            ScadGenerator.nodeNoseCone(node, outPath);
                            break;
                        case "bodytube":
                            ScadGenerator.bodyTubeIner = ScadGenerator.nodeBodyTube(node, outPath);
                            break;
                        case "tubecoupler":
                            ScadGenerator.nodeTubeCoupler(node, ScadGenerator.bodyTubeIner, outPath);
                            break;
                        case "freeformfinset":
                            ScadGenerator.nodeFreeFormFinset(node, outPath);
                            break;
                        case "ellipticalfinset":
                            ScadGenerator.nodeEllipticalFinset(node, outPath);
                            break;
                        case "trapezoidfinset":
                            ScadGenerator.nodeTrapezoidFinset(node, outPath);
                            break;
                        case "centeringring":
                            ScadGenerator.nodeCenteringRing(node, outPath);
                            break;
                        case "bulkhead":
                            ScadGenerator.nodeBulkhead(node, outPath);
                            break;
                        case "innertube":
                            ScadGenerator.nodeInnerTube(node, outPath);
                            break;
                        default:
                            break;
                    }
                    printNode(node.getChildNodes(), level, outPath);
                    if (level > DEPTH_XML) {
                        DEPTH_XML = level;
                    }
                }
            }
        }
    }
}
