package ilp.coursework.ilpcoursework1.CW3;

public class WindEnvironment {
    private double windDirection;  // Degrees (0-360)
    private double windSpeed;      // m/s

    public WindEnvironment() {}

    public WindEnvironment(double direction, double speed) {
        this.windDirection = direction;
        this.windSpeed = speed;
    }

    public double getWindDirection() { return windDirection; }
    public void setWindDirection(double windDirection) {
        this.windDirection = windDirection % 360;
    }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }

    @Override
    public String toString() {
        return String.format("Wind[dir=%.1f°, speed=%.2f m/s]", windDirection, windSpeed);
    }
}
