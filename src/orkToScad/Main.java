package orkToScad;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Entry point: asks for the input .ork file and the output folder, then runs the conversion. */
public class Main {

    public static void main(String[] args) {
        Options options = showInputDialog();
        if (options == null) {
            System.out.println("Cancelled.");
            System.exit(0);
            return;
        }
        Path input = options.input;
        Path outDir = options.outDir;
        System.out.println("Input file : " + input);
        System.out.println("Output dir : " + outDir);

        ConversionReport report = Converter.convert(input, outDir, options.stl);

        for (Path p : report.getGenerated()) {
            System.out.println("Generated  : " + p.getFileName());
        }
        System.out.println(report.summary());
        showResult(report, outDir);
        System.exit(report.hasErrors() ? 1 : 0);
    }

    /** What the user chose in the dialog. */
    private static final class Options {
        Path input;
        Path outDir;
        boolean stl;
    }

    /** Returns the user's choices, or null if the user cancelled. */
    private static Options showInputDialog() {
        JTextField inputFileField = new JTextField(30);
        JTextField outputDirField = new JTextField(30);

        JButton browseInput = new JButton("Browse...");
        browseInput.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setFileFilter(new FileNameExtensionFilter("OpenRocket design (*.ork)", "ork"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                inputFileField.setText(chooser.getSelectedFile().getAbsolutePath());
                if (outputDirField.getText().isBlank()) {
                    outputDirField.setText(chooser.getSelectedFile().getParentFile().getAbsolutePath());
                }
            }
        });

        JButton browseOutput = new JButton("Browse...");
        browseOutput.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JCheckBox stlBox = new JCheckBox("Also generate STL files (nose cones, tubes, couplers, centering rings)", true);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        panel.add(new JLabel("Input File:"), c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(inputFileField, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(browseInput, c);
        c.gridy = 1;
        panel.add(new JLabel("Output Directory:"), c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(outputDirField, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(browseOutput, c);
        c.gridy = 2;
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(stlBox, c);

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, panel, "OpenRocket to OpenSCAD",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String in = inputFileField.getText().trim();
            String out = outputDirField.getText().trim();
            if (in.isEmpty() || out.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Please select an input file and an output directory.",
                        "OpenRocket to OpenSCAD", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            Options o = new Options();
            o.input = Paths.get(in).toAbsolutePath().normalize();
            o.outDir = Paths.get(out).toAbsolutePath().normalize();
            o.stl = stlBox.isSelected();
            return o;
        }
    }

    private static void showResult(ConversionReport report, Path outDir) {
        JTextArea text = new JTextArea(report.summary() + "\n\nOutput: " + outDir, 15, 60);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        int type = report.hasErrors() ? JOptionPane.ERROR_MESSAGE
                : report.getWarnings().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
        List<Path> stlFiles = report.getGenerated().stream()
                .filter(p -> p.getFileName().toString().endsWith(".stl"))
                .collect(Collectors.toList());
        if (stlFiles.isEmpty()) {
            JOptionPane.showMessageDialog(null, new JScrollPane(text), "OpenRocket to OpenSCAD", type);
            return;
        }
        String[] choices = { "View STL parts", "Close" };
        int choice = JOptionPane.showOptionDialog(null, new JScrollPane(text), "OpenRocket to OpenSCAD",
                JOptionPane.DEFAULT_OPTION, type, null, choices, choices[0]);
        if (choice == 0) {
            StlViewer.showFiles(null, stlFiles);
        }
    }
}
