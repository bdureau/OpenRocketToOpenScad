package orkToScad;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Triangles of an STL file (binary or ASCII), with bounding box. Used by the viewer. */
public final class StlMesh {

    /** 9 floats per triangle: x0 y0 z0 x1 y1 z1 x2 y2 z2. */
    final float[] vertices;
    final float[] min = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE };
    final float[] max = { -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };

    private StlMesh(float[] vertices) {
        this.vertices = vertices;
        for (int i = 0; i < vertices.length; i += 3) {
            for (int k = 0; k < 3; k++) {
                min[k] = Math.min(min[k], vertices[i + k]);
                max[k] = Math.max(max[k], vertices[i + k]);
            }
        }
    }

    public int triangleCount() {
        return vertices.length / 9;
    }

    public float size(int axis) {
        return max[axis] - min[axis];
    }

    public static StlMesh load(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (data.length >= 84) {
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            long n = b.getInt(80) & 0xFFFFFFFFL;
            if (84 + 50 * n == data.length) {
                return binary(b, (int) n);
            }
        }
        String text = new String(data, StandardCharsets.US_ASCII);
        if (text.stripLeading().startsWith("solid") && text.contains("vertex")) {
            return ascii(text);
        }
        throw new IOException("Not a valid STL file: " + file.getFileName());
    }

    private static StlMesh binary(ByteBuffer b, int n) {
        float[] v = new float[n * 9];
        for (int i = 0; i < n; i++) {
            int o = 84 + i * 50 + 12; // skip the stored normal
            for (int k = 0; k < 9; k++) {
                v[i * 9 + k] = b.getFloat(o + 4 * k);
            }
        }
        return new StlMesh(v);
    }

    private static StlMesh ascii(String text) throws IOException {
        float[] v = new float[1024];
        int count = 0;
        for (String line : text.split("\\R")) {
            String s = line.trim();
            if (!s.startsWith("vertex")) {
                continue;
            }
            String[] parts = s.split("\\s+");
            if (parts.length < 4) {
                throw new IOException("Malformed STL vertex line: " + s);
            }
            if (count + 3 > v.length) {
                v = java.util.Arrays.copyOf(v, v.length * 2);
            }
            try {
                for (int k = 0; k < 3; k++) {
                    v[count++] = Float.parseFloat(parts[k + 1]);
                }
            } catch (NumberFormatException e) {
                throw new IOException("Malformed STL vertex line: " + s, e);
            }
        }
        if (count == 0 || count % 9 != 0) {
            throw new IOException("Malformed ASCII STL file");
        }
        return new StlMesh(java.util.Arrays.copyOf(v, count));
    }
}
