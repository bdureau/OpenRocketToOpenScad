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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenRocketToOpenScad {

  private static String FILENAME = "e:\\rocket5.ork";
  private static int DEPTH_XML = 0;
  private static int freeformfinsetCount = 0;
  private static int trapezoidfinsetCount = 0;
  private static int centeringringCount = 0;
  private static int bulkheadCount = 0;
  private static int ellipticalfinset =0;
  private static String outPath = "e:\\";
  

  public static void main(String[] args) {
	  
	  Path currentRelativePath = Paths.get("");
	  outPath = currentRelativePath.toAbsolutePath().toString() + "\\";
	  System.out.println("Current absolute path is: " + outPath);

	  for (String a: args) {
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

          Document doc = db.parse(new File(FILENAME));

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
  
  private static void writeFile (String fileName, String fileContent) {
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
              if (node.getNodeType() == Node.ELEMENT_NODE) {  
            	  if(node.getNodeName().equals("freeformfinset")) {
            		  
            		  freeformfinsetCount++;
            		  String fileName =outPath +"freeformfinset"+freeformfinsetCount +".scad";
            		  String fileContent ="";
            		  float tabheight=0; 
            		  float tablength=0;
            		  float tabposition=0;
            		  float rootchord =0;
            		  String tabposition_relativeto="";
            		  
            		  NodeList childNodesList = node.getChildNodes();
            		  System.out.println("//---freeformfinset---");	
            		  fileContent= "//---freeformfinset---\n";
            		  for (int a = 0; a < childNodesList.getLength(); a++) {
            	          Node childNode = childNodesList.item(a);
                          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        	  if(childNode.getNodeName().equals("tabheight")) {
                        		  
                        		  try {
                        			  tabheight = 1000 *Float.parseFloat(childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabheight = 0;
                        				System.out.println("tabheight error");
                        				System.out.println("tabheight:"+ tabheight*1000);
                        			}
                        		    //System.out.println("tabheight:"+ tabheight*1000);
                        	  }
                        	  if(childNode.getNodeName().equals("tablength")) {
                        		  
                        		  try {
                        			  tablength = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tablength = 0;
                        				System.out.println("tablength error");
                        				System.out.println("tablength:"+ tablength*1000);
                        			}
                        		  //System.out.println("tablength:"+ tablength*1000);
                       
                        	  }
                        	  if(childNode.getNodeName().equals("tabposition")) {
                        		  
                        		  try {
                        			  tabposition = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabposition = 0;
                        				System.out.println("tabposition error");
                        				System.out.println("tabposition:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("tabposition:"+ childNode.getTextContent());
                        		  
                        		  tabposition_relativeto =childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                        		  System.out.println(tabposition_relativeto);
                        		  //tabposition_relativeto
                        		  //center
                        		  //front
                        	  }
                        	  if(childNode.getNodeName().equals("finpoints")) {
                        		  System.out.print("polygon(points=[");
                        		  fileContent = fileContent +"polygon(points=[";
                        		  NodeList subChildNodesList = childNode.getChildNodes();
                        		  for (int b = 0; b < subChildNodesList.getLength(); b++) {
                        	          Node subChildNode = subChildNodesList.item(b);
                        	          if (subChildNode.getNodeType() == Node.ELEMENT_NODE) {
                        	        	  
                        	        	  Element element = (Element) subChildNode;
                        	        	  float x=0;
                                		  try {
                                			  x = 1000 * Float.parseFloat( element.getAttribute("x").toString());
                                			}
                                			catch (NumberFormatException e)
                                			{
                                				x = 0;
                                				System.out.println("x error");
                                			}
                                		  float y=0;
                                		  try {
                                			  y = 1000 * Float.parseFloat( element.getAttribute("y").toString());
                                			}
                                			catch (NumberFormatException e)
                                			{
                                				y = 0;
                                				System.out.println("y error");
                                			}
                        	        	  //System.out.println("x:"+ x*1000 + " y:" +y*1000);
                                		  if(b<subChildNodesList.getLength() -2) {
                                			  System.out.print("[" + x +","+ y +"],");
                                			  fileContent = fileContent +"[" + x +","+ y +"],";
                                		  }
                                		  else {
                                			  System.out.print("[" + x +","+ y +"]");
                                			  fileContent = fileContent +"[" + x +","+ y +"]";
                                		  }
                                		  if(b==1) {
                                			  rootchord =x;
                                		  }
                        	          }                      	          
                        		  }
                        		  System.out.println("]);");
                        		  fileContent = fileContent +"]);\n";
                        		    
                        	  }
                        	 
                        	  
                          }
            		  }
            		  
            		  
            		  if (tabposition_relativeto.equals("center")) {
            			  System.out.print("translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])");
            			  fileContent = fileContent +"translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])";
            			  //float center = 
            		  } else if (tabposition_relativeto.equals("front")) {
            			  
            		  } else if (tabposition_relativeto.equals("end")) {  
            			  
            		  }
            		  System.out.println("polygon(points=[["+ 0 +",0],["+ 0 +",-" + tabheight +
            				  "],["+ tablength + ",-" + tabheight + 
            				  "],["+ tablength + ",-" + 0 + "]]);");
            		  
            		  fileContent = fileContent +"polygon(points=[["+ 0 +",0],["+ 0 +",-"+tabheight +
            				  "],["+ tablength + ",-" + tabheight+ 
            				  "],["+ tablength + ",-" + 0 + "]]);\n";
            		  
            		  
            		  writeFile (fileName,  fileContent);
            		  //tabheight
            		  //tablength
            		  //tabposition
            		  //finpoints 
            		  //x
            		  //y
          		  
            	  }
            		  
            	  if(node.getNodeName().equals("ellipticalfinset")) {
            		  ellipticalfinset++;
            		  String fileName =outPath +"ellipticalfinset"+ellipticalfinset +".scad";
            		  String fileContent ="";
            		  float tabheight=0; 
            		  float tablength=0;
            		  float tabposition=0;
            		  float rootchord =0;
            		  float height =0;
            		  String tabposition_relativeto ="";
            		  
            		  NodeList childNodesList = node.getChildNodes();
            		  System.out.println("//---ellipticalfinset---");
            		  fileContent = "//---ellipticalfinset---\n";
            		  for (int a = 0; a < childNodesList.getLength(); a++) {
            	          Node childNode = childNodesList.item(a);
                          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        	  if(childNode.getNodeName().equals("tabheight")) {
                        		  try {
                        			  tabheight = 1000* Float.parseFloat(childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabheight = 0;
                        				System.out.println("tabheight error");
                        				System.out.println("tabheight:"+ tabheight*1000);
                        			}
                        		  //System.out.println("tabheight:"+ childNode.getTextContent());
                        	  }
                        	  if(childNode.getNodeName().equals("tablength")) {
                        		  try {
                        			  tablength = 1000* Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tablength = 0;
                        				System.out.println("tablength error");
                        				System.out.println("tablength:"+ tablength*1000);
                        			}
                        		  //System.out.println("tablength:"+ childNode.getTextContent());
                        	  }
                        	  if(childNode.getNodeName().equals("tabposition")) {
                        		  try {
                        			  tabposition = 1000* Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabposition = 0;
                        				System.out.println("tabposition error");
                        				System.out.println("tabposition:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("tabposition:"+ childNode.getTextContent());
                        		  tabposition_relativeto =childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                        		  System.out.println(tabposition_relativeto);
                        	  }
                        	  if(childNode.getNodeName().equals("rootchord")) {
                        		  try {
                        			  rootchord = 1000* Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				rootchord = 0;
                        				System.out.println("rootchord error");
                        				System.out.println("rootchord:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("rootchord:"+ rootchord*1000);
                        	  }
                        	  if(childNode.getNodeName().equals("height")) {
                        		  try {
                        			  height = 1000* Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				height = 0;
                        				System.out.println("height error");
                        				System.out.println("height:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("height:"+ height*1000);
                        	  }
                        	  
                          }
            		  }
            		  System.out.println("difference() {");
            		  fileContent = fileContent + "difference() {";
            		  System.out.println("scale([1,"+(2*height/rootchord)+"])circle(d="+rootchord+",$fn=100);");
            		  fileContent = fileContent + "scale([1,"+(2*height/rootchord)+"])circle(d="+rootchord+",$fn=100);\n";
            		  System.out.println("translate([0,"+(height/2) +",0])square(["+ rootchord+","+height+"],center =true);");
            		  fileContent = fileContent + "translate([0,"+(height/2) +",0])square(["+ rootchord+","+height+"],center =true);\n";
            		  System.out.println("}");
            		  fileContent = fileContent +"}\n";
            		  
            		  //tab
            		  if(tabposition_relativeto.equals("center")) {
            			  System.out.print("translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])");
            			  fileContent = fileContent +"translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])";
            		  }
            		  else if(tabposition_relativeto.equals("front")) {
            			  System.out.print("translate(["+ (tabposition-(rootchord/2))+",0,0])");
            			  fileContent = fileContent +"translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])"; 
            		  } 
            		  else if(tabposition_relativeto.equals("end")) {
            			  System.out.print("translate(["+ (-tabposition-(rootchord/2))+",0,0])");
            			  fileContent = fileContent +"translate(["+ ((-rootchord/2)+(tabposition)+(tablength/2))+",0,0])"; 
            		  }
            		  System.out.println(" polygon(points=[["+0 +",0],["+ 0 +","+tabheight +
            				  "],["+ tablength + "," + tabheight+ 
            				  "],["+ tablength + "," + 0+"]]);");
            		  fileContent = fileContent + " polygon(points=[["+tabposition +",0],["+ 0 +","+tabheight +
            				  "],["+ tablength + "," + tabheight+ 
            				  "],["+ tablength + "," + 0 + "]]);\n";
            		  
            		  writeFile (fileName,  fileContent);
            	  }
            	  
            	  if(node.getNodeName().equals("trapezoidfinset")) {
            		  trapezoidfinsetCount++;
            		  String fileName =outPath+"trapezoidfinset"+trapezoidfinsetCount +".scad";
            		  String fileContent ="";
            		  float tabheight=0; 
            		  float tablength=0;
            		  float tabposition=0;
            		  float rootchord =0;
            		  float tipchord =0;
            		  float sweeplength =0;
            		  float height =0;
            		  String tabposition_relativeto ="";
            		  
            		  NodeList childNodesList = node.getChildNodes();
            		  System.out.println("//---trapezoidfinset---");
            		  fileContent = "//---trapezoidfinset---\n";
            		  for (int a = 0; a < childNodesList.getLength(); a++) {
            	          Node childNode = childNodesList.item(a);
                          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        	  if(childNode.getNodeName().equals("tabheight")) {
                        		  try {
                        			  tabheight = 1000 * Float.parseFloat(childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabheight = 0;
                        				System.out.println("tabheight error");
                        				System.out.println("tabheight:"+ tabheight*1000);
                        			}
                        		  //System.out.println("tabheight:"+ childNode.getTextContent());
                        	  }
                        	  if(childNode.getNodeName().equals("tablength")) {
                        		  try {
                        			  tablength = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tablength = 0;
                        				System.out.println("tablength error");
                        				System.out.println("tablength:"+ tablength*1000);
                        			}
                        		  //System.out.println("tablength:"+ childNode.getTextContent());
                        	  }
                        	  if(childNode.getNodeName().equals("tabposition")) {
                        		  try {
                        			  tabposition = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tabposition = 0;
                        				System.out.println("tabposition error");
                        				System.out.println("tabposition:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("tabposition:"+ childNode.getTextContent());
                        		  tabposition_relativeto =childNode.getAttributes().getNamedItem("relativeto").getTextContent();
                        		  System.out.println(tabposition_relativeto);
                        	  }
                        	  if(childNode.getNodeName().equals("rootchord")) {
                        		  try {
                        			  rootchord = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				rootchord = 0;
                        				System.out.println("rootchord error");
                        				System.out.println("rootchord:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("rootchord:"+ rootchord*1000);
                        	  }
                        	  if(childNode.getNodeName().equals("tipchord")) {
                        		  try {
                        			  tipchord = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				tipchord = 0;
                        				System.out.println("tipchord error");
                        				System.out.println("tipchord:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("tipchord:"+ tipchord*1000);
                        	  }
                        	  if(childNode.getNodeName().equals("sweeplength")) {
                        		  try {
                        			  sweeplength = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				sweeplength = 0;
                        				System.out.println("sweeplength error");
                        				System.out.println("sweeplength:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("sweeplength:"+ sweeplength*1000);
                        	  }
                        	  if(childNode.getNodeName().equals("height")) {
                        		  try {
                        			  height = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				height = 0;
                        				System.out.println("height error");
                        				System.out.println("height:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("height:"+ height*1000);
                        	  }
                          }
            		  }
            		  System.out.println("polygon(points=[[0,0],["+ rootchord +","+0 +
            				  "],["+ (tipchord + sweeplength) + "," + height+ 
            				  "],["+ sweeplength + "," + height+"]]);");
            		  fileContent = fileContent + "polygon(points=[[0,0],["+ rootchord +","+0 +
            				  "],["+ (tipchord +sweeplength) + "," + height+ 
            				  "],["+ sweeplength + "," + height +"]]);\n";
            		  
            		  if( tabposition_relativeto.equals("center")) {  
            			  System.out.print("translate(["+ ((rootchord/2) -(tablength/2) - tabposition) +"," + 0 + ","+ 0 + "])");
            			  fileContent = fileContent + "translate(["+ ((rootchord/2) -(tablength/2) - tabposition) +"," + 0 + ","+ 0 + "])";
            		  } else if(tabposition_relativeto.equals("front")) {
            			  
            		  } else if(tabposition_relativeto.equals("end")) {
            			  
            		  }
            		  
            		  System.out.println("polygon(points=[["+ 0 +",0],["+ 0 +",-"+tabheight +
            				  "],["+ (tablength ) +",-" + tabheight+ 
            				  "],["+ (tablength) +",-" + 0 + "]]);");
            		  
            		  fileContent = fileContent + "polygon(points=[["+tabposition +",0],["+ tabposition +",-"+tabheight +
            				  "],["+ (tablength +tabposition) +",-" + tabheight+ 
            				  "],["+ (tablength +tabposition) +",-" + 0+"]]);\n";
            		  
            		  
            		  writeFile (fileName,  fileContent);
            		  //tabheight
            		  //tablength
            		  //tabposition
            		  //rootchord
            		  //tipchord
            		  //sweeplength
            		  //height
            		  
            	  }
            	  
            	  if(node.getNodeName().equals("centeringring")) {
            		  centeringringCount++;
            		  String fileName =outPath+"centeringring"+centeringringCount +".scad";
            		  String fileContent ="";
            		  System.out.println("//----centeringring----");
            		  NodeList childNodesList = node.getChildNodes();
            		  float outerradius=0;
            		  float innerradius=0;
            		  fileContent = fileContent + "//----centeringring----\n";
            		  
            		  for (int a = 0; a < childNodesList.getLength(); a++) {
            	          Node childNode = childNodesList.item(a);
                          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        	  if(childNode.getNodeName().equals("outerradius")) {
                        		  try {
                        			  outerradius = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				outerradius = 0;
                        				System.out.println("outerradius error");
                        				System.out.println("outerradius:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("outerradius:"+ childNode.getTextContent());
                        	  }
                        	  if(childNode.getNodeName().equals("innerradius")) {
                        		  try {
                        			  innerradius = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				outerradius = 0;
                        				System.out.println("innerradius error");
                        				System.out.println("innerradius:"+ childNode.getTextContent());
                        			}
                        		  //System.out.println("innerradius:"+ childNode.getTextContent());
                        	  }
                          }
            		  }
            		  System.out.println("difference() {");
            		  fileContent = fileContent + "difference() {\n";
            		  System.out.println("circle(r="+outerradius+", $fn=100);");
            		  fileContent = fileContent + "circle(r="+outerradius+", $fn=100);\n";
            		  System.out.println("circle(r="+innerradius+", $fn=100);");
            		  fileContent = fileContent +"circle(r="+innerradius+", $fn=100);\n";
            		  System.out.println("}");
            		  fileContent = fileContent +"}\n";
            		  
            		  writeFile (fileName,  fileContent);
            		  //outerradius
            		  //innerradius
            	  }
            	  
            	  if(node.getNodeName().equals("bulkhead")) {
            		  bulkheadCount++;
            		  String fileName =outPath+"bulkhead"+bulkheadCount +".scad";
            		  String fileContent ="";
            		  System.out.println("//-----bulkhead----");
            		  fileContent = fileContent +"//-----bulkhead----\n";
            		  NodeList childNodesList = node.getChildNodes();
            		  for (int a = 0; a < childNodesList.getLength(); a++) {
            	          Node childNode = childNodesList.item(a);
                          if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        	  if(childNode.getNodeName().equals("outerradius")) {
                        		  
                        		  float outerradius=0;
                        		  try {
                        			  outerradius = 1000 * Float.parseFloat( childNode.getTextContent().toString());
                        			}
                        			catch (NumberFormatException e)
                        			{
                        				outerradius = 0;
                        				System.out.println("outerradius error");
                        				System.out.println("outerradius:"+ outerradius*1000);
                        			}
                        		  //System.out.println("outerradius:"+ outerradius*1000);
                        		  
                        		  System.out.println("circle(r="+outerradius+", $fn=100);");
                        		  fileContent = fileContent + "circle(r="+outerradius+", $fn=100);\n";
                        	  }
                          }
            		  }
            		  writeFile (fileName,  fileContent);
            		  //outerradius
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

}