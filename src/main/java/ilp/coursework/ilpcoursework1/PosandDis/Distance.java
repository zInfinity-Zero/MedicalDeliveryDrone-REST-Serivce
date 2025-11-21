package ilp.coursework.ilpcoursework1.PosandDis;

public class Distance {

    private Position position1;
    private Position position2;

    public Distance() {}

    public Distance(Position p1, Position p2) {
        this.position1 = p1;
        this.position2 = p2;
    }
    public Position getPosition1() { return position1; }
    public void setPosition1(Position position1) { this.position1 = position1; }

    public Position getPosition2() { return position2; }
    public void setPosition2(Position position2) { this.position2 = position2; }
}
