package ilp.coursework.ilpcoursework1.unit;

import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Service.Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NextPositionUnitTest {

    private Services s;

    @BeforeEach
    void setUp() {
        s = new Services();
    }

    @Test
    void testNextPositionEast() {
        Position start = new Position(-3.192473,55.946233 );
        double angle = 0.0; // East
        Position next = s.nextPosition(start, angle);

        assertEquals(55.946233, next.getLat(), 1e-9);
        assertEquals(-3.192323, next.getLng(), 1e-6); // expected to increase in lng
    }

    @Test
    void testNextPositionNorth() {
        Position start = new Position(-3.192473,55.946233);
        double angle = 90.0; // North
        Position next = s.nextPosition(start, angle);

        assertTrue(next.getLat() > start.getLat());
        assertEquals(start.getLng(), next.getLng(), 1e-9);
    }

    @Test
    void testInvalidAngleThrowsException() {
        Position start = new Position(-3.192473,55.946233);
        assertThrows(IllegalArgumentException.class, () -> s.nextPosition(start, 10.0));
    }

    @Test
    void testNullStartThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> s.nextPosition(null, 90.0));
    }
}