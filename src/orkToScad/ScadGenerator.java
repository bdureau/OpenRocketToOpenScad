package orkToScad;

import org.w3c.dom.*;
import java.text.DecimalFormat;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

public class ScadGenerator {
    private static DecimalFormat df = new DecimalFormat("0.00");
    public static float bodyTubeIner = 0;
    private static int bulkheadCount = 0;
    private static int tubeCouplerCount = 0;
    private static int bodyTubeCount = 0;
    private static int noseConeCount = 0;
    private static int innerTubeCount = 0;
    private static int freeformfinsetCount = 0;
    private static int ellipticalfinsetCount = 0;
    private static int trapezoidfinsetCount = 0;
    private static int centeringringCount = 0;

    public static void nodeBulkhead(Node node, String outPath) {
        bulkheadCount++;
        String fileName = outPath + "bulkhead-cnc" + bulkheadCount + ".scad";
        String fileName3D = outPath + "bulkhead-3d" + bulkheadCount + ".scad";
        String fileContent = "";
        String fileContent3D = "";
        float outerradius = 0;
        float length = 0;
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("outerradius")) {
                    try {
                        outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        outerradius = 0;
                        System.out.println("outerradius error");
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
            }
        }
        fileContent = "circle(r=" + df.format(outerradius) + ", $fn=200);\n";
        fileContent3D = "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length) + ", $fn=200);\n";
        FileUtils.writeFile(fileName, fileContent);
        FileUtils.writeFile(fileName3D, fileContent3D);
    }

    public static void nodeTubeCoupler(Node node, float bodyTubeIner, String outPath) {
        tubeCouplerCount++;
        String fileName3D = outPath + "tube-coupler-3d" + tubeCouplerCount + ".scad";
        String fileContent3D = "";
        float length = 0;
        float outerradius = 0;
        float thickness = 0;
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("outerradius")) {
                    if (childNode.getTextContent().equals("auto")) {
                        outerradius = bodyTubeIner;
                    } else {
                        try {
                            outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                        } catch (NumberFormatException e) {
                            outerradius = 0;
                            System.out.println("outerradius error");
                        }
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
                if (childNode.getNodeName().equals("thickness")) {
                    try {
                        thickness = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        thickness = 0;
                        System.out.println("thickness error");
                    }
                }
            }
        }
        fileContent3D = "difference() {\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length) + ", $fn=200);\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(length)
                + ", $fn=200);\n";
        fileContent3D += "}";
        FileUtils.writeFile(fileName3D, fileContent3D);
    }

    public static float nodeBodyTube(Node node, String outPath) {
        bodyTubeCount++;
        String fileName3D = outPath + "body-tube-3d" + bodyTubeCount + ".scad";
        String fileContent3D = "";
        float length = 0;
        float outerradius = 0;
        float thickness = 0;
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("radius")) {
                    try {
                        outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        outerradius = 0;
                        System.out.println("outerradius error");
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
                if (childNode.getNodeName().equals("thickness")) {
                    try {
                        thickness = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        thickness = 0;
                        System.out.println("thickness error");
                    }
                }
            }
        }
        fileContent3D = "difference() {\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length) + ", $fn=200);\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(length)
                + ", $fn=200);\n";
        fileContent3D += "}";
        FileUtils.writeFile(fileName3D, fileContent3D);
        return outerradius - thickness;
    }

    public static void nodeInnerTube(Node node, String outPath) {
        innerTubeCount++;
        String fileName3D = outPath + "inner-tube-3d" + innerTubeCount + ".scad";
        String fileContent3D = "";
        float length = 0;
        float outerradius = 0;
        float thickness = 0;
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("outerradius")) {
                    try {
                        outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        outerradius = 0;
                        System.out.println("outerradius error");
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
                if (childNode.getNodeName().equals("thickness")) {
                    try {
                        thickness = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        thickness = 0;
                        System.out.println("thickness error");
                    }
                }
            }
        }
        fileContent3D = "difference() {\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length) + ", $fn=200);\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(length)
                + ", $fn=200);\n";
        fileContent3D += "}";
        FileUtils.writeFile(fileName3D, fileContent3D);
    }

    public static void nodeNoseCone(Node node, String outPath) {
        noseConeCount++;
        String fileName3D = outPath + "nose-cone-3d" + noseConeCount + ".scad";
        float length = 0;
        float aftradius = 0;
        float thickness = 0;
        float aftshoulderradius = 0;
        float aftshoulderlength = 0;
        float aftshoulderthickness = 0;
        String shape = "ogive";
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("aftradius")) {
                    try {
                        aftradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        aftradius = 0;
                        System.out.println("aftradius error");
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
                if (childNode.getNodeName().equals("thickness")) {
                    try {
                        thickness = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        thickness = 0;
                        System.out.println("thickness error");
                    }
                }
                if (childNode.getNodeName().equals("aftshoulderradius")) {
                    try {
                        aftshoulderradius = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        aftshoulderradius = 0;
                        System.out.println("aftshoulderradius error");
                    }
                }
                if (childNode.getNodeName().equals("aftshoulderlength")) {
                    try {
                        aftshoulderlength = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        aftshoulderlength = 0;
                        System.out.println("aftshoulderlength error");
                    }
                }
                if (childNode.getNodeName().equals("aftshoulderthickness")) {
                    try {
                        aftshoulderthickness = 1000.0f * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        aftshoulderthickness = 0;
                        System.out.println("aftshoulderthickness error");
                    }
                }
                if (childNode.getNodeName().equals("shape")) {
                    shape = childNode.getTextContent();
                }
            }
        }
        switch (shape) {
            case "conical":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "Conical", outPath);
                break;
            case "ogive":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "Ogive", outPath);
                break;
            case "ellipsoid":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "Ellipsoid", outPath);
                break;
            case "powerseries":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "PowerSeries", outPath);
                break;
            case "parabolicseries":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "ParabolicSeries", outPath);
                break;
            case "haackseries":
                noseConeAndShoulder(aftradius, length, thickness,
                        aftshoulderradius, aftshoulderlength, aftshoulderthickness, 0.3f, "HaackSeries", outPath);
                break;
        }
    }

    public static void nodeFreeFormFinset(Node node, String outPath) {
        freeformfinsetCount++;
        String fileName = outPath + "freeformfinset" + freeformfinsetCount + ".scad";
        String fileContent = "";
        float tabheight = 0;
        float tablength = 0;
        float tabposition = 0;
        float rootchord = 0;
        String tabposition_relativeto = "";
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("tabheight")) {
                    try {
                        tabheight = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabheight = 0;
                        System.out.println("tabheight error");
                    }
                }
                if (childNode.getNodeName().equals("tablength")) {
                    try {
                        tablength = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tablength = 0;
                        System.out.println("tablength error");
                    }
                }
                if (childNode.getNodeName().equals("tabposition")) {
                    try {
                        tabposition = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabposition = 0;
                        System.out.println("tabposition error");
                    }
                    tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                }
                if (childNode.getNodeName().equals("finpoints")) {
                    fileContent = "polygon(points=[";
                    NodeList subChildNodesList = childNode.getChildNodes();
                    for (int b = 0; b < subChildNodesList.getLength(); b++) {
                        Node subChildNode = subChildNodesList.item(b);
                        if (subChildNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) subChildNode;
                            float x = 0;
                            try {
                                x = 1000 * Float.parseFloat(element.getAttribute("x"));
                            } catch (NumberFormatException e) {
                                x = 0;
                                System.out.println("x error");
                            }
                            float y = 0;
                            try {
                                y = 1000 * Float.parseFloat(element.getAttribute("y"));
                            } catch (NumberFormatException e) {
                                y = 0;
                                System.out.println("y error");
                            }
                            if (b < subChildNodesList.getLength() - 2) {
                                fileContent += "[" + x + "," + y + "],";
                            } else {
                                fileContent += "[" + x + "," + y + "]";
                            }
                            if (b == 1) {
                                rootchord = x;
                            }
                        }
                    }
                    fileContent += "]);\n";
                }
            }
        }

        if (tabposition_relativeto.equals("center")) {
            fileContent += "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
        }
        fileContent += "polygon(points=[[" + 0 + ",0],[" + 0 + ",-" + tabheight + "],[" + tablength + ",-"
                + tabheight + "],[" + tablength + ",-" + 0 + "]]);\n";

        FileUtils.writeFile(fileName, fileContent);
    }

    public static void nodeEllipticalFinset(Node node, String outPath) {
        ellipticalfinsetCount++;
        String fileName = outPath + "ellipticalfinset" + ellipticalfinsetCount + ".scad";
        String fileContent = "";
        float tabheight = 0;
        float tablength = 0;
        float tabposition = 0;
        float rootchord = 0;
        float height = 0;
        String tabposition_relativeto = "";
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("tabheight")) {
                    try {
                        tabheight = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabheight = 0;
                        System.out.println("tabheight error");
                    }
                }
                if (childNode.getNodeName().equals("tablength")) {
                    try {
                        tablength = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tablength = 0;
                        System.out.println("tablength error");
                    }
                }
                if (childNode.getNodeName().equals("tabposition")) {
                    try {
                        tabposition = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabposition = 0;
                        System.out.println("tabposition error");
                    }
                    tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                }
                if (childNode.getNodeName().equals("rootchord")) {
                    try {
                        rootchord = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        rootchord = 0;
                        System.out.println("rootchord error");
                    }
                }
                if (childNode.getNodeName().equals("height")) {
                    try {
                        height = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        height = 0;
                        System.out.println("height error");
                    }
                }
            }
        }
        fileContent = "difference() {\n";
        fileContent += "scale([1," + (2 * height / rootchord) + "])circle(d=" + rootchord + ",$fn=100);\n";
        fileContent += "translate([0," + (height / 2) + ",0])square([" + rootchord + "," + height
                + "],center =true);\n";
        fileContent += "}\n";

        if (tabposition_relativeto.equals("center")) {
            fileContent += "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
        }
        fileContent += "polygon(points=[[" + 0 + ",0],[" + 0 + "," + tabheight + "],[" + tablength + ","
                + tabheight + "],[" + tablength + "," + 0 + "]]);\n";

        FileUtils.writeFile(fileName, fileContent);
    }

    public static void nodeTrapezoidFinset(Node node, String outPath) {
        trapezoidfinsetCount++;
        String fileName = outPath + "trapezoidfinset" + trapezoidfinsetCount + ".scad";
        String fileContent = "";
        float tabheight = 0;
        float tablength = 0;
        float tabposition = 0;
        float rootchord = 0;
        float tipchord = 0;
        float sweeplength = 0;
        float height = 0;
        String tabposition_relativeto = "";
        NodeList childNodesList = node.getChildNodes();
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("tabheight")) {
                    try {
                        tabheight = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabheight = 0;
                        System.out.println("tabheight error");
                    }
                }
                if (childNode.getNodeName().equals("tablength")) {
                    try {
                        tablength = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tablength = 0;
                        System.out.println("tablength error");
                    }
                }
                if (childNode.getNodeName().equals("tabposition")) {
                    try {
                        tabposition = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tabposition = 0;
                        System.out.println("tabposition error");
                    }
                    tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                }
                if (childNode.getNodeName().equals("rootchord")) {
                    try {
                        rootchord = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        rootchord = 0;
                        System.out.println("rootchord error");
                    }
                }
                if (childNode.getNodeName().equals("tipchord")) {
                    try {
                        tipchord = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        tipchord = 0;
                        System.out.println("tipchord error");
                    }
                }
                if (childNode.getNodeName().equals("sweeplength")) {
                    try {
                        sweeplength = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        sweeplength = 0;
                        System.out.println("sweeplength error");
                    }
                }
                if (childNode.getNodeName().equals("height")) {
                    try {
                        height = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        height = 0;
                        System.out.println("height error");
                    }
                }
            }
        }
        fileContent = "polygon(points=[[0,0],[" + rootchord + "," + 0 + "],[" + (tipchord + sweeplength) + ","
                + height + "],[" + sweeplength + "," + height + "]]);\n";

        if (tabposition_relativeto.equals("center")) {
            fileContent += "translate([" + ((rootchord / 2) - (tablength / 2) - tabposition) + "," + 0 + "," + 0 + "])";
        }
        fileContent += "polygon(points=[[" + tabposition + ",0],[" + tabposition + ",-" + tabheight + "],["
                + (tablength + tabposition) + ",-" + tabheight + "],[" + (tablength + tabposition) + ",-" + 0
                + "]]);\n";

        FileUtils.writeFile(fileName, fileContent);
    }

    public static void nodeCenteringRing(Node node, String outPath) {
        centeringringCount++;
        String fileName = outPath + "centeringring-CNC" + centeringringCount + ".scad";
        String fileName3D = outPath + "centeringring-3d" + centeringringCount + ".scad";
        String fileContent = "";
        String fileContent3D = "";
        NodeList childNodesList = node.getChildNodes();
        float outerradius = 0;
        float innerradius = 0;
        float length = 0;
        for (int a = 0; a < childNodesList.getLength(); a++) {
            Node childNode = childNodesList.item(a);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                if (childNode.getNodeName().equals("outerradius")) {
                    try {
                        outerradius = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        outerradius = 0;
                        System.out.println("outerradius error");
                    }
                }
                if (childNode.getNodeName().equals("innerradius")) {
                    try {
                        innerradius = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        innerradius = 0;
                        System.out.println("innerradius error");
                    }
                }
                if (childNode.getNodeName().equals("length")) {
                    try {
                        length = 1000 * Float.parseFloat(childNode.getTextContent());
                    } catch (NumberFormatException e) {
                        length = 0;
                        System.out.println("length error");
                    }
                }
            }
        }
        fileContent = "difference() {\n";
        fileContent += "circle(r=" + df.format(outerradius) + ", $fn=200);\n";
        fileContent += "circle(r=" + df.format(innerradius) + ", $fn=200);\n";
        fileContent += "}\n";

        fileContent3D = "difference() {\n";
        fileContent3D += "cylinder(r=" + df.format(outerradius) + ",h=" + df.format(length) + ", $fn=200);\n";
        fileContent3D += "cylinder(r=" + df.format(innerradius) + ",h=" + df.format(length) + ", $fn=200);\n";
        fileContent3D += "}\n";
        FileUtils.writeFile(fileName, fileContent);
        FileUtils.writeFile(fileName3D, fileContent3D);
    }

    private static void noseConeAndShoulder(float aftradius, float length, float thickness,
            float aftshoulderradius, float aftshoulderlength, float aftshoulderthickness, float n, String type,
            String outPath) {
        Charset charset = StandardCharsets.UTF_8;
        String content;
        try {
            Path inPath = Paths.get("openscad\\noseCone-" + type + ".scad");
            content = new String(Files.readAllBytes(inPath), charset);
            content = content.replaceAll("##aftradius##", df.format(aftradius));
            content = content.replaceAll("##length##", df.format(length));
            content = content.replaceAll("##thickness##", df.format(thickness));
            content = content.replaceAll("##aftshoulderradius##", df.format(aftshoulderradius));
            content = content.replaceAll("##aftshoulderlength##", df.format(aftshoulderlength));
            content = content.replaceAll("##aftshoulderthickness##", df.format(aftshoulderthickness));
            content = content.replaceAll("##N##", df.format(n));

            Path outFilePath = Paths.get(outPath + "noseCone-" + type + "3D.scad");
            Files.write(outFilePath, content.getBytes(charset));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
