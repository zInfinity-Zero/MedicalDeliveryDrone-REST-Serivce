package ilp.coursework.ilpcoursework1.unit;

import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Service.Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IsInRegionUnitTest {

    private Services s;
    private List<Position> square;

    @BeforeEach
    void setUp() {
        s = new Services();
        // simple square region
        square = Arrays.asList(
                new Position(0.0, 0.0),
                new Position(0.0, 1.0),
                new Position(1.0, 1.0),
                new Position(1.0, 0.0)
        );
    }

    @Test
    void testPointInsideRegion() {
        Position inside = new Position(0.5, 0.5);
        assertTrue(s.isInRegion(inside, square));
    }

    @Test
    void testPointOutsideRegion() {
        Position outside = new Position(2.0, 2.0);
        assertFalse(s.isInRegion(outside, square));
    }

    @Test
    void testPointOnEdge() {
        Position edge = new Position(0.0, 0.5);
        assertTrue(s.isInRegion(edge, square)); // edge usually counts as inside
    }

    @Test
    void testInvalidRegionTooFewVertices() {
        List<Position> invalidRegion = Arrays.asList(
                new Position(0.0, 0.0),
                new Position(1.0, 1.0)
        );
        Position point = new Position(0.5, 0.5);
        assertThrows(IllegalArgumentException.class, () -> s.isInRegion(point, invalidRegion));
    }

    @Test
    void testNullInputsThrowException() {
        assertThrows(IllegalArgumentException.class, () -> s.isInRegion(null, square));
        assertThrows(IllegalArgumentException.class, () -> s.isInRegion(new Position(0.1, 0.1), null));
    }
}
