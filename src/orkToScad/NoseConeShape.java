package orkToScad;

/**
 * Nose cone outline, using OpenRocket's formulas ({@link OrShapes}).
 * Must stay in sync with {@code openscad/noseCone.scad}.
 */
public final class NoseConeShape extends BodyShape {

    private final String shape;
    private final double length;
    private final double radius;
    private final double param;

    /**
     * @param shape  canonical shape name (see {@link OrShapes#canonical(String)})
     * @param length cone length, mm
     * @param radius base radius, mm
     * @param param  OpenRocket shape parameter, or NaN to use OpenRocket's default for this shape
     */
    public NoseConeShape(String shape, double length, double radius, double param) {
        if (OrShapes.canonical(shape) == null || !OrShapes.canonical(shape).equals(shape)) {
            throw new IllegalArgumentException("Unsupported nose cone shape: " + shape);
        }
        this.shape = shape;
        this.length = length;
        this.radius = radius;
        this.param = OrShapes.parameter(shape, param);
    }

    public double parameter() {
        return param;
    }

    @Override
    public double length() {
        return length;
    }

    @Override
    public double foreRadius() {
        return 0;
    }

    @Override
    public double aftRadius() {
        return radius;
    }

    /** Points are denser near the tip, where the curvature is highest. */
    @Override
    public double xAt(int i) {
        double u = (double) i / STEPS;
        return length * u * u;
    }

    @Override
    public double radius(double x) {
        return OrShapes.radius(shape, x, radius, length, param);
    }
}
