package ilp.coursework.ilpcoursework1.CW3;

import ilp.coursework.ilpcoursework1.Drone.Availability;
import ilp.coursework.ilpcoursework1.Drone.Drone;
import ilp.coursework.ilpcoursework1.Drone.ServicePoint;
import ilp.coursework.ilpcoursework1.Util.MedDispatchRec;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SchedulingService {

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
     * Count how many drones are using depot at specific time
     */
    private int countConcurrentAt(List<TimeSlot> slots, LocalDateTime time) {
        return (int) slots.stream()
                .filter(slot -> !time.isBefore(slot.start) && !time.isAfter(slot.end))
                .count();
    }
    // Consider both availability AND dispatch time
    // Earliest time the drone is allowed to start work for this dispatch

    public LocalDateTime getEarliestAvailabilityForDispatch(Drone drone, MedDispatchRec dispatch) {
        LocalDate date = dispatch.getDate();
        LocalTime requestedTime = dispatch.getTime();
        LocalDateTime requestedDateTime = LocalDateTime.of(date, requestedTime);

        List<LocalDateTime> availabilityStarts = drone.getAvailability().stream()
                .filter(av -> av.getDayOfWeek() == date.getDayOfWeek())
                .map(av -> LocalDateTime.of(date, av.getFrom()))
                .collect(Collectors.toList());

        if (availabilityStarts.isEmpty()) {
            // No availability this day, just return requested time; feasibility will fail later
            return requestedDateTime;
        }

        LocalDateTime earliestAvailability = availabilityStarts.stream()
                .min(LocalDateTime::compareTo)
                .orElse(requestedDateTime);

        // Drone cannot start before availability, and cannot start before requested dispatch time
        return earliestAvailability.isAfter(requestedDateTime)
                ? earliestAvailability
                : requestedDateTime;
    }

    public LocalDateTime getEarliestAvailability(Drone drone, LocalDate date) {
        return drone.getAvailability().stream()
                .filter(av -> av.getDayOfWeek() == date.getDayOfWeek())
                .map(av -> LocalDateTime.of(date, av.getFrom()))
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.of(date, LocalTime.of(9, 0)));
    }
}