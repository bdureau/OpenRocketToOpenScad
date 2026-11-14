package orkToScad;

/**
 * Transition outline between a fore radius and an aft radius, computed like OpenRocket's
 * {@code Transition.getRadius(x)}, including "clipped" shapes.
 * Must stay in sync with {@code openscad/transition.scad}.
 */
public final class TransitionShape extends BodyShape {

    /** OpenRocket's CLIP_PRECISION (0.0001 m), in mm. */
    private static final double CLIP_PRECISION = 0.1;

    private final String shape;
    private final double length;
    private final double foreRadius;
    private final double aftRadius;
    private final double param;
    private final boolean clipped;
    private final double clipLength;

    /**
     * @param shape   canonical shape name (see {@link OrShapes#canonical(String)})
     * @param param   shape parameter, or NaN for OpenRocket's default
     * @param clipped OpenRocket's "clipped" option (only used by clippable shapes)
     */
    public TransitionShape(String shape, double length, double foreRadius, double aftRadius, double param,
            boolean clipped) {
        if (OrShapes.canonical(shape) == null || !OrShapes.canonical(shape).equals(shape)) {
            throw new IllegalArgumentException("Unsupported transition shape: " + shape);
        }
        this.shape = shape;
        this.length = length;
        this.foreRadius = foreRadius;
        this.aftRadius = aftRadius;
        this.param = OrShapes.parameter(shape, param);
        this.clipped = clipped && OrShapes.clippable(shape);
        this.clipLength = this.clipped ? clipLength() : 0;
    }

    public double parameter() {
        return param;
    }

    public boolean isClipped() {
        return clipped;
    }

    @Override
    public double length() {
        return length;
    }

    @Override
    public double foreRadius() {
        return foreRadius;
    }

    @Override
    public double aftRadius() {
        return aftRadius;
    }

    /** Cosine spacing: points are denser at both ends. */
    @Override
    public double xAt(int i) {
        return length * (1 - Math.cos(Math.PI * i / STEPS)) / 2;
    }

    @Override
    public double radius(double x) {
        if (x <= 0) {
            return foreRadius;
        }
        if (x >= length) {
            return aftRadius;
        }
        double r1 = foreRadius;
        double r2 = aftRadius;
        if (r1 == r2) {
            return r1;
        }
        if (r1 > r2) {
            x = length - x;
            double tmp = r1;
            r1 = r2;
            r2 = tmp;
        }
        if (clipped) {
            return OrShapes.radius(shape, clipLength + x, r2, clipLength + length, param);
        }
        return r1 + OrShapes.radius(shape, x, r2 - r1, length, param);
    }

    /** Same binary search as OpenRocket's {@code Transition.calculateClip}. */
    private double clipLength() {
        double r1 = Math.min(foreRadius, aftRadius);
        double r2 = Math.max(foreRadius, aftRadius);
        if (r1 == 0 || length <= 0 || r1 == r2) {
            return 0;
        }
        double min = 0;
        double max = length;
        int n = 0;
        while (OrShapes.radius(shape, max, r2, max + length, param) - r1 < 0) {
            min = max;
            max *= 2;
            n++;
            if (n > 10) {
                break;
            }
        }
        while (true) {
            double c = (min + max) / 2;
            if ((max - min) < CLIP_PRECISION) {
                return c;
            }
            double val = OrShapes.radius(shape, c, r2, c + length, param);
            if (val - r1 > 0) {
                max = c;
            } else {
                min = c;
            }
        }
    }
}
