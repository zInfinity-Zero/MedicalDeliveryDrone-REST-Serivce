package ilp.coursework.ilpcoursework1.Drone;

import ilp.coursework.ilpcoursework1.CW3.BatteryModel;

public class Capability {
    private boolean cooling;
    private boolean heating;
    private double capacity;

    private int maxMoves;

    private double costPerMove;

    private double costInitial;
    private double costFinal;

    public boolean isCooling() { return cooling; }
    public void setCooling(boolean cooling) { this.cooling = cooling; }

    public boolean isHeating() { return heating; }
    public void setHeating(boolean heating) { this.heating = heating; }

    public double getCapacity() { return capacity; }
    public void setCapacity(double capacity) { this.capacity = capacity; }

    public int getMaxMoves() { return maxMoves; }
    public void setMaxMoves(int maxMoves) { this.maxMoves = maxMoves; }

    public double getCostPerMove() { return costPerMove; }
    public void setCostPerMove(double costPerMove) { this.costPerMove = costPerMove; }

    public double getCostInitial() { return costInitial; }
    public void setCostInitial(double costInitial) { this.costInitial = costInitial; }

    public double getCostFinal() { return costFinal; }
    public void setCostFinal(double costFinal) { this.costFinal = costFinal; }


    /// CW3 extension
    private BatteryModel battery;
    private double cruiseSpeed;        // m/s in calm conditions
    private double windSensitivity;    // Speed reduction factor per m/s wind

    public BatteryModel getBattery() { return battery; }
    public void setBattery(BatteryModel battery) { this.battery = battery; }

    public double getCruiseSpeed() { return cruiseSpeed; }
    public void setCruiseSpeed(double cruiseSpeed) { this.cruiseSpeed = cruiseSpeed; }

    public double getWindSensitivity() { return windSensitivity; }
    public void setWindSensitivity(double windSensitivity) {
        this.windSensitivity = windSensitivity;
}
}
