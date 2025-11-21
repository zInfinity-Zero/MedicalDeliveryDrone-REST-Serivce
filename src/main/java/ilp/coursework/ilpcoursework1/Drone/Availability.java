package ilp.coursework.ilpcoursework1.Drone;

import ilp.coursework.ilpcoursework1.CW3.TimeInterval;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Availability {
    private DayOfWeek dayOfWeek;
    private LocalTime from;
    private LocalTime until;

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getFrom() {
        return from;
    }

    public void setFrom(LocalTime from) {
        this.from = from;
    }

    public LocalTime getUntil() {
        return until;
    }

    public void setUntil(LocalTime until) {
        this.until = until;
    }

    /// CW£ entension
    public static List<TimeInterval> getIntervalsForDate(//convert weekly aval to time intervals
            List<Availability> availabilities,
            LocalDate date) {

        List<TimeInterval> intervals = new ArrayList<>();

        for (Availability av : availabilities) {
            if (av.getDayOfWeek() == date.getDayOfWeek()) {
                LocalDateTime start = LocalDateTime.of(date, av.getFrom());
                LocalDateTime end = LocalDateTime.of(date, av.getUntil());
                intervals.add(new TimeInterval(start, end));
            }
        }

        return intervals;
    }
    public static boolean fitsInAnyInterval( //check if task fits aval window
            List<TimeInterval> intervals,
            LocalDateTime taskStart,
            double durationMinutes) {

        LocalDateTime taskEnd = taskStart.plusMinutes((long) durationMinutes);
        TimeInterval task = new TimeInterval(taskStart, taskEnd);

        return intervals.stream().anyMatch(interval -> interval.contains(task));
    }
}