package orkToScad;

import java.util.Locale;

/**
 * Nose cone and transition profile formulas, identical to OpenRocket's {@code Transition.Shape}.
 *
 * The same formulas are written in {@code openscad/noseCone.scad} and {@code openscad/transition.scad};
 * keep them in sync.
 */
public final class OrShapes {

    /** OpenRocket's threshold under which an ogive becomes a cone. */
    private static final double MINFEATURE = 0.001;

    private OrShapes() {
    }

    /**
     * Canonical shape name as written by OpenRocket ("conical", "ogive", "ellipsoid", "power",
     * "parabolic", "haack"). Also accepts the "...series" spellings. Returns null if unknown.
     */
    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "conical":
                return "conical";
            case "ogive":
                return "ogive";
            case "ellipsoid":
                return "ellipsoid";
            case "power":
            case "powerseries":
                return "power";
            case "parabolic":
            case "parabolicseries":
                return "parabolic";
            case "haack":
            case "haackseries":
                return "haack";
            default:
                return null;
        }
    }

    /** Suffix used in output file names (kept from the previous versions). */
    public static String fileSuffix(String canonical) {
        switch (canonical) {
            case "conical":
                return "Conical";
            case "ogive":
                return "Ogive";
            case "ellipsoid":
                return "Ellipsoid";
            case "power":
                return "PowerSeries";
            case "parabolic":
                return "ParabolicSeries";
            case "haack":
                return "HaackSeries";
            default:
                throw new IllegalArgumentException(canonical);
        }
    }

    /** OpenRocket's default shape parameter. */
    public static double defaultParameter(String shape) {
        switch (shape) {
            case "ogive":
            case "parabolic":
                return 1.0;
            case "power":
                return 0.5;
            default:
                return 0.0;
        }
    }

    /** Clamps the parameter to OpenRocket's range (0..1, 0..1/3 for Haack); NaN gives the default. */
    public static double parameter(String shape, double raw) {
        double p = Double.isNaN(raw) ? defaultParameter(shape) : raw;
        double max = shape.equals("haack") ? 1.0 / 3.0 : 1.0;
        return Math.max(0.0, Math.min(max, p));
    }

    /** Whether OpenRocket allows clipping this shape on transitions. */
    public static boolean clippable(String shape) {
        return shape.equals("ellipsoid") || shape.equals("power") || shape.equals("haack");
    }

    /**
     * Radius at distance x from the tip of a shape of base radius R and length L (OpenRocket's
     * {@code Shape.getRadius(x, radius, length, param)}).
     */
    public static double radius(String shape, double x, double R, double L, double p) {
        switch (shape) {
            case "conical":
                return R * x / L;
            case "ogive": {
                if (L < R) { // OpenRocket scales short ogives
                    x = x * R / L;
                    L = R;
                }
                if (p < MINFEATURE) {
                    return R * x / L;
                }
                double rho = safeSqrt((L * L + R * R) * (sq((2 - p) * L) + sq(p * R)) / (4 * sq(p * R)));
                double lp = L / p;
                double y0 = safeSqrt(rho * rho - lp * lp);
                return safeSqrt(rho * rho - sq(lp - x)) - y0;
            }
            case "ellipsoid": {
                double xx = x * R / L;
                return safeSqrt(2 * R * xx - xx * xx);
            }
            case "power":
                if (p <= 0.00001) {
                    return x <= 0.00001 ? 0 : R;
                }
                return R * Math.pow(x / L, p);
            case "parabolic":
                return R * ((2 * x / L - p * sq(x / L)) / (2 - p));
            case "haack": {
                double theta = Math.acos(Math.max(-1, Math.min(1, 1 - 2 * x / L)));
                return R * safeSqrt((theta - Math.sin(2 * theta) / 2 + p * Math.pow(Math.sin(theta), 3)) / Math.PI);
            }
            default:
                throw new IllegalArgumentException("Unknown shape: " + shape);
        }
    }

    private static double safeSqrt(double v) {
        return v <= 0 ? 0 : Math.sqrt(v);
    }

    private static double sq(double v) {
        return v * v;
    }
}
