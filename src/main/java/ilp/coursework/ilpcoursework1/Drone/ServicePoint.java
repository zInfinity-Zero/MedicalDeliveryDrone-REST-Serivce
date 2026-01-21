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
        public Location(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }
    }


    public void setLocation(Location location) {this.location = location;}

    // existing LngLat i realise i messed up when im almost done
    public DeliveryPathDTO.LngLat getPosition() {
        return new DeliveryPathDTO.LngLat(location.lng, location.lat);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }



    /// CW3 Extension
    private int maxConcurrentSlots;     // How many drones can use depot simultaneously

    private double rechargeRate;        // Wh per minute
    private double fullRechargeTime;    // Alternative: fixed time to full charge

    public int getMaxConcurrentSlots() { return maxConcurrentSlots; }
    public void setMaxConcurrentSlots(int maxConcurrentSlots) {
        this.maxConcurrentSlots = maxConcurrentSlots;
    }


    public double getRechargeRate() { return rechargeRate; }
    public void setRechargeRate(double rechargeRate) {
        this.rechargeRate = rechargeRate;
    }


}
