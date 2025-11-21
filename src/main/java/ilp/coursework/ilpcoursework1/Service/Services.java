package ilp.coursework.ilpcoursework1.Service;

import ilp.coursework.ilpcoursework1.PosandDis.Distance;
import ilp.coursework.ilpcoursework1.Util.GeoValidator;
import ilp.coursework.ilpcoursework1.PosandDis.Position;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Services {

    public Double calculateDistance(Distance distance) {
        Position p1 = distance.getPosition1();
        Position p2 = distance.getPosition2();

        if (!GeoValidator.isValid(p1) || !GeoValidator.isValid(p2)) {
            throw new IllegalArgumentException("Invalid positions");
        }


        return Math.sqrt(Math.pow(p1.getLat() - p2.getLat(), 2)
                + Math.pow(p1.getLng() - p2.getLng(), 2));
    }

    public Boolean isCloseTo(Distance distance) {
        Position p1 = distance.getPosition1();
        Position p2 = distance.getPosition2();

        if (!GeoValidator.isValid(p1) || !GeoValidator.isValid(p2)) {
            throw new IllegalArgumentException("Invalid positions");
        }

        double d = calculateDistance(distance);
        return d < 0.00015;
    }

    public Position nextPosition(Position start, Double angle) {
        if (angle == null || angle < 0 || angle > 360|| angle % 22.5 != 0) {
            throw new IllegalArgumentException("Invalid angle");
        }
        if (!GeoValidator.isValid(start)) {
            throw new IllegalArgumentException("Invalid position");
        }

        double radians = Math.toRadians(angle);

        double deltaLat = Math.sin(radians) * 0.00015;
        double deltaLng = Math.cos(radians) * 0.00015;

        Position next = new Position();
        next.setLat(start.getLat() + deltaLat);
        next.setLng(start.getLng() + deltaLng);

        return next;
    }


    public Boolean isInRegion(Position point, List<Position> vertices) {
        if (vertices == null || vertices.isEmpty() || vertices.size() < 3) {
            throw new IllegalArgumentException("Invalid region");
        }
        for (Position v : vertices) {
            if (!GeoValidator.isValid(v)) {
                throw new IllegalArgumentException("Invalid region");
            }
        }
        if (!GeoValidator.isValid(point)) {
            throw new IllegalArgumentException("Invalid position");
        }


        int intersectCount = 0;
        for (int j = 0; j < vertices.size() - 1; j++) {
            Position v1 = vertices.get(j);
            Position v2 = vertices.get(j + 1);

            if (isPointOnSegment(point, v1, v2)) {
                return true; // treat edges as inside
            }
            if (rayIntersectsSegment(point, v1, v2)) {
                intersectCount++;
            }
        }

        Position last = vertices.get(vertices.size() - 1);
        Position first = vertices.get(0);

        if (rayIntersectsSegment(point, last, first)) {
            intersectCount++;
        }

        return intersectCount % 2 == 1;
    }

    private boolean rayIntersectsSegment(Position p, Position v1, Position v2) {
        double px = p.getLng();
        double py = p.getLat();
        double x1 = v1.getLng();
        double y1 = v1.getLat();
        double x2 = v2.getLng();
        double y2 = v2.getLat();

        if (y1 > y2) {
            double tmpX = x1, tmpY = y1;
            x1 = x2; y1 = y2;
            x2 = tmpX; y2 = tmpY;
        }

        if (py == y1 || py == y2) py += 1e-10;
        if (py < y1 || py > y2) return false;
        if (x1 == x2) return px <= x1;
        double xinters = (py - y1) * (x2 - x1) / (y2 - y1) + x1;
        return px <= xinters;
    }

    private boolean isPointOnSegment(Position p, Position a, Position b) {
        double px = p.getLng(), py = p.getLat();
        double ax = a.getLng(), ay = a.getLat();
        double bx = b.getLng(), by = b.getLat();

        double cross = (py - ay) * (bx - ax) - (px - ax) * (by - ay);
        if (Math.abs(cross) > 1e-10) return false;

        double dot = (px - ax) * (px - bx) + (py - ay) * (py - by);
        if (dot > 1e-10) return false;

        return true;
    }
}
