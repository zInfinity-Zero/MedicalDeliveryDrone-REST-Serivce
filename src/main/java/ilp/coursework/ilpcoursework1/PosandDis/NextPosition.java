package ilp.coursework.ilpcoursework1.PosandDis;

public class NextPosition {
    private Position next;

    public NextPosition() {}

    public NextPosition(Position next) {
        this.next = next;
    }

    public Position getNext() { return next; }
    public void setNext(Position next) { this.next = next; }
}
