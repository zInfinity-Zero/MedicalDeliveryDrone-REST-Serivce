package ilp.coursework.ilpcoursework1.Util;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public class MedDispatchRec {

    private int id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime time;
    private Requirements requirements;

    private Delivery delivery;

    public static class Delivery {
        private double lng;
        private double lat;

        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
    }
    public Delivery getDelivery() { return delivery; }
    public void setDelivery(Delivery delivery) { this.delivery = delivery; }
    public static class Requirements {
        private Double capacity;
        private Boolean cooling;
        private Boolean heating;
        private Double maxCost;


        public Double getCapacity() {
            return capacity;
        }
        public void setCapacity(Double capacity) {this.capacity = capacity;}

        public Boolean getCooling() {
            return cooling;
        }

        public void setCooling(Boolean cooling) {
            this.cooling = cooling;
        }

        public Boolean getHeating() {
            return heating;
        }

        public void setHeating(Boolean heating) {
            this.heating = heating;
        }

        public Double getMaxCost() {
            return maxCost;
        }

        public void setMaxCost(Double maxCost) {
            this.maxCost = maxCost;
        }
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {this.id = id;}

    public LocalDate getDate() {return date;}
    public void setDate(LocalDate date) {this.date = date;}
    public LocalTime getTime() {return time;}
    public void setTime(LocalTime time) {this.time = time;}
    public Requirements getRequirements() {return requirements;}
    public void setRequirements(Requirements requirements) {this.requirements = requirements;}

}