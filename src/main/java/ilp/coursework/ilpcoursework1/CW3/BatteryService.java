package ilp.coursework.ilpcoursework1.CW3;

import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BatteryService {

    /**
     * consumption for a single step
     */
    public double calculateStepConsumption(
            BatteryModel battery,
            double payloadFraction) {

        double base = battery.getBaseConsumptionPerStep();
        double payload = payloadFraction * battery.getConsumptionPayloadFactor();

        return base + payload;
    }

    /**
     * degradation factor
     */
    public double applyDegradation(
            double consumption,
            double degradationFactor,
            double payloadFraction) {


        return consumption * (1 + degradationFactor * payloadFraction);
    }

    /**
     * Check if battery can complete entire path
     */
    public boolean canCompletePath(
            List<DeliveryPathDTO.LngLat> path,
            BatteryModel battery,
            double currentCharge,
            double payloadFraction) {

        double remaining = currentCharge;

        for (int i = 0; i < path.size() - 1; i++) {
            double consumption = calculateStepConsumption(battery, payloadFraction);

            consumption = applyDegradation(
                    consumption,
                    battery.getDegradationFactor(),
                    payloadFraction);

            remaining -= consumption;

            if (remaining < 0) return false;
        }

        return true;
    }

    /**
     * battery consumption for a path
     */
    public double calculatePathConsumption(
            List<DeliveryPathDTO.LngLat> path,
            BatteryModel battery,
            double payloadFraction) {

        double totalConsumption = 0;
        double currentCharge = battery.getCurrentCharge();

        for (int i = 0; i < path.size() - 1; i++) {
            double consumption = calculateStepConsumption(battery, payloadFraction);

            consumption = applyDegradation(
                    consumption,
                    battery.getDegradationFactor(),
                    payloadFraction);

            totalConsumption += consumption;
        }

        return totalConsumption;
    }
}