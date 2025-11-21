package ilp.coursework.ilpcoursework1.CW3;

import ilp.coursework.ilpcoursework1.Drone.Drone;

public class BatteryModel {
    private double capacity;                    // Wh
    private double baseConsumptionPerStep;      // Wh per step
    private double consumptionPayloadFactor;    // Additional Wh per unit payload
    private double consumptionWindFactor;       // Wh per m/s headwind
    private double degradationFactor;           // Degradation multiplier (0-1)
    private double currentCharge;               // Runtime state (Wh)

    public BatteryModel() {
        this.currentCharge = this.capacity; // Start fully charged
    }

    public double getCapacity() { return capacity; }
    public void setCapacity(double capacity) {
        this.capacity = capacity;
        this.currentCharge = capacity;
    }

    public double getBaseConsumptionPerStep() { return baseConsumptionPerStep; }
    public void setBaseConsumptionPerStep(double baseConsumptionPerStep) {
        this.baseConsumptionPerStep = baseConsumptionPerStep;
    }

    public double getConsumptionPayloadFactor() { return consumptionPayloadFactor; }
    public void setConsumptionPayloadFactor(double consumptionPayloadFactor) {
        this.consumptionPayloadFactor = consumptionPayloadFactor;
    }

    public double getConsumptionWindFactor() { return consumptionWindFactor; }
    public void setConsumptionWindFactor(double consumptionWindFactor) {
        this.consumptionWindFactor = consumptionWindFactor;
    }

    public double getDegradationFactor() { return degradationFactor; }
    public void setDegradationFactor(double degradationFactor) {
        this.degradationFactor = degradationFactor;
    }

    public double getCurrentCharge() { return currentCharge; }
    public void setCurrentCharge(double currentCharge) {
        this.currentCharge = currentCharge;
    }

    public void discharge(double amount) {
        this.currentCharge = Math.max(0, this.currentCharge - amount);
    }

    public void recharge(double amount) {
        this.currentCharge = Math.min(this.capacity, this.currentCharge + amount);
    }

    public void fullyRecharge() {
        this.currentCharge = this.capacity;
    }

    public double getChargePercent() {
        return (currentCharge / capacity) * 100.0;
    }
}