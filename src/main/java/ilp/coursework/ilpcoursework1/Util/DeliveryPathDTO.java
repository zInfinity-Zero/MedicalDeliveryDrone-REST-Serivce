package ilp.coursework.ilpcoursework1.Util;

import java.util.ArrayList;
import java.util.List;

public class DeliveryPathDTO {
    /// note this this not needed but i forgot i had a position class therefore created a lnglat class for cw2 both can be used interchangably with converter
    public static class LngLat {
        private double lng;
        private double lat;
        public LngLat() {}
        public LngLat(double lng, double lat) { this.lng = lng; this.lat = lat; }

        public double getLng() {
            return lng;
        }
        public double getLat() {return lat;}
    }

    public static class DeliveryPath {
        private int deliveryId;
        private List<LngLat> flightPath;

        public  DeliveryPath(int deliveryId, List<LngLat> flightPath) {this.deliveryId = deliveryId; this.flightPath = flightPath;}
        public DeliveryPath() {};
        public int getDeliveryId() {return deliveryId;}
        public List<LngLat> getFlightPath() {return flightPath;}

        public void setDeliveryId(int deliveryId) {
            this.deliveryId = deliveryId;
        }
        public void setFlightPath(List<LngLat> flightPath) {this.flightPath = flightPath;}
    }

    public static class DronePath {
        private String droneId;
        private List<DeliveryPath> deliveries = new ArrayList<>();
        public  DronePath(String droneId,List<DeliveryPath> deliveries) {this.droneId = droneId; this.deliveries =  deliveries;}
        public DronePath(){};
        public String getDroneId() {return droneId;}
        public List<DeliveryPath> getDeliveries() {return deliveries;}

        public void setDroneId(String droneId) {
            this.droneId = droneId;
        }

        public void setDeliveries(List<DeliveryPath> deliveries) {
            this.deliveries = deliveries;
        }

    }

    public static class CalcResult {
        private double totalCost;
        private int totalMoves;
        private List<DronePath> dronePaths = new ArrayList<>();

        public  CalcResult (double totalCost, int totalMoves, List<DronePath> dronePaths) {this.totalCost = totalCost; this.totalMoves = totalMoves; this.dronePaths = dronePaths;}
        public  CalcResult () {};

        public double getTotalCost() {return totalCost;}
        public int getTotalMoves() {return totalMoves;}
        public List<DronePath> getDronePaths() {return dronePaths;}

        public void setTotalCost(double totalCost) {this.totalCost = totalCost;}
        public void setTotalMoves(int totalMoves) {this.totalMoves = totalMoves;}

        public void setDronePaths(List<DronePath> dronePaths) {
            this.dronePaths = dronePaths;
        }
    }
}
