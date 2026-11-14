package orkToScad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.w3c.dom.Document;

/** Converts one .ork file into .scad files. Independent from the user interface, so it can be tested. */
public final class Converter {

    private Converter() {
    }

    /** Converts with STL and SVG generation enabled. */
    public static ConversionReport convert(Path orkFile, Path outDir) {
        return convert(orkFile, outDir, true);
    }

    /**
     * @param stl also write STL files for the 3D parts (nose cones, transitions, tubes, couplers, rings, rail buttons...)
     */
    public static ConversionReport convert(Path orkFile, Path outDir, boolean stl) {
        return convert(orkFile, outDir, stl, true);
    }

    /**
     * @param stl also write STL files for the 3D parts
     * @param svg also write SVG cut outlines for the flat parts (fins, centering rings, bulkheads)
     */
    public static ConversionReport convert(Path orkFile, Path outDir, boolean stl, boolean svg) {
        ConversionReport report = new ConversionReport();
        Document doc;
        try {
            doc = OrkReader.read(orkFile);
        } catch (IOException e) {
            report.error(e);
            return report;
        }
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            report.error(new I18n.Msg("conv.cannotCreateOut", outDir, e.getMessage()));
            return report;
        }
        if (!Files.isWritable(outDir)) {
            report.error(new I18n.Msg("conv.outNotWritable", outDir));
            return report;
        }
        new NodeProcessor(new ScadGenerator(outDir, report, stl, svg), report).processDocument(doc.getDocumentElement());
        if (report.getGenerated().isEmpty() && !report.hasErrors()) {
            report.warn(new I18n.Msg("conv.noComponent", orkFile.getFileName()));
        }
        return report;
    }
}
