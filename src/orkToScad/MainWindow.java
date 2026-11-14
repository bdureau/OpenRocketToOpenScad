package orkToScad;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * The application window: conversion settings at the top, the parts on the left, and three previews
 * of the selected part (3D / STL, 2D / SVG, OpenSCAD code or render), with the result at the bottom.
 */
final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient Settings settings = new Settings();

    private final JTextField inputField = new UiKit.HintField(I18n.tr("toolbar.input.hint"));
    private final JTextField outputField = new UiKit.HintField(I18n.tr("toolbar.output.hint"));
    private final JCheckBox stlBox = new JCheckBox("STL", true);
    private final JCheckBox svgBox = new JCheckBox("SVG", true);
    private final UiKit.PrimaryButton convertButton = new UiKit.PrimaryButton(I18n.tr("toolbar.convert"));

    private final PartsPanel partsPanel = new PartsPanel();
    private final StlViewPanel stlView = new StlViewPanel();
    private final SvgViewPanel svgView = new SvgViewPanel();
    private final ScadPanel scadPanel = new ScadPanel();
    private final PreviewBox stlPreview = new PreviewBox(UiKit.icon(UiKit.Glyph.CUBE, UiKit.STL_ICON, 16), I18n.tr("box.stl"),
            stlView);
    private final PreviewBox svgPreview = new PreviewBox(UiKit.icon(UiKit.Glyph.SHEET, UiKit.SVG_ICON, 16), I18n.tr("box.svg"),
            svgView);
    private final PreviewBox scadPreview = new PreviewBox(UiKit.icon(UiKit.Glyph.CODE, UiKit.SCAD_ICON, 16),
            I18n.tr("box.scad"), scadPanel);

    private final JLabel status = UiKit.label(I18n.tr("status.ready"), UiKit.BASE.deriveFont(12f), new Color(0x3F4651));
    private final JButton reportLink = UiKit.link(I18n.tr("status.showReport"));

    private final JComboBox<String> languageCombo = new JComboBox<>();
    /** Set once the window is being replaced by one in another language. */
    private boolean switching;

    private transient ConversionReport lastReport;
    /** Folder shown with "Open a folder..." (when the parts do not come from a conversion). */
    private transient Path lastFolder;
    /** Output folder proposed automatically for the current input (replaced when the input changes). */
    private String autoOutput;

    private MainWindow() {
        super("ORKtoSCAD");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(UiKit.WINDOW);
        setIconImages(HelpMenu.windowIcons());
        setJMenuBar(HelpMenu.menuBar(this));
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolbar(), BorderLayout.NORTH);
        getContentPane().add(body(), BorderLayout.CENTER);
        getContentPane().add(statusBar(), BorderLayout.SOUTH);

        partsPanel.setOnSelect(this::show);
        scadPanel.setErrorSink(this::showError);
        scadPanel.locateLink().addActionListener(e -> locateOpenScad());
        scadPanel.setOpenScad(OpenScad.find(settings.get("openscad")));
        installDrop(getRootPane());
        installDrop(partsPanel.dropZone());

        inputField.setText(settings.get("input") == null ? "" : settings.get("input"));
        outputField.setText(settings.get("output") == null ? "" : settings.get("output"));
        stlBox.setSelected(settings.getBoolean("stl", true));
        svgBox.setSelected(settings.getBoolean("svg", true));
        updateConvertButton();
        show(null);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1440, screen.width), Math.min(900, screen.height));
        setMinimumSize(new Dimension(Math.min(1000, screen.width), Math.min(640, screen.height)));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                saveSettings();
            }
        });
    }

    /** Opens the window; converts {@code ork} right away if given. */
    static void open(Path ork) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // keep the default look
        }
        applyLanguage(new Settings().get("language"));
        MainWindow w = new MainWindow();
        w.setVisible(true);
        SwingUtilities.invokeLater(w::balanceSplits);
        if (ork != null) {
            w.setInput(ork);
            w.convert();
        }
    }

    /** Sets the language of the texts, and of Swing's own dialogs (file chooser...) where Java has them. */
    private static void applyLanguage(String code) {
        I18n.setLanguage(code);
        Locale.setDefault(I18n.locale());
        JComponent.setDefaultLocale(I18n.locale());
    }

    /** Rebuilds the window in another language, keeping the files, the parts and the selection. */
    private void switchLanguage(String code) {
        settings.put("language", code == null || code.isEmpty() ? null : code);
        saveSettings();
        applyLanguage(code);
        MainWindow w = new MainWindow();
        w.inputField.setText(inputField.getText());
        w.outputField.setText(outputField.getText());
        w.autoOutput = autoOutput;
        w.stlBox.setSelected(stlBox.isSelected());
        w.svgBox.setSelected(svgBox.isSelected());
        w.setBounds(getBounds());
        w.setExtendedState(getExtendedState());
        int selected = partsPanel.selectedPartIndex();
        w.setTitle(getTitle());
        if (lastReport != null) {
            w.lastReport = lastReport;
            w.partsPanel.setParts(Part.fromReport(lastReport));
            w.showReportStatus(lastReport);
        } else if (lastFolder != null) {
            w.showFolder(lastFolder);
        }
        w.partsPanel.selectPart(selected);
        w.setVisible(true);
        SwingUtilities.invokeLater(w::balanceSplits);
        dispose();
    }

    // ------------------------------------------------------------------ layout

    private JComponent toolbar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(UiKit.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiKit.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel inLabel = UiKit.label(I18n.tr("toolbar.input"), UiKit.BASE, UiKit.MUTED);
        inLabel.setLabelFor(inputField);
        styleField(inputField, 300, I18n.tr("toolbar.input.accessible"));
        JButton browseIn = button(I18n.tr("toolbar.browse"));
        browseIn.addActionListener(e -> browseInput());

        JLabel outLabel = UiKit.label(I18n.tr("toolbar.output"), UiKit.BASE, UiKit.MUTED);
        outLabel.setLabelFor(outputField);
        styleField(outputField, 230, I18n.tr("toolbar.output.accessible"));
        JButton browseOut = button(I18n.tr("toolbar.browse"));
        browseOut.addActionListener(e -> browseOutput());

        for (JCheckBox c : new JCheckBox[] { stlBox, svgBox }) {
            c.setOpaque(false);
            c.setFont(UiKit.BASE);
        }
        stlBox.setToolTipText(I18n.tr("toolbar.stl.tip"));
        svgBox.setToolTipText(I18n.tr("toolbar.svg.tip"));

        convertButton.addActionListener(e -> convert());

        // Language: "automatic" (the system's) or one of the available languages, by their own names.
        languageCombo.addItem("");
        for (String code : I18n.LANGUAGES) {
            languageCombo.addItem(code);
        }
        languageCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean sel, boolean focus) {
                String code = (String) value;
                String text = code == null || code.isEmpty()
                        ? I18n.tr("language.auto", I18n.nativeName(I18n.systemLanguage()))
                        : I18n.nativeName(code);
                return super.getListCellRendererComponent(list, text, index, sel, focus);
            }
        });
        String saved = settings.get("language");
        languageCombo.setSelectedItem(saved != null && I18n.LANGUAGES.contains(saved) ? saved : "");
        languageCombo.setFont(UiKit.BASE);
        languageCombo.setToolTipText(I18n.tr("toolbar.language.tip"));
        languageCombo.getAccessibleContext().setAccessibleName(I18n.tr("toolbar.language.tip"));
        languageCombo.setMaximumSize(new Dimension(190, 32));
        // Arrow keys only move in the list; the choice applies on Enter or click (not at every step).
        languageCombo.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
        languageCombo.addActionListener(e -> {
            String code = (String) languageCombo.getSelectedItem();
            String current = settings.get("language");
            if (!switching && !java.util.Objects.equals(code == null || code.isEmpty() ? null : code, current)) {
                switching = true; // one switch per window
                SwingUtilities.invokeLater(() -> switchLanguage(code));
            }
        });
        JButton openFolder = button(I18n.tr("toolbar.openFolder"));
        openFolder.setToolTipText(I18n.tr("toolbar.openFolder.tip"));
        openFolder.addActionListener(e -> openFolder());

        inputField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateConvertButton();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateConvertButton();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateConvertButton();
            }
        });

        bar.add(inLabel);
        bar.add(UiKit.gap(8));
        bar.add(inputField);
        bar.add(UiKit.gap(8));
        bar.add(browseIn);
        bar.add(UiKit.gap(18));
        bar.add(outLabel);
        bar.add(UiKit.gap(8));
        bar.add(outputField);
        bar.add(UiKit.gap(8));
        bar.add(browseOut);
        bar.add(UiKit.gap(14));
        bar.add(stlBox);
        bar.add(svgBox);
        bar.add(Box.createHorizontalGlue());
        bar.add(UiKit.gap(12));
        bar.add(convertButton);
        bar.add(UiKit.gap(10));
        bar.add(openFolder);
        bar.add(UiKit.gap(14));
        JLabel globe = new JLabel(UiKit.icon(UiKit.Glyph.GLOBE, UiKit.MUTED, 16));
        globe.setLabelFor(languageCombo);
        bar.add(globe);
        bar.add(UiKit.gap(6));
        bar.add(languageCombo);
        return bar;
    }

    private JSplitPane centerSplit;
    private JSplitPane rightSplit;

    /** Gives the three boxes their initial proportions (half width for the 3D view, half height for each other box). */
    private void balanceSplits() {
        centerSplit.setDividerLocation(0.5);
        rightSplit.setDividerLocation(0.5);
    }

    private JComponent body() {
        stlPreview.addTool(checkbox(I18n.tr("stl.cutaway"), false, on -> stlView.setCutaway(on),
                I18n.tr("stl.cutaway.tip")));
        stlPreview.addTool(smallButton(I18n.tr("stl.reset"), () -> stlView.resetView()));
        svgPreview.addTool(checkbox(I18n.tr("svg.grid"), true, on -> svgView.setGrid(on), I18n.tr("svg.grid.tip")));
        svgPreview.addTool(smallButton(I18n.tr("svg.fit"), () -> svgView.fit()));
        for (JComponent c : scadPanel.tools()) {
            scadPreview.addTool(c);
        }
        scadPreview.setFooter(scadPanel.footer());

        JSplitPane right = split(JSplitPane.VERTICAL_SPLIT, svgPreview, scadPreview);
        JSplitPane center = split(JSplitPane.HORIZONTAL_SPLIT, stlPreview, right);
        centerSplit = center;
        rightSplit = right;

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBackground(UiKit.WINDOW);
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        body.add(partsPanel, BorderLayout.WEST);
        body.add(center, BorderLayout.CENTER);
        return body;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(UiKit.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiKit.BORDER),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        status.setIconTextGap(8);
        bar.add(status);
        bar.add(UiKit.gap(8));
        bar.add(reportLink);
        bar.add(Box.createHorizontalGlue());
        reportLink.setVisible(false);
        reportLink.addActionListener(e -> showReport());
        return bar;
    }

    /** Split pane with an invisible 12 px divider, so the boxes look like a grid. */
    private static JSplitPane split(int orientation, JComponent a, JComponent b) {
        JSplitPane s = new JSplitPane(orientation, a, b);
        s.setResizeWeight(0.5);
        s.setContinuousLayout(true);
        s.setBorder(null);
        s.setOpaque(false);
        s.setDividerSize(12);
        s.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(UiKit.WINDOW);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        s.setBorder(null);
        a.setMinimumSize(new Dimension(200, 150));
        b.setMinimumSize(new Dimension(200, 150));
        return s;
    }

    private static void styleField(JTextField f, int width, String accessibleName) {
        f.setFont(UiKit.BASE);
        f.setPreferredSize(new Dimension(width, 32));
        f.setMaximumSize(new Dimension(width * 2, 32));
        f.setMinimumSize(new Dimension(120, 32));
        f.getAccessibleContext().setAccessibleName(accessibleName);
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(UiKit.BASE);
        return b;
    }

    private static JButton smallButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(UiKit.BASE.deriveFont(12f));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JCheckBox checkbox(String text, boolean selected, java.util.function.Consumer<Boolean> action,
            String tip) {
        JCheckBox c = new JCheckBox(text, selected);
        c.setFont(UiKit.BASE.deriveFont(12f));
        c.setToolTipText(tip);
        c.addActionListener(e -> action.accept(c.isSelected()));
        return c;
    }

    // ------------------------------------------------------------------ actions

    private void updateConvertButton() {
        convertButton.setEnabled(!inputField.getText().isBlank());
    }

    private void setInput(Path ork) {
        Path abs = ork.toAbsolutePath().normalize();
        inputField.setText(abs.toString());
        String out = outputField.getText().trim();
        if (out.isEmpty() || out.equals(autoOutput)) {
            String name = abs.getFileName().toString();
            String stem = name.toLowerCase(Locale.ROOT).endsWith(".ork") ? name.substring(0, name.length() - 4) : name;
            autoOutput = abs.resolveSibling(stem + "_out").toString();
            outputField.setText(autoOutput);
        }
    }

    private void browseInput() {
        JFileChooser chooser = new JFileChooser(currentDir(inputField.getText()));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.tr("dialog.orkFilter"), "ork"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setInput(chooser.getSelectedFile().toPath());
        }
    }

    private void browseOutput() {
        JFileChooser chooser = new JFileChooser(currentDir(outputField.getText()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().getAbsolutePath());
            autoOutput = null;
        }
    }

    private static File currentDir(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        File f = new File(text.trim());
        return f.isDirectory() ? f : f.getParentFile();
    }

    private void convert() {
        String in = inputField.getText().trim();
        if (in.isEmpty()) {
            return;
        }
        Path input = Paths.get(in).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            showError(I18n.tr("status.fileNotFound", input));
            return;
        }
        if (outputField.getText().isBlank()) {
            setInput(input);
        }
        Path output = Paths.get(outputField.getText().trim()).toAbsolutePath().normalize();
        boolean stl = stlBox.isSelected();
        boolean svg = svgBox.isSelected();

        convertButton.setEnabled(false);
        setStatus(I18n.tr("status.converting"), null);
        reportLink.setVisible(false);
        new SwingWorker<ConversionReport, Void>() {
            @Override
            protected ConversionReport doInBackground() {
                return Converter.convert(input, output, stl, svg);
            }

            @Override
            protected void done() {
                updateConvertButton();
                try {
                    lastReport = get();
                    lastFolder = null;
                } catch (Exception e) {
                    showError(I18n.tr("status.failed", e.getMessage()));
                    return;
                }
                setTitle("ORKtoSCAD \u2014 " + input.getFileName());
                partsPanel.setParts(Part.fromReport(lastReport));
                showReportStatus(lastReport);
                saveSettings();
                if (lastReport.hasErrors()) {
                    showReport();
                }
            }
        }.execute();
    }

    private void openFolder() {
        JFileChooser chooser = new JFileChooser(currentDir(outputField.getText()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18n.tr("dialog.openFolder.title"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        showFolder(chooser.getSelectedFile().toPath());
    }

    /** Shows the parts found in an output folder. */
    private void showFolder(Path dir) {
        try {
            List<Part> parts = Part.fromFolder(dir);
            lastReport = null;
            lastFolder = dir;
            partsPanel.setParts(parts);
            setTitle("ORKtoSCAD \u2014 " + dir.getFileName());
            reportLink.setVisible(false);
            if (parts.isEmpty()) {
                setStatus(I18n.tr("status.folderEmpty", dir), UiKit.WARN);
            } else {
                setStatus(I18n.tr("status.folderOpened", I18n.plural("count.parts", parts.size())), UiKit.OK);
            }
        } catch (IOException e) {
            showError(I18n.tr("status.folderError", e.getMessage()));
        }
    }

    private void locateOpenScad() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.tr("scad.locate.title"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path exe = OpenScad.find(chooser.getSelectedFile().getAbsolutePath());
        if (exe == null || !exe.equals(chooser.getSelectedFile().toPath().toAbsolutePath())) {
            showError(I18n.tr("scad.locate.invalid", chooser.getSelectedFile()));
            return;
        }
        settings.put("openscad", exe.toString());
        settings.save();
        scadPanel.setOpenScad(exe);
    }

    // ------------------------------------------------------------------ previews

    /** Shows a part (or nothing) in the three boxes. */
    private void show(Part part) {
        if (part == null) {
            stlPreview.showEmpty(null, I18n.tr("empty.stl.none"));
            svgPreview.showEmpty(null, I18n.tr("empty.svg.none"));
            scadPreview.showEmpty(null, I18n.tr("empty.scad.none"));
            return;
        }
        boolean flat = part.kind == Part.Kind.BULKHEAD || part.kind == Part.Kind.TRAPEZOIDFINSET
                || part.kind == Part.Kind.ELLIPTICALFINSET || part.kind == Part.Kind.FREEFORMFINSET;
        boolean hasFlatPlan = flat || part.kind == Part.Kind.CENTERINGRING;

        Path stl = part.stl();
        if (stl != null) {
            try {
                stlView.setMesh(StlMesh.load(stl), stl.getFileName().toString(), null);
                stlPreview.showContent(stl.getFileName().toString());
            } catch (IOException | RuntimeException e) {
                stlPreview.showEmpty(I18n.tr("empty.stl.unreadable"), e.getMessage());
            }
        } else if (flat) {
            stlPreview.showEmpty(I18n.tr("empty.stl.title"), I18n.tr("empty.stl.flat"));
        } else {
            stlPreview.showEmpty(I18n.tr("empty.stl.title"), I18n.tr("empty.stl.option"));
        }

        Path svg = part.svg();
        if (svg != null) {
            try {
                svgView.setDrawing(SvgViewPanel.load(svg), part.copies);
                svgPreview.showContent(svg.getFileName().toString());
            } catch (IOException | RuntimeException e) {
                svgPreview.showEmpty(I18n.tr("empty.svg.unreadable"), e.getMessage());
            }
        } else if (hasFlatPlan) {
            svgPreview.showEmpty(I18n.tr("empty.svg.title"), I18n.tr("empty.svg.option"));
        } else {
            svgPreview.showEmpty(I18n.tr("empty.svg.title"), I18n.tr("empty.svg.3d"));
        }

        List<Path> scad = part.scadFiles();
        if (!scad.isEmpty()) {
            scadPanel.setFiles(scad);
            scadPreview.showContent(null); // the file name is in the selector
        } else {
            scadPreview.showEmpty(I18n.tr("empty.scad.title"), "");
        }
    }

    // ------------------------------------------------------------------ status and report

    private void setStatus(String text, Color iconColor) {
        status.setText(text);
        status.setForeground(new Color(0x3F4651));
        status.setIcon(iconColor == null ? null
                : UiKit.icon(iconColor == UiKit.OK ? UiKit.Glyph.CHECK : UiKit.Glyph.WARNING, iconColor, 14));
    }

    private void showReportStatus(ConversionReport r) {
        int files = r.getGenerated().size();
        int warnings = r.getWarnings().size();
        int notes = r.getNotes().size();
        if (r.hasErrors()) {
            setStatus(I18n.tr("status.errors", r.getErrors().get(0)), UiKit.ERROR);
        } else {
            setStatus(I18n.tr("status.done", I18n.plural("count.files", files),
                    I18n.plural("count.warnings", warnings), I18n.plural("count.notes", notes)),
                    warnings > 0 ? UiKit.WARN : UiKit.OK);
        }
        reportLink.setVisible(true);
    }

    private void showError(String message) {
        setStatus(message, UiKit.ERROR);
    }

    private void showReport() {
        if (lastReport == null) {
            return;
        }
        JTextArea text = new JTextArea(lastReport.summary());
        text.setEditable(false);
        text.setFont(UiKit.MONO_SMALL);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(760, 420));
        JDialog d = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE).createDialog(this, I18n.tr("report.title"));
        d.setResizable(true);
        d.setVisible(true);
    }

    // ------------------------------------------------------------------ drag and drop, settings

    /** Dropping a .ork file on the window fills the input and converts it. */
    private void installDrop(JComponent target) {
        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        if (f.getName().toLowerCase(Locale.ROOT).endsWith(".ork")) {
                            setInput(f.toPath());
                            SwingUtilities.invokeLater(MainWindow.this::convert);
                            e.dropComplete(true);
                            return;
                        }
                    }
                    showError(I18n.tr("status.dropOrk"));
                    e.dropComplete(false);
                } catch (Exception ex) {
                    e.dropComplete(false);
                }
            }
        }, true);
    }

    private void saveSettings() {
        settings.put("input", inputField.getText().trim());
        settings.put("output", outputField.getText().trim());
        settings.put("stl", stlBox.isSelected());
        settings.put("svg", svgBox.isSelected());
        settings.save();
    }
}
