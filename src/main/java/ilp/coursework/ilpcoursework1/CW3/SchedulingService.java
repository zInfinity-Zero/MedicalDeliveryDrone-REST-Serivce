package ilp.coursework.ilpcoursework1.CW3;

import ilp.coursework.ilpcoursework1.Drone.Availability;
import ilp.coursework.ilpcoursework1.Drone.Drone;
import ilp.coursework.ilpcoursework1.Drone.ServicePoint;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchedulingService {

    // Track depot slot usage: servicePointId -> list of time slots
    private Map<Integer, List<TimeSlot>> depotSchedule = new ConcurrentHashMap<>();

    public static class TimeSlot {
        LocalDateTime start;
        LocalDateTime end;
        String droneId;

        public TimeSlot(LocalDateTime start, LocalDateTime end, String droneId) {
            this.start = start;
            this.end = end;
            this.droneId = droneId;
        }
    }

    /**
     * Check if drone is available during the required time window
     */
    public boolean fitsInAvailability(
            Drone drone,
            LocalDateTime start,
            double durationMinutes) {

        LocalDateTime end = start.plusMinutes((long) durationMinutes);

        for (Availability av : drone.getAvailability()) {
            if (av.getDayOfWeek() == start.getDayOfWeek()) {
                LocalTime startTime = start.toLocalTime();
                LocalTime endTime = end.toLocalTime();

                if (!startTime.isBefore(av.getFrom()) &&
                        !endTime.isAfter(av.getUntil())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculate queue delay at service point
     */
    public double calculateQueueDelay(
            ServicePoint sp,
            LocalDateTime arrivalTime,
            double operationDuration) {

        List<TimeSlot> slots = depotSchedule.getOrDefault(
                sp.getId(), new ArrayList<>());

        // Find first available slot
        LocalDateTime earliestStart = arrivalTime;

        while (countConcurrentAt(slots, earliestStart) >= sp.getMaxConcurrentSlots()) {
            earliestStart = earliestStart.plusMinutes(1);
        }

        return Duration.between(arrivalTime, earliestStart).toMinutes();
    }

    /**
     * Reserve a time slot at a service point
     */
    public void reserveSlot(
            int servicePointId,
            LocalDateTime start,
            LocalDateTime end,
            String droneId) {

        depotSchedule.computeIfAbsent(servicePointId, k -> new ArrayList<>())
                .add(new TimeSlot(start, end, droneId));
    }

    /**
     * Clear all reservations (useful for replanning)
     */
    public void clearAllReservations() {
        depotSchedule.clear();
    }

    /**
     * Count how many drones are using depot at specific time
     */
    private int countConcurrentAt(List<TimeSlot> slots, LocalDateTime time) {
        return (int) slots.stream()
                .filter(slot -> !time.isBefore(slot.start) && !time.isAfter(slot.end))
                .count();
    }
}