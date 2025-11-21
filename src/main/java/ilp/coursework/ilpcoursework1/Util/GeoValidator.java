package ilp.coursework.ilpcoursework1.Util;

import ilp.coursework.ilpcoursework1.PosandDis.Position;

public class GeoValidator {

    public static boolean isValid(Position p) {
        if (p == null || p.getLat() == null || p.getLng() == null) return false;
        return isValidLat(p.getLat()) && isValidLng(p.getLng());
    }

    public static boolean isValidLat(double lat) {
        return lat >= -90 && lat <= 90;
    }

    public static boolean isValidLng(double lng) {
        return lng >= -180 && lng <= 180;
    }
}
