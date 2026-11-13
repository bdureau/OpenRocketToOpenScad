package orkToScad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.w3c.dom.Document;

/** Converts one .ork file into .scad files. Independent from the user interface, so it can be tested. */
public final class Converter {

    private Converter() {
    }

    /** Converts with STL generation enabled. */
    public static ConversionReport convert(Path orkFile, Path outDir) {
        return convert(orkFile, outDir, true);
    }

    /**
     * @param stl also write STL files for nose cones, body tubes, inner tubes, tube couplers and centering rings
     */
    public static ConversionReport convert(Path orkFile, Path outDir, boolean stl) {
        ConversionReport report = new ConversionReport();
        Document doc;
        try {
            doc = OrkReader.read(orkFile);
        } catch (IOException e) {
            report.error(e.getMessage());
            return report;
        }
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            report.error("Cannot create output directory " + outDir + ": " + e.getMessage());
            return report;
        }
        if (!Files.isWritable(outDir)) {
            report.error("Output directory is not writable: " + outDir);
            return report;
        }
        new NodeProcessor(new ScadGenerator(outDir, report, stl)).process(doc.getDocumentElement());
        if (report.getGenerated().isEmpty() && !report.hasErrors()) {
            report.warn("No supported component found in " + orkFile.getFileName());
        }
        return report;
    }
}
