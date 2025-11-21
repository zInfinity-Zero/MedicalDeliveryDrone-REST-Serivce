package ilp.coursework.ilpcoursework1.Drone;

import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;

import java.util.List;

public class ServicePoint {

    private int id;
    private String name;
    private Location location;
    private List<Drone> drones;     // drones available at this SP


    public static class Location {
        private double lng;
        private double lat;
        private Double alt; // optional, ILP sends alt: null sometimes

        public double getLng() { return lng; }
        public double getLat() { return lat; }
    }


    public Location getLocation() {
        return location;
    }

    // existing LngLat i realise i messed up when im almost done
    public DeliveryPathDTO.LngLat getPosition() {
        return new DeliveryPathDTO.LngLat(location.lng, location.lat);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }


    public List<Drone> getDrones() { return drones; }
    public void setDrones(List<Drone> drones) { this.drones = drones; }
}
