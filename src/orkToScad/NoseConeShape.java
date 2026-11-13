package orkToScad;

/**
 * Nose cone profiles, using the same formulas as OpenRocket.
 *
 * {@link #radius(double)} gives the radius at distance {@code x} from the tip (0 &lt;= x &lt;= length).
 * The same formulas are written in {@code openscad/noseCone.scad}; keep both in sync.
 */
public final class NoseConeShape {

    /** Number of profile points; the OpenSCAD template uses the same value and spacing. */
    public static final int STEPS = 100;

    private final String shape;
    private final double length;
    private final double radius;
    private final double param;

    /**
     * @param shape  OpenRocket shape name (conical, ogive, ellipsoid, powerseries, parabolicseries, haackseries)
     * @param length cone length, mm
     * @param radius base radius, mm
     * @param param  OpenRocket shape parameter, or NaN to use OpenRocket's default for this shape
     */
    public NoseConeShape(String shape, double length, double radius, double param) {
        if (!isSupported(shape)) {
            throw new IllegalArgumentException("Unsupported nose cone shape: " + shape);
        }
        this.shape = shape;
        this.length = length;
        this.radius = radius;
        double p = Double.isNaN(param) ? defaultParameter(shape) : param;
        this.param = Math.max(0.0, Math.min(1.0, p));
    }

    public static boolean isSupported(String shape) {
        switch (shape) {
            case "conical":
            case "ogive":
            case "ellipsoid":
            case "powerseries":
            case "parabolicseries":
            case "haackseries":
                return true;
            default:
                return false;
        }
    }

    /** OpenRocket's default shape parameter, used when the file does not contain one. */
    public static double defaultParameter(String shape) {
        switch (shape) {
            case "ogive":
            case "parabolicseries":
                return 1.0;
            case "powerseries":
                return 0.5;
            default:
                return 0.0;
        }
    }

    public double parameter() {
        return param;
    }

    /** Distance from the tip of profile point {@code i} (0..STEPS). Points are denser near the tip. */
    public double xAt(int i) {
        double u = (double) i / STEPS;
        return length * u * u;
    }

    /** Radius (mm) at distance x (mm) from the tip. */
    public double radius(double x) {
        double R = radius;
        double L = length;
        double p = param;
        switch (shape) {
            case "conical":
                return R * x / L;
            case "ogive": {
                if (p < 0.001) {
                    return R * x / L;
                }
                double rho = Math.sqrt((L * L + R * R) * (sq((2 - p) * L) + sq(p * R)) / (4 * sq(p * R)));
                double lp = L / p;
                double y0 = Math.sqrt(Math.max(0, rho * rho - lp * lp));
                return Math.sqrt(Math.max(0, rho * rho - sq(lp - x))) - y0;
            }
            case "ellipsoid": {
                double xx = x * R / L;
                return Math.sqrt(Math.max(0, 2 * R * xx - xx * xx));
            }
            case "powerseries":
                if (p <= 0.00001) {
                    return x > 0 ? R : 0;
                }
                return R * Math.pow(x / L, p);
            case "parabolicseries":
                return R * ((2 * x / L - p * sq(x / L)) / (2 - p));
            case "haackseries": {
                double theta = Math.acos(Math.max(-1, Math.min(1, 1 - 2 * x / L)));
                return R * Math.sqrt(Math.max(0,
                        (theta - Math.sin(2 * theta) / 2 + p * Math.pow(Math.sin(theta), 3)) / Math.PI));
            }
            default:
                throw new IllegalStateException(shape);
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
