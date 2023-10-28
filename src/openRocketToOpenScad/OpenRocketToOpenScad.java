package openRocketToOpenScad;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class OpenRocketToOpenScad {

	//private static String FILENAME = "e:\\rocket.ork";
	private static String FILENAME = "e:\\PML_AMRAAM3_38mm-dual.ork";
	private static int DEPTH_XML = 0;
	private static int freeformfinsetCount = 0;
	private static int trapezoidfinsetCount = 0;
	private static int centeringringCount = 0;
	private static int bulkheadCount = 0;
	private static int ellipticalfinsetCount = 0;
	private static int tubeCouplerCount = 0;
	private static int bodyTubeCount = 0;
	private static int noiseConeCount = 0;
	private static int innerTubeCount = 0;
	private static String outPath = "e:\\";
	private static float bodyTubeIner = 0;

	public static void main(String[] args) {

		Path currentRelativePath = Paths.get("");
		outPath = currentRelativePath.toAbsolutePath().toString() + "\\";
		System.out.println("Current absolute path is: " + outPath);

		for (String a : args) {
			System.out.println(a);
		}

		if (args.length > 0) {
			FILENAME = args[0];
		}
		// Instantiate the Factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		try {

			// optional, but recommended
			// process XML securely, avoid attacks like XML External Entities (XXE)
			dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

			// parse XML file
			DocumentBuilder db = dbf.newDocumentBuilder();
			unzip(FILENAME, "e:\\"); 
			Document doc = db.parse(new File("e:\\rocket.ork"));

			// optional, but recommended
			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			System.out.println("Root Element :" + doc.getDocumentElement().getNodeName());
			System.out.println("------");
			NodeList childNodes = doc.getChildNodes();

			printNode(childNodes, 0);

		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

	}

	private static void writeFile(String fileName, String fileContent) {
		PrintWriter writer;
		try {
			writer = new PrintWriter(fileName, "UTF-8");
			writer.println(fileContent);
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void printNode(NodeList nodeList, int level) {
		level++;

		if (nodeList != null && nodeList.getLength() > 0) {
			for (int i = 0; i < nodeList.getLength(); i++) {

				Node node = nodeList.item(i);
				System.out.println("NodeName:" + node.getNodeName());

				if (node.getNodeType() == Node.ELEMENT_NODE) {
					if (node.getNodeName().equals("nosecone")) {
						nodeNoseCone(node);
					}

					if (node.getNodeName().equals("bodytube")) {
						bodyTubeIner = nodeBodyTube(node);
					}
					if (node.getNodeName().equals("tubecoupler")) {
						nodeTubeCoupler(node, bodyTubeIner);
					}
					if (node.getNodeName().equals("freeformfinset")) {
						nodeFreeFormFinset(node);
					}

					if (node.getNodeName().equals("ellipticalfinset")) {
						nodeEllipticalFinset(node);
					}

					if (node.getNodeName().equals("trapezoidfinset")) {
						nodeTrapezoidFinset(node);
					}

					if (node.getNodeName().equals("centeringring")) {
						nodeCenteringRing(node);
					}

					if (node.getNodeName().equals("bulkhead")) {
						nodeBulkhead(node);
					}
					//innertube
					if (node.getNodeName().equals("innertube")) {
						nodeInnerTube(node);
					}
					printNode(node.getChildNodes(), level);
					// how depth is it?
					if (level > DEPTH_XML) {
						DEPTH_XML = level;
					}
				}
			}
		}
	}

	private static void nodeBulkhead(Node node) {
		bulkheadCount++;
		String fileName = outPath + "bulkhead-cnc" + bulkheadCount + ".scad";
		String fileName3D = outPath + "bulkhead-3d" + bulkheadCount + ".scad";
		String fileContent = "";
		String fileContent3D = "";
		float outerradius = 0;
		float lenght = 0;
		DecimalFormat df = new DecimalFormat("0.00");
		System.out.println("//-----bulkhead----");
		fileContent = fileContent + "//-----bulkhead----\n";
		NodeList childNodesList = node.getChildNodes();
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("outerradius")) {
					try {
						outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						outerradius = 0;
						System.out.println("outerradius error");
						System.out.println("outerradius:" + outerradius * 1000);
					}
				}
				if (childNode.getNodeName().equals("length")) {
					try {
						lenght = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						lenght = 0;
						System.out.println("lenght error");
						System.out.println("lenght:" + lenght * 1000);
					}
				}
			}
		}
		System.out.println("circle(r=" + outerradius + ", $fn=200);");
		fileContent = "circle(r=" + df.format(outerradius) + ", $fn=200);\n";
		fileContent3D = "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(lenght) + ", $fn=200);\n";
		writeFile(fileName, fileContent);
		writeFile(fileName3D, fileContent3D);
	}

	private static void nodeTubeCoupler(Node node, float bodyTubeIner) {
		tubeCouplerCount++;
		String fileName3D = outPath + "tube-coupler-3d" + tubeCouplerCount + ".scad";
		String fileContent3D = "";
		float lenght = 0;
		float outerradius = 0;
		float thickness = 0;
		DecimalFormat df = new DecimalFormat("0.00");
		NodeList childNodesList = node.getChildNodes();
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("outerradius")) {
					if (childNode.getTextContent().toString().equals("auto")) {
						outerradius = bodyTubeIner;
					} else {
						try {
							outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
						} catch (NumberFormatException e) {
							outerradius = 0;
							System.out.println("outerradius error");
							System.out.println("outerradius:" + outerradius * 1000);
						}
					}
				}
				if (childNode.getNodeName().equals("length")) {
					try {
						lenght = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						lenght = 0;
						System.out.println("lenght error");
						System.out.println("lenght:" + lenght * 1000);
					}
				}
				if (childNode.getNodeName().equals("thickness")) {
					try {
						thickness = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						lenght = 0;
						System.out.println("thickness error");
						System.out.println("thickness:" + lenght * 1000);
					}
				}
			}
		}
		fileContent3D = "difference() {\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(lenght)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(lenght)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "}";
		writeFile(fileName3D, fileContent3D);
	}

	private static float nodeBodyTube(Node node) {
		bodyTubeCount++;
		String fileName3D = outPath + "body-tube-3d" + bodyTubeCount + ".scad";
		String fileContent3D = "";
		float length = 0;
		float outerradius = 0;
		float thickness = 0;
		DecimalFormat df = new DecimalFormat("0.00");
		NodeList childNodesList = node.getChildNodes();
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("radius")) {

					try {
						outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						outerradius = 0;
						System.out.println("outerradius error");
						System.out.println("outerradius:" + outerradius * 1000);
					}

				}
				if (childNode.getNodeName().equals("length")) {
					try {
						length = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						length = 0;
						System.out.println("length error");
						System.out.println("length:" + length * 1000);
					}
				}
				if (childNode.getNodeName().equals("thickness")) {
					try {
						thickness = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						thickness = 0;
						System.out.println("thickness error");
						System.out.println("thickness:" + thickness * 1000);
					}
				}
			}
		}
		fileContent3D = "difference() {\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "}";
		writeFile(fileName3D, fileContent3D);
		return outerradius - thickness;
	}

	private static void  nodeInnerTube(Node node) {
		innerTubeCount++;
		String fileName3D = outPath + "inner-tube-3d" + innerTubeCount + ".scad";
		String fileContent3D = "";
		float length = 0;
		float outerradius = 0;
		float thickness = 0;
		DecimalFormat df = new DecimalFormat("0.00");
		NodeList childNodesList = node.getChildNodes();
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("outerradius")) {

					try {
						outerradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						outerradius = 0;
						System.out.println("outerradius error");
						System.out.println("outerradius:" + outerradius * 1000);
					}

				}
				if (childNode.getNodeName().equals("length")) {
					try {
						length = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						length = 0;
						System.out.println("length error");
						System.out.println("length:" + length * 1000);
					}
				}
				if (childNode.getNodeName().equals("thickness")) {
					try {
						thickness = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						thickness = 0;
						System.out.println("thickness error");
						System.out.println("thickness:" + thickness * 1000);
					}
				}
			}
		}
		fileContent3D = "difference() {\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius) + ", h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius - thickness) + ", h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "}";
		writeFile(fileName3D, fileContent3D);
	}
	private static void nodeNoseCone(Node node) {
		noiseConeCount++;
		String fileName3D = outPath + "body-tube-3d" + bodyTubeCount + ".scad";
		String fileContent3D = "";
		float length = 0;
		float aftradius = 0;
		float thickness = 0;
		float aftshoulderradius = 0;
		float aftshoulderlength =0;
		float aftshoulderthickness = 0;
		String shape = "ogive";
		DecimalFormat df = new DecimalFormat("0.00");
		NodeList childNodesList = node.getChildNodes();
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("aftradius")) {
					try {
						aftradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						aftradius = 0;
						System.out.println("aftradius error");
						System.out.println("aftradius:" + aftradius * 1000);
					}
				}
				if (childNode.getNodeName().equals("length")) {
					try {
						length = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						length = 0;
						System.out.println("length error");
						System.out.println("length:" + length * 1000);
					}
				}
				if (childNode.getNodeName().equals("thickness")) {
					try {
						thickness = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						thickness = 0;
						System.out.println("thickness error");
						System.out.println("thickness:" + thickness * 1000);
					}
				}
				if (childNode.getNodeName().equals("aftradius")) {
					try {
						aftshoulderradius = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						aftshoulderradius = 0;
						System.out.println("aftshoulderradius error");
						System.out.println("aftshoulderradius:" + aftshoulderradius * 1000);
					}
				}
				if (childNode.getNodeName().equals("aftshoulderlength")) {
					try {
						aftshoulderlength = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						aftshoulderlength = 0;
						System.out.println("aftshoulderlength error");
						System.out.println("aftshoulderlength:" + aftshoulderlength * 1000);
					}
				}
				if (childNode.getNodeName().equals("aftshoulderthickness")) {
					try {
						aftshoulderthickness = 1000.0f * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						aftshoulderthickness = 0;
						System.out.println("aftshoulderthickness error");
						System.out.println("aftshoulderthickness:" + aftshoulderthickness * 1000);
					}
				}
				if (childNode.getNodeName().equals("shape")) {
					shape = childNode.getTextContent().toString();	
				}
			}
		}
		if(shape.equals("conical")) {
			noseConeConical(aftradius, length, thickness);
		}
		if(shape.equals("ogive")) {
			noseConeOgive(aftradius, length, thickness);
		}
		// ellipsoid
		if(shape.equals("ellipsoid")) {
			noseConeEllipsoid(aftradius, length, thickness);
		}
		
	}

	private static void nodeFreeFormFinset(Node node) {
		freeformfinsetCount++;
		String fileName = outPath + "freeformfinset" + freeformfinsetCount + ".scad";
		String fileContent = "";
		float tabheight = 0;
		float tablength = 0;
		float tabposition = 0;
		float rootchord = 0;
		String tabposition_relativeto = "";

		NodeList childNodesList = node.getChildNodes();
		System.out.println("//---freeformfinset---");
		fileContent = "//---freeformfinset---\n";
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("tabheight")) {

					try {
						tabheight = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabheight = 0;
						System.out.println("tabheight error");
						System.out.println("tabheight:" + tabheight * 1000);
					}
					// System.out.println("tabheight:"+ tabheight*1000);
				}
				if (childNode.getNodeName().equals("tablength")) {

					try {
						tablength = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tablength = 0;
						System.out.println("tablength error");
						System.out.println("tablength:" + tablength * 1000);
					}
					// System.out.println("tablength:"+ tablength*1000);

				}
				if (childNode.getNodeName().equals("tabposition")) {

					try {
						tabposition = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabposition = 0;
						System.out.println("tabposition error");
						System.out.println("tabposition:" + childNode.getTextContent());
					}
					// System.out.println("tabposition:"+ childNode.getTextContent());

					tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
					System.out.println(tabposition_relativeto);
					// tabposition_relativeto
					// center
					// front
				}
				if (childNode.getNodeName().equals("finpoints")) {
					System.out.print("polygon(points=[");
					fileContent = fileContent + "polygon(points=[";
					NodeList subChildNodesList = childNode.getChildNodes();
					for (int b = 0; b < subChildNodesList.getLength(); b++) {
						Node subChildNode = subChildNodesList.item(b);
						if (subChildNode.getNodeType() == Node.ELEMENT_NODE) {

							Element element = (Element) subChildNode;
							float x = 0;
							try {
								x = 1000 * Float.parseFloat(element.getAttribute("x").toString());
							} catch (NumberFormatException e) {
								x = 0;
								System.out.println("x error");
							}
							float y = 0;
							try {
								y = 1000 * Float.parseFloat(element.getAttribute("y").toString());
							} catch (NumberFormatException e) {
								y = 0;
								System.out.println("y error");
							}
							// System.out.println("x:"+ x*1000 + " y:" +y*1000);
							if (b < subChildNodesList.getLength() - 2) {
								System.out.print("[" + x + "," + y + "],");
								fileContent = fileContent + "[" + x + "," + y + "],";
							} else {
								System.out.print("[" + x + "," + y + "]");
								fileContent = fileContent + "[" + x + "," + y + "]";
							}
							if (b == 1) {
								rootchord = x;
							}
						}
					}
					System.out.println("]);");
					fileContent = fileContent + "]);\n";

				}

			}
		}

		if (tabposition_relativeto.equals("center")) {
			System.out.print("translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])");
			fileContent = fileContent + "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
			// float center =
		} else if (tabposition_relativeto.equals("front")) {

		} else if (tabposition_relativeto.equals("end")) {

		}
		System.out.println("polygon(points=[[" + 0 + ",0],[" + 0 + ",-" + tabheight + "],[" + tablength + ",-"
				+ tabheight + "],[" + tablength + ",-" + 0 + "]]);");

		fileContent = fileContent + "polygon(points=[[" + 0 + ",0],[" + 0 + ",-" + tabheight + "],[" + tablength + ",-"
				+ tabheight + "],[" + tablength + ",-" + 0 + "]]);\n";

		writeFile(fileName, fileContent);
		// tabheight
		// tablength
		// tabposition
		// finpoints
		// x
		// y
	}

	private static void nodeEllipticalFinset(Node node) {
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
		System.out.println("//---ellipticalfinset---");
		fileContent = "//---ellipticalfinset---\n";
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("tabheight")) {
					try {
						tabheight = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabheight = 0;
						System.out.println("tabheight error");
						System.out.println("tabheight:" + tabheight * 1000);
					}
					// System.out.println("tabheight:"+ childNode.getTextContent());
				}
				if (childNode.getNodeName().equals("tablength")) {
					try {
						tablength = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tablength = 0;
						System.out.println("tablength error");
						System.out.println("tablength:" + tablength * 1000);
					}
					// System.out.println("tablength:"+ childNode.getTextContent());
				}
				if (childNode.getNodeName().equals("tabposition")) {
					try {
						tabposition = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabposition = 0;
						System.out.println("tabposition error");
						System.out.println("tabposition:" + childNode.getTextContent());
					}
					// System.out.println("tabposition:"+ childNode.getTextContent());
					tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
					System.out.println(tabposition_relativeto);
				}
				if (childNode.getNodeName().equals("rootchord")) {
					try {
						rootchord = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						rootchord = 0;
						System.out.println("rootchord error");
						System.out.println("rootchord:" + childNode.getTextContent());
					}
					// System.out.println("rootchord:"+ rootchord*1000);
				}
				if (childNode.getNodeName().equals("height")) {
					try {
						height = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						height = 0;
						System.out.println("height error");
						System.out.println("height:" + childNode.getTextContent());
					}
					// System.out.println("height:"+ height*1000);
				}

			}
		}
		System.out.println("difference() {");
		fileContent = fileContent + "difference() {";
		System.out.println("scale([1," + (2 * height / rootchord) + "])circle(d=" + rootchord + ",$fn=100);");
		fileContent = fileContent + "scale([1," + (2 * height / rootchord) + "])circle(d=" + rootchord + ",$fn=100);\n";
		System.out.println(
				"translate([0," + (height / 2) + ",0])square([" + rootchord + "," + height + "],center =true);");
		fileContent = fileContent + "translate([0," + (height / 2) + ",0])square([" + rootchord + "," + height
				+ "],center =true);\n";
		System.out.println("}");
		fileContent = fileContent + "}\n";

		// tab
		if (tabposition_relativeto.equals("center")) {
			System.out.print("translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])");
			fileContent = fileContent + "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
		} else if (tabposition_relativeto.equals("front")) {
			System.out.print("translate([" + (tabposition - (rootchord / 2)) + ",0,0])");
			fileContent = fileContent + "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
		} else if (tabposition_relativeto.equals("end")) {
			System.out.print("translate([" + (-tabposition - (rootchord / 2)) + ",0,0])");
			fileContent = fileContent + "translate([" + ((-rootchord / 2) + (tabposition) + (tablength / 2)) + ",0,0])";
		}
		System.out.println(" polygon(points=[[" + 0 + ",0],[" + 0 + "," + tabheight + "],[" + tablength + ","
				+ tabheight + "],[" + tablength + "," + 0 + "]]);");
		fileContent = fileContent + " polygon(points=[[" + tabposition + ",0],[" + 0 + "," + tabheight + "],["
				+ tablength + "," + tabheight + "],[" + tablength + "," + 0 + "]]);\n";

		writeFile(fileName, fileContent);
	}

	private static void nodeTrapezoidFinset(Node node) {
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
		System.out.println("//---trapezoidfinset---");
		fileContent = "//---trapezoidfinset---\n";
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("tabheight")) {
					try {
						tabheight = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabheight = 0;
						System.out.println("tabheight error");
						System.out.println("tabheight:" + tabheight * 1000);
					}
					// System.out.println("tabheight:"+ childNode.getTextContent());
				}
				if (childNode.getNodeName().equals("tablength")) {
					try {
						tablength = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tablength = 0;
						System.out.println("tablength error");
						System.out.println("tablength:" + tablength * 1000);
					}
					// System.out.println("tablength:"+ childNode.getTextContent());
				}
				if (childNode.getNodeName().equals("tabposition")) {
					try {
						tabposition = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tabposition = 0;
						System.out.println("tabposition error");
						System.out.println("tabposition:" + childNode.getTextContent());
					}
					// System.out.println("tabposition:"+ childNode.getTextContent());
					tabposition_relativeto = childNode.getAttributes().getNamedItem("relativeto").getTextContent();
					System.out.println(tabposition_relativeto);
				}
				if (childNode.getNodeName().equals("rootchord")) {
					try {
						rootchord = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						rootchord = 0;
						System.out.println("rootchord error");
						System.out.println("rootchord:" + childNode.getTextContent());
					}
					// System.out.println("rootchord:"+ rootchord*1000);
				}
				if (childNode.getNodeName().equals("tipchord")) {
					try {
						tipchord = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						tipchord = 0;
						System.out.println("tipchord error");
						System.out.println("tipchord:" + childNode.getTextContent());
					}
					// System.out.println("tipchord:"+ tipchord*1000);
				}
				if (childNode.getNodeName().equals("sweeplength")) {
					try {
						sweeplength = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						sweeplength = 0;
						System.out.println("sweeplength error");
						System.out.println("sweeplength:" + childNode.getTextContent());
					}
					// System.out.println("sweeplength:"+ sweeplength*1000);
				}
				if (childNode.getNodeName().equals("height")) {
					try {
						height = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						height = 0;
						System.out.println("height error");
						System.out.println("height:" + childNode.getTextContent());
					}
					// System.out.println("height:"+ height*1000);
				}
			}
		}
		System.out.println("polygon(points=[[0,0],[" + rootchord + "," + 0 + "],[" + (tipchord + sweeplength) + ","
				+ height + "],[" + sweeplength + "," + height + "]]);");
		fileContent = fileContent + "polygon(points=[[0,0],[" + rootchord + "," + 0 + "],[" + (tipchord + sweeplength)
				+ "," + height + "],[" + sweeplength + "," + height + "]]);\n";

		if (tabposition_relativeto.equals("center")) {
			System.out.print(
					"translate([" + ((rootchord / 2) - (tablength / 2) - tabposition) + "," + 0 + "," + 0 + "])");
			fileContent = fileContent + "translate([" + ((rootchord / 2) - (tablength / 2) - tabposition) + "," + 0
					+ "," + 0 + "])";
		} else if (tabposition_relativeto.equals("front")) {

		} else if (tabposition_relativeto.equals("end")) {

		}

		System.out.println("polygon(points=[[" + 0 + ",0],[" + 0 + ",-" + tabheight + "],[" + (tablength) + ",-"
				+ tabheight + "],[" + (tablength) + ",-" + 0 + "]]);");

		fileContent = fileContent + "polygon(points=[[" + tabposition + ",0],[" + tabposition + ",-" + tabheight + "],["
				+ (tablength + tabposition) + ",-" + tabheight + "],[" + (tablength + tabposition) + ",-" + 0
				+ "]]);\n";

		writeFile(fileName, fileContent);
		// tabheight
		// tablength
		// tabposition
		// rootchord
		// tipchord
		// sweeplength
		// height

	}

	private static void nodeCenteringRing(Node node) {
		centeringringCount++;
		String fileName = outPath + "centeringring-CNC" + centeringringCount + ".scad";
		String fileName3D = outPath + "centeringring-3d" + centeringringCount + ".scad";
		String fileContent = "";
		String fileContent3D = "";
		System.out.println("//----centeringring----");
		NodeList childNodesList = node.getChildNodes();
		float outerradius = 0;
		float innerradius = 0;
		float length = 0;
		DecimalFormat df = new DecimalFormat("0.00");
		fileContent = fileContent + "//----centeringring for CNC----\n";
		fileContent3D = fileContent3D + "//----centeringring for 3D printer----\n";
		for (int a = 0; a < childNodesList.getLength(); a++) {
			Node childNode = childNodesList.item(a);
			if (childNode.getNodeType() == Node.ELEMENT_NODE) {
				if (childNode.getNodeName().equals("outerradius")) {
					try {
						outerradius = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						outerradius = 0;
						System.out.println("outerradius error");
						System.out.println("outerradius:" + childNode.getTextContent());
					}
				}
				if (childNode.getNodeName().equals("innerradius")) {
					try {
						innerradius = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						innerradius = 0;
						System.out.println("innerradius error");
						System.out.println("innerradius:" + childNode.getTextContent());
					}
				}
				if (childNode.getNodeName().equals("length")) {
					try {
						length = 1000 * Float.parseFloat(childNode.getTextContent().toString());
					} catch (NumberFormatException e) {
						length = 0;
						System.out.println("length error");
						System.out.println("length:" + childNode.getTextContent());
					}
				}
			}
		}
		// CNC file
		System.out.println("difference() {");
		fileContent = fileContent + "difference() {\n";
		System.out.println("circle(r=" + df.format(outerradius) + ", $fn=200);");
		fileContent = fileContent + "circle(r=" + df.format(outerradius) + ", $fn=200);\n";
		System.out.println("circle(r=" + df.format(innerradius) + ", $fn=200);");
		fileContent = fileContent + "circle(r=" + df.format(innerradius) + ", $fn=200);\n";
		System.out.println("}");
		fileContent = fileContent + "}\n";

		// 3D printer file
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(outerradius) + ",h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "cylinder(r=" + df.format(innerradius) + ",h=" + df.format(length)
				+ ", $fn=200);\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName, fileContent);
		writeFile(fileName3D, fileContent3D);
		// outerradius
		// innerradius
	}
	
	private static void noseConeConical(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "cylinder(r1 = " + df.format(aftradius) + ", r2 =0, h =" +df.format(length)+ ");";
		fileContent3D = fileContent3D + "translate([0,0, - thickness]) cylinder(r1 = " + df.format(aftradius- thickness) + ", r2 =0, h =" +df.format(length)+ ", $fn=200);";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}
	
	private static void noseConeOgive(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		//fileContent3D = "L= "+ df.format(length) + ";\nR=" + df.format(aftradius) + ";\nnof=30;" ;
		
		fileContent3D = fileContent3D + "module ogive(L,R) { \n ";
		fileContent3D = fileContent3D  + "nof=L/2;\n" ;
		fileContent3D = fileContent3D + "	phi = (pow(R,2) + pow(L,2))/ (2*R);\n ";
		fileContent3D = fileContent3D + "	$fn=100;\nH2=L/nof;\nx1= 0;\nx2=0;\nA1=0;\nA2= 0;\n";
		fileContent3D = fileContent3D + "	for (i=[1:nof]) {\n";
		fileContent3D = fileContent3D + "		assign(x1 = ((L/nof)*(i-1)),\n";
		fileContent3D = fileContent3D + "		x2 = ((L/nof)*i),\n";
		fileContent3D = fileContent3D + "		A1= sqrt(pow(phi,2) - pow((L - ((L/nof)*(i-1))), 2) ) + (R - phi),\n";
		fileContent3D = fileContent3D + "		A2= sqrt(pow(phi,2) - pow((L - ((L/nof)*i)), 2) ) + (R - phi),\n";
		fileContent3D = fileContent3D + "		H1= (L/nof)*i)\n";
		fileContent3D = fileContent3D + "		{\n";
		fileContent3D = fileContent3D + "			translate ([0,0, L- H1]) cylinder (r1 = A2, r2 = A1 , h= H2);\n";
		fileContent3D = fileContent3D + "		}\n";
		fileContent3D = fileContent3D + "	}\n";
		fileContent3D = fileContent3D + "}\n";
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "ogive("+df.format(length)+","+df.format(aftradius) +");\n";
		fileContent3D = fileContent3D + "translate([0,0,-"+thickness +"])ogive(" +df.format(length)+","+df.format(aftradius-thickness) +");\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}
	
	private static void noseConeEllipsoid(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}
	
	private static void noseConePowerSeries(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}

	private static void noseConeParabolicSeries(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}
	
	private static void noseConeHaackSeries(float aftradius, float length, float thickness) {
		String fileContent3D = "";
		String fileName3D = outPath + "noiseCone-3d" + noiseConeCount + ".scad";
		DecimalFormat df = new DecimalFormat("0.00");
		
		fileContent3D = fileContent3D + "difference() {\n";
		fileContent3D = fileContent3D + "}\n";
		writeFile(fileName3D, fileContent3D);
	}
	private static void unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        // create output directory if it doesn't exist
        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;
        //buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}