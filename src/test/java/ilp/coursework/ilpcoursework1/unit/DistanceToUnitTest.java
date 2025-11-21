package ilp.coursework.ilpcoursework1.unit;

import ilp.coursework.ilpcoursework1.PosandDis.Distance;
import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Service.Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DistanceToUnitTest {

    private Services s;

    @BeforeEach
    void setUp() {
        s = new Services();
    }

    @Test
    void testValidDistance() {
        Position p1 = new Position(1.0, 1.0);
        Position p2 = new Position(1.0, 1.0001);
        Distance distance = new Distance(p1, p2);

        Double result = s.calculateDistance(distance);

        assertNotNull(result);
        assertTrue(result > 0);
    }

    @Test
    void testInvalidPositionsThrowsException() {
        Distance distance = new Distance(null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            s.calculateDistance(distance);
        });
    }
}
