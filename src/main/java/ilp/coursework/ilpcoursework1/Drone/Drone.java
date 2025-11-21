package ilp.coursework.ilpcoursework1.Drone;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Drone {

    private String id;
    private String name;
    private Capability capability;

    private List<Availability> availability = new ArrayList<>();
    private int spId ;


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Capability getCapability() { return capability; }
    public void setCapability(Capability capability) { this.capability = capability; }


    public int getServicePointId() { return spId; }
    public void setServicePointId(int spId) { this.spId = spId; }

    public List<Availability> getAvailability() { return availability; }
    public void setAvailability(List<Availability> availability) {
        this.availability = availability;
    }
}