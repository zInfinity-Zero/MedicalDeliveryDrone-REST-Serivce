package ilp.coursework.ilpcoursework1.CW3;

import java.time.Duration;
import java.time.LocalDateTime;

public class TimeInterval {
    private LocalDateTime start;
    private LocalDateTime end;

    public TimeInterval(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public boolean overlaps(TimeInterval other) {
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start);
    }

    public boolean contains(LocalDateTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    public boolean contains(TimeInterval other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
}