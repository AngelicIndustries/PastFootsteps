package industries.angelic.pastfootsteps.footprint;

import java.util.ArrayList;
import java.util.List;

/** Rotated footprint clipped to its collision support, retaining the original texture coordinates. */
public final class DecalGeometry {

    public static final class Vertex {

        public final double x, z, u, v;

        Vertex(double x, double z, double u, double v) {
            this.x = x;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    private DecalGeometry() {}

    public static Vertex[] clipped(double x, double z, double yaw, double width, double length, double minX,
        double maxX, double minZ, double maxZ) {
        if (!Double.isFinite(x + z + yaw + width + length + minX + maxX + minZ + maxZ) || width <= 0
            || length <= 0
            || maxX <= minX
            || maxZ <= minZ) return new Vertex[0];
        double c = Math.cos(yaw), s = Math.sin(yaw);
        List<Vertex> polygon = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double u = i == 1 || i == 2 ? 1 : 0, v = i >= 2 ? 1 : 0;
            double lateral = (u - 0.5) * width, forward = (0.5 - v) * length;
            polygon.add(new Vertex(x + c * lateral - s * forward, z + s * lateral + c * forward, u, v));
        }
        polygon = clip(polygon, true, minX, true);
        polygon = clip(polygon, true, maxX, false);
        polygon = clip(polygon, false, minZ, true);
        polygon = clip(polygon, false, maxZ, false);
        return polygon.toArray(new Vertex[0]);
    }

    private static List<Vertex> clip(List<Vertex> input, boolean xAxis, double edge, boolean minimum) {
        List<Vertex> result = new ArrayList<>();
        if (input.isEmpty()) return result;
        Vertex previous = input.get(input.size() - 1);
        for (Vertex current : input) {
            double p = xAxis ? previous.x : previous.z, q = xAxis ? current.x : current.z;
            boolean pIn = minimum ? p >= edge : p <= edge, qIn = minimum ? q >= edge : q <= edge;
            if (pIn != qIn) {
                double t = (edge - p) / (q - p);
                result.add(
                    new Vertex(
                        previous.x + (current.x - previous.x) * t,
                        previous.z + (current.z - previous.z) * t,
                        previous.u + (current.u - previous.u) * t,
                        previous.v + (current.v - previous.v) * t));
            }
            if (qIn) result.add(current);
            previous = current;
        }
        return result;
    }

    /** Stable selection independent of frame rate, actor iteration order and other consumers. */
    public static boolean select(long actorSeed, long sequence, double density) {
        long bits = actorSeed * 0x9E3779B97F4A7C15L + sequence;
        bits = (bits ^ (bits >>> 30)) * 0xBF58476D1CE4E5B9L;
        bits = (bits ^ (bits >>> 27)) * 0x94D049BB133111EBL;
        bits ^= bits >>> 31;
        return (bits >>> 11) * 0x1.0p-53 < density;
    }
}
