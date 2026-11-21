package industries.angelic.pastfootsteps.footprint;

import static org.junit.Assert.*;

import org.junit.Test;

public class DecalGeometryTest {

    @Test
    public void rotatedPrintIsClippedWithoutStretchingTexture() {
        DecalGeometry.Vertex[] polygon = DecalGeometry.clipped(0.95, 0.5, Math.PI / 4, 0.3, 0.6, 0, 1, 0, 1);
        assertTrue(polygon.length >= 3);
        boolean cut = false;
        for (DecalGeometry.Vertex point : polygon) {
            assertTrue(point.x >= 0 && point.x <= 1);
            assertTrue(point.z >= 0 && point.z <= 1);
            assertTrue(point.u >= 0 && point.u <= 1);
            assertTrue(point.v >= 0 && point.v <= 1);
            if (point.x == 1 && (point.u > 0 && point.u < 1 || point.v > 0 && point.v < 1)) cut = true;
        }
        assertTrue(cut);
    }

    @Test
    public void invalidOrDisjointSupportsProduceNoPrint() {
        assertEquals(0, DecalGeometry.clipped(3, 3, 0, .3, .6, 0, 1, 0, 1).length);
        assertEquals(0, DecalGeometry.clipped(0, 0, 0, .3, .6, 0, 0, 0, 1).length);
        assertEquals(0, DecalGeometry.clipped(Double.NaN, 0, 0, .3, .6, 0, 1, 0, 1).length);
    }

    @Test
    public void densityFiltersContactsDeterministically() {
        int selected = 0;
        for (int i = 0; i < 1000; i++) {
            assertFalse(DecalGeometry.select(12, i, 0));
            assertTrue(DecalGeometry.select(12, i, 1));
            boolean value = DecalGeometry.select(12, i, .25);
            assertEquals(value, DecalGeometry.select(12, i, .25));
            if (value) selected++;
        }
        assertTrue(selected > 180 && selected < 320);
    }
}
