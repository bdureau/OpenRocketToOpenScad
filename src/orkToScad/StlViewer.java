package orkToScad;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Window listing STL files on the left and showing the selected one in 3D on the right. */
public final class StlViewer extends JDialog {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<Path> files = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(files);
    private final StlViewPanel view = new StlViewPanel();

    private StlViewer(Window owner, List<Path> stlFiles) {
        super(owner, "ORKtoSCAD - STL parts", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        stlFiles.forEach(files::addElement);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((lst, value, index, selected, focus) -> {
            JLabel l = new JLabel(value.getFileName().toString());
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            l.setBackground(selected ? lst.getSelectionBackground() : lst.getBackground());
            l.setForeground(selected ? lst.getSelectionForeground() : lst.getForeground());
            return l;
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                show(list.getSelectedValue());
            }
        });

        JScrollPane listPane = new JScrollPane(list);
        listPane.setPreferredSize(new Dimension(230, 600));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPane, view);
        split.setResizeWeight(0);

        JCheckBox cutaway = new JCheckBox("Cutaway view");
        cutaway.setToolTipText("Remove half of the part to see the wall thickness");
        cutaway.addActionListener(e -> view.setCutaway(cutaway.isSelected()));

        JButton reset = new JButton("Reset view");
        reset.addActionListener(e -> view.resetView());

        JButton open = new JButton("Open STL...");
        open.addActionListener(e -> openFile());

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cutaway);
        buttons.add(reset);
        buttons.add(open);
        buttons.add(close);

        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);

        if (!files.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    /** Opens the viewer (modal) on the given files; returns when the window is closed. */
    public static void showFiles(Window owner, List<Path> stlFiles) {
        new StlViewer(owner, new ArrayList<>(stlFiles)).setVisible(true);
    }

    private void show(Path file) {
        if (file == null) {
            view.setMesh(null, null, "No part selected");
            return;
        }
        try {
            view.setMesh(StlMesh.load(file), file.getFileName().toString(), null);
        } catch (IOException | RuntimeException ex) {
            view.setMesh(null, null, "Cannot read " + file.getFileName() + ": " + ex.getMessage());
        }
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        if (list.getSelectedValue() != null) {
            chooser.setCurrentDirectory(list.getSelectedValue().getParent().toFile());
        }
        chooser.setFileFilter(new FileNameExtensionFilter("STL files (*.stl)", "stl"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path p = chooser.getSelectedFile().toPath().toAbsolutePath();
            if (!files.contains(p)) {
                files.addElement(p);
            }
            list.setSelectedValue(p, true);
        }
    }
}
