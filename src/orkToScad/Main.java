package orkToScad;

import javax.swing.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;

public class Main {
    private static String FILENAME;
    private static String outPath;
    private static boolean keepIntermediateFolder;

    public static void main(String[] args) {
        // Show input dialog to get user input
        showInputDialog();

        // Check if user input is valid
        if (FILENAME == null || outPath == null) {
            System.out.println("Error: Input file or output directory not specified.");
            return;
        }

        Path currentRelativePath = Paths.get("");
        String projectBasePath = Paths.get(outPath).toAbsolutePath().toString() + File.separator;
        System.out.println("Current absolute path is: " + projectBasePath);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            // Process the .ork file
            String intermediateFolder = processOrkFile(FILENAME, projectBasePath);

            Document doc = db.parse(new File(FILENAME));
            doc.getDocumentElement().normalize();

            System.out.println("Root Element :" + doc.getDocumentElement().getNodeName());
            System.out.println("------");

            NodeProcessor.printNode(doc.getChildNodes(), 0, projectBasePath);

            // Optionally remove intermediate folder
            if (!keepIntermediateFolder && intermediateFolder != null) {
                deleteDirectory(new File(intermediateFolder));
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void showInputDialog() {
        JTextField inputFileField = new JTextField(20);
        JTextField outputDirField = new JTextField(20);
        JCheckBox keepFolderCheckBox = new JCheckBox("Keep Intermediate Folder");

        JButton inputFileButton = new JButton("Browse...");
        inputFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = fileChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    inputFileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        JButton outputDirButton = new JButton("Browse...");
        outputDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int result = dirChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    outputDirField.setText(dirChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        JPanel panel = new JPanel(new GridLayout(3, 3));
        panel.add(new JLabel("Input File:"));
        panel.add(inputFileField);
        panel.add(inputFileButton);
        panel.add(new JLabel("Output Directory:"));
        panel.add(outputDirField);
        panel.add(outputDirButton);
        panel.add(new JLabel(""));
        panel.add(keepFolderCheckBox);

        int result = JOptionPane.showConfirmDialog(null, panel, "OpenRocket to OpenSCAD", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            FILENAME = inputFileField.getText();
            outPath = outputDirField.getText();
            keepIntermediateFolder = keepFolderCheckBox.isSelected();
        }
    }

    private static String processOrkFile(String orkFilePath, String outputDir) {
        File orkFile = new File(orkFilePath);
        if (!orkFile.exists() || !orkFilePath.endsWith(".ork")) {
            System.out.println("Error: The provided file does not have a .ork extension or does not exist.");
            return null;
        }

        // Create a copy of the .ork file with a .zip extension
        String zipFilePath = orkFilePath.replace(".ork", ".zip");
        try {
            Files.copy(Paths.get(orkFilePath), Paths.get(zipFilePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println("Error: Unable to create a zip file from the .ork file.");
            e.printStackTrace();
            return null;
        }

        // Create a directory for extraction
        String extractDir = orkFilePath.substring(0, orkFilePath.lastIndexOf('.'));
        new File(extractDir).mkdirs();

        // Unzip the file
        FileUtils.unzip(zipFilePath, extractDir);

        // Verify the presence of rocket.ork
        File rocketOrkFile = new File(extractDir, "rocket.ork");
        if (!rocketOrkFile.exists()) {
            System.out.println("Error: rocket.ork not found in the extracted contents.");
            return null;
        }

        // Clean up: remove the temporary zip file
        new File(zipFilePath).delete();

        // Set FILENAME to the extracted rocket.ork file path
        FILENAME = rocketOrkFile.getAbsolutePath();
        System.out.println("Success: Extracted rocket.ork to " + FILENAME);

        return extractDir; // Return the intermediate folder path
    }

    private static void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
}
