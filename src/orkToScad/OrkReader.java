package orkToScad;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Reads an OpenRocket design file and returns its XML document.
 *
 * OpenRocket has used three on-disk formats over time, all with the .ork extension:
 * <ul>
 * <li>ZIP archive containing {@code rocket.ork} (current versions)</li>
 * <li>GZIP-compressed XML (older versions, e.g. most PML sample files)</li>
 * <li>Plain XML</li>
 * </ul>
 * The format is detected from the first bytes of the file, never from its name.
 *
 * Nothing is extracted to disk: the XML is parsed straight from the (decompressed)
 * stream, so no intermediate files are created next to the input and archive entry
 * names can never be used to write outside a target folder ("zip slip").
 */
public final class OrkReader {

    /** Upper bound on the decompressed XML size, to protect against zip/gzip bombs. */
    static final long MAX_XML_BYTES = 64L * 1024 * 1024;

    private OrkReader() {
    }

    public static Document read(Path orkFile) throws IOException {
        if (!Files.isRegularFile(orkFile)) {
            throw new I18n.LocalizedIOException(new I18n.Msg("err.input", orkFile));
        }
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(orkFile))) {
            raw.mark(4);
            int b0 = raw.read();
            int b1 = raw.read();
            raw.reset();

            if (b0 == 'P' && b1 == 'K') {
                return parseFromZip(raw);
            }
            if (b0 == 0x1F && b1 == 0x8B) {
                return parse(new GZIPInputStream(raw));
            }
            if (b0 == '<' || b0 == 0xEF /* UTF-8 BOM */ || Character.isWhitespace(b0)) {
                return parse(raw);
            }
            throw new I18n.LocalizedIOException(new I18n.Msg("err.format", orkFile));
        }
    }

    private static Document parseFromZip(InputStream raw) throws IOException {
        ZipInputStream zip = new ZipInputStream(raw);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            // The design lives in "rocket.ork"; other entries (decals, images...) are ignored.
            if (!entry.isDirectory() && entry.getName().equals("rocket.ork")) {
                return parse(zip);
            }
        }
        throw new I18n.LocalizedIOException(new I18n.Msg("err.noRocket"));
    }

    private static Document parse(InputStream in) throws IOException {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            builder.setErrorHandler(null); // report errors through exceptions only
            Document doc = builder.parse(new LimitedInputStream(in, MAX_XML_BYTES));
            doc.getDocumentElement().normalize();
            if (!"openrocket".equals(doc.getDocumentElement().getNodeName())) {
                throw new I18n.LocalizedIOException(
                        new I18n.Msg("err.notOpenRocket", doc.getDocumentElement().getNodeName()));
            }
            return doc;
        } catch (SAXException e) {
            throw new I18n.LocalizedIOException(new I18n.Msg("err.xml", e.getMessage()), e);
        } catch (ParserConfigurationException e) {
            throw new I18n.LocalizedIOException(new I18n.Msg("err.xmlConfig", e.getMessage()), e);
        }
    }

    /** XML parser hardened against XXE / entity expansion attacks. */
    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        return f;
    }

    /** Fails once more than {@code limit} bytes have been read. Does not close the wrapped stream twice. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                check(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                check(n);
            }
            return n;
        }

        private void check(int n) throws IOException {
            count += n;
            if (count > limit) {
                throw new I18n.LocalizedIOException(new I18n.Msg("err.tooLarge", limit / (1024 * 1024)));
            }
        }

        @Override
        public void close() {
            // The caller owns the underlying stream (try-with-resources in read()).
        }
    }
}
