package ticketingsystem.verify;

import java.util.Objects;

public class Execution {
    public final long preTime;
    public final long postTime;
    public enum Opcode { NULL, BUY, REFUND, SOLD, INQUIRY, ERROR };
    public final Execution.Opcode opcode;
    public final int threadId;
    public final long ticketId;
    public final String name;
    public final int route;
    public final int coach;
    public final int seat;
    public final int departure;
    public final int arrival;
    public final int left;

    static public Execution.Opcode parse(String trace) {
        if (trace.contains("TicketBought")) {
            return Execution.Opcode.BUY;
        } else if (trace.contains("TicketRefund")) {
            return Execution.Opcode.REFUND;
        } else if (trace.contains("TicketSoldOut")) {
            return Execution.Opcode.SOLD;
        } else if (trace.contains("ErrorOfRefund")) {
            return Execution.Opcode.ERROR;
        } else if (trace.contains("RemainTicket")) {
            return Execution.Opcode.INQUIRY;
        } else {
            return Execution.Opcode.NULL;
        }
    }

    public Execution(long preTime, long postTime, Execution.Opcode opcode, int threadId) {
        this.preTime = preTime;
        this.postTime = postTime;
        this.opcode = opcode;
        this.threadId = threadId;
        this.ticketId = -1;
        this.name = null;
        this.route = -1;
        this.coach = -1;
        this.seat = -1;
        this.departure = -1;
        this.arrival = -1;
        this.left = -1;
    }

    public Execution(long preTime, long postTime, Execution.Opcode opcode, int threadId, long ticketId, String name, int route, int coach, int seat, int departure, int arrival) {
        this.preTime = preTime;
        this.postTime = postTime;
        this.opcode = opcode;
        this.threadId = threadId;
        this.ticketId = ticketId;
        this.name = name;
        this.route = route;
        this.coach = coach;
        this.seat = seat;
        this.departure = departure;
        this.arrival = arrival;
        this.left = -1;
    }

    public Execution(long preTime, long postTime, Execution.Opcode opcode, int threadId, int route, int departure, int arrival) {
        this.preTime = preTime;
        this.postTime = postTime;
        this.opcode = opcode;
        this.threadId = threadId;
        this.ticketId = -1;
        this.name = null;
        this.route = route;
        this.coach = -1;
        this.seat = -1;
        this.departure = departure;
        this.arrival = arrival;
        this.left = -1;
    }

    public Execution(long preTime, long postTime, Execution.Opcode opcode, int threadId, int route, int departure, int arrival, int left) {
        this.preTime = preTime;
        this.postTime = postTime;
        this.opcode = opcode;
        this.threadId = threadId;
        this.ticketId = -1;
        this.name = null;
        this.route = route;
        this.coach = -1;
        this.seat = -1;
        this.departure = departure;
        this.arrival = arrival;
        this.left = left;
    }

    static public Execution build(String trace) {
        String[] sub = trace.split(" ");
        Execution.Opcode op = parse(trace);
        switch (op) {
            case BUY:
            case REFUND: return new Execution(
                    Long.parseLong(sub[0]),
                    Long.parseLong(sub[1]),
                    op,
                    Integer.parseInt(sub[2]),
                    Long.parseLong(sub[4]),
                    sub[5],
                    Integer.parseInt(sub[6]),
                    Integer.parseInt(sub[7]),
                    Integer.parseInt(sub[10]),
                    Integer.parseInt(sub[8]),
                    Integer.parseInt(sub[9]));
            case INQUIRY: return new Execution(
                    Long.parseLong(sub[0]),
                    Long.parseLong(sub[1]),
                    op,
                    Integer.parseInt(sub[2]),
                    Integer.parseInt(sub[5]),
                    Integer.parseInt(sub[6]),
                    Integer.parseInt(sub[7]),
                    Integer.parseInt(sub[4]));
            case SOLD: return new Execution(
                    Long.parseLong(sub[0]),
                    Long.parseLong(sub[1]),
                    op,
                    Integer.parseInt(sub[2]),
                    Integer.parseInt(sub[4]),
                    Integer.parseInt(sub[5]),
                    Integer.parseInt(sub[6]));
            case ERROR: return new Execution(
                    Long.parseLong(sub[0]),
                    Long.parseLong(sub[1]),
                    op,
                    Integer.parseInt(sub[2]));
            default: return null;
        }
    }

    static public boolean overlappedAndConflict(Execution e1, Execution e2) {
        if ((e2.preTime - e1.postTime) * (e2.postTime - e1.preTime) < 0) {
            if (e1.opcode != Execution.Opcode.ERROR && e2.opcode != Execution.Opcode.ERROR) {
                return e1.route == e2.route
                        && ((e2.departure - e1.arrival) * (e2.arrival - e1.departure)) < 0;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    static public boolean overlapped(Execution e1, Execution e2) {
        if (e1.opcode != Execution.Opcode.ERROR && e2.opcode != Execution.Opcode.ERROR) {
            return (e2.preTime - e1.postTime) * (e2.postTime - e1.preTime) < 0;
        } else {
            return false;
        }
    }

    public String toString() {
        switch (opcode) {
            case BUY: return preTime + " " + postTime + " " +
                    threadId + " " +
                    "TicketBought" + " " +
                    ticketId + " " +
                    name + " " +
                    route + " " +
                    coach  + " " +
                    departure + " " +
                    arrival + " " +
                    seat;
            case REFUND: return preTime + " " + postTime + " " +
                    threadId + " " +
                    "TicketRefund" + " " +
                    ticketId + " " +
                    name + " " +
                    route + " " +
                    coach  + " " +
                    departure + " " +
                    arrival + " " +
                    seat;
            case INQUIRY: return preTime + " " + postTime + " " +
                    threadId + " " +
                    "RemainTicket" + " " +
                    left + " " +
                    route+ " " +
                    departure+ " " +
                    arrival;
            case SOLD: return preTime + " " + postTime + " " +
                    threadId + " " +
                    "TicketSoldOut" + " " +
                    route + " " +
                    departure+ " " +
                    arrival;
            case ERROR: return preTime + " " + postTime + " " +
                    threadId + " " +
                    "ErrOfRefund";
            default: return null;
        }
    }

    public Ticket toTicket() {
        Ticket ticket = new Ticket();
        ticket.tid = ticketId;
        ticket.passenger = name;
        ticket.route = route;
        ticket.coach = coach;
        ticket.seat = seat;
        ticket.departure = departure;
        ticket.arrival = arrival;
        return ticket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Execution execution = (Execution) o;
        return preTime == execution.preTime && postTime == execution.postTime && threadId == execution.threadId && ticketId == execution.ticketId && route == execution.route && coach == execution.coach && seat == execution.seat && departure == execution.departure && arrival == execution.arrival && left == execution.left && opcode == execution.opcode && Objects.equals(name, execution.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preTime, postTime, opcode, threadId, ticketId, name, route, coach, seat, departure, arrival, left);
    }
}
