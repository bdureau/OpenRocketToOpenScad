package orkToScad;

/**
 * Outer outline of a nose cone or transition: radius along the axis, from the fore end (x = 0)
 * to the aft end (x = length). All values in mm.
 *
 * The outline is sampled at {@link #STEPS} + 1 points ({@link #xAt(int)}); the OpenSCAD templates
 * use the same number of points and the same spacing, so .scad and .stl describe the same part.
 */
public abstract class BodyShape {

    /** Number of profile segments. */
    public static final int STEPS = 100;

    public abstract double length();

    public abstract double foreRadius();

    public abstract double aftRadius();

    /** Distance from the fore end of sample point i (0..STEPS). */
    public abstract double xAt(int i);

    /** Radius at distance x from the fore end. */
    public abstract double radius(double x);

    /** Largest radius of the outline. */
    public double maxRadius() {
        double m = 0;
        for (int i = 0; i <= STEPS; i++) {
            m = Math.max(m, radius(xAt(i)));
        }
        return m;
    }
}
