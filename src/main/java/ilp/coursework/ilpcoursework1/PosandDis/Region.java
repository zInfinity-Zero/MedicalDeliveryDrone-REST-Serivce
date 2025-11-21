package ilp.coursework.ilpcoursework1.PosandDis;
import java.util.List;

public class Region {
    private String name;
    private List<Position> vertices;

    public Region() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Position> getVertices() { return vertices; }
    public void setVertices(List<Position> vertices) { this.vertices = vertices; }
}