package ilp.coursework.ilpcoursework1.Drone;

import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;

import java.util.List;

public class RestrictedZone {

    private int id;
    private List<Position> vertices;

    private String name;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public List<Position> getVertices() { return vertices; }
    public void setVertices(List<Position> vertices) { this.vertices = vertices; }


    public String getName() { return name; }
    public void setName(String name) { this.name = name; }


}