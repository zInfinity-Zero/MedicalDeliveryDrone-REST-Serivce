package ilp.coursework.ilpcoursework1.PosandDis;

public class Position {
    private Double lat;
    private Double lng;

    public Position() {}

    public Position(Double lng, Double lat) {
        this.lng = lng;
        this.lat = lat;

    }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
