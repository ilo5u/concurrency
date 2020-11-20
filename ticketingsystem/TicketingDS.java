package ticketingsystem;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {
    private final AtomicInteger id;
    private final int routemax;
    private final int coachmax;
    private final int seatmax;
    private final int stationmax;
    private final Semaphore sellers;

    private final int tourmax;
    private final int nomax;
    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        id = new AtomicInteger(0);
        routemax = routenum;
        coachmax = coachnum;
        seatmax = seatnum;
        stationmax = stationnum;
        sellers = new Semaphore(threadnum);

        /// how many tours in total (only single direction)
        tourmax = (stationmax * (stationmax - 1)) >> 1;
        // how many No. in total
        nomax = (coachmax - 1) * Math.max(coachmax, seatmax) + seatmax;

        initRoutes();
    }

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        try {
            sellers.acquire(); // passenger attempts to acquire an ticket window
            // passenger gets a seller

            // buy one ticket
            int no = routes[route].acquire(hashTour(departure, arrival), passenger);
            if (no == -1) {
                return null;
            } else {
                Ticket ticket = new Ticket();
                ticket.tid = id.incrementAndGet();
                ticket.passenger = passenger;
                ticket.route = route;
                ticket.coach = getCoach(no);
                ticket.seat = getSeat(no);
                ticket.departure = departure;
                ticket.arrival = arrival;
                return ticket;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sellers.release(); // passenger leaves ticket window
        }
        return null;
    }

    public int inquiry(int route, int departure, int arrival) {
        try {
            sellers.acquire(); // passenger attempts to acquire an ticket window
            // passenger gets a seller

            // inquiry amount of the rest tickets
            return routes[route].count(hashTour(departure, arrival));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sellers.release(); // passenger leaves ticket window
        }
        return 0;
    }

    public boolean refundTicket(Ticket ticket) {
        try {
            sellers.acquire(); // passenger attempts to acquire an ticket window
            // passenger gets a seller

            // refund one ticket
            return routes[ticket.route].release(
                    hashTour(ticket.departure, ticket.arrival),
                    hashNo(ticket.coach, ticket.seat),
                    ticket.passenger);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sellers.release(); // passenger leaves ticket window
        }
        return false;
    }

    // nomax
    // ^
    // |-------------------
    // | 8 || 8 |     | 8 |
    // |---||---|     |---|
    // |...||...|     |...|
    // |---||---|     |---|
    // | 1 || 1 |     | 1 | --> <D1 A2><D1 A3>< ... ><D7 A8> -> tourmax
    // --------------------
    // <R 1><R 2><...><R 4>
    public class RouteInfo {
        public final int id;

        public RouteInfo(int key) {
            id = key;
            // initial tour index table
            initTours();
        }

        public class SeatInfo {
            public SeatInfo() {
                vector = new int[tourmax + 1];
                lock = new ReentrantLock();
            }
            public boolean or(int tourid, int no, String name, Set<Integer> mask) {
                lock.lock();
                if (vector[tourid] > 0) {
                    lock.unlock();
                    return false;
                }

                for (int tour : mask) {
                    if (vector[tour] == 0) {
                        rests[tour].decrementAndGet();
                    }
                    ++vector[tour];
                }

                passengers[hashPass(tourid, no)] = name;
                lock.unlock();
                return true;
            }
            public void xor(int pass, int no, Set<Integer> mask) {
                lock.lock();
                for (int tour : mask) {
                    --vector[tour];
                    if (vector[tour] == 0) {
                        rests[tour].incrementAndGet();
                        empty[tour].set(no);
                    }
                }
                passengers[pass] = null;
                lock.unlock();
            }

            // @key hashed index of which tour e.g. <1,2> = 1 <1,3> = 2
            public boolean empty(int key) {
                return vector[key] == 0;
            }

            // each element referred to a cell
            // true: sold
            // false: empty
            int[] vector = null;
            Lock lock = null;
        }
        // dynamic records
        SeatInfo[] tours = null;
        String[] passengers = null;

        // initialize only once
        // speed up the masking
        Set<Integer>[] collison = null;

        // speed up the inquiry
        AtomicInteger[] rests = null;
        AtomicInteger[] empty = null;

        private void initTours() {
            tours = new SeatInfo[nomax + 1];
            for (int i = 1; i <= nomax; ++i) {
                tours[i] = new SeatInfo();
            }
            passengers = new String[tourmax * nomax + 1];

            collison = new Set[tourmax + 1];
            for (int i = 1; i <= tourmax; ++i) {
                collison[i] = new HashSet<>();
                for (int j = 1; j <= tourmax; ++j) {
                    collison[i].add(j);
                }
            }
            // initialize collision table
            for (int dep = 1; dep <= stationmax; ++dep) {
                for (int arr = dep + 1; arr <= stationmax; ++arr) {
                    int key = hashTour(dep, arr);
                    // release left part
                    for (int i = 1; i < dep; ++i) {
                        for (int j = i + 1; j <= dep; ++j) {
                            collison[key].remove(hashTour(i, j));
                        }
                    }
                    // release right part
                    for (int i = arr; i <= stationmax; ++i) {
                        for (int j = i + 1; j <= stationmax; ++j) {
                            collison[key].remove(hashTour(i, j));
                        }
                    }
                }
            }

            rests = new AtomicInteger[tourmax + 1];
            for (int i = 1; i <= tourmax; ++i) {
                rests[i] = new AtomicInteger(nomax);
            }
            empty = new AtomicInteger[tourmax + 1];
            for (int i = 1; i <= tourmax; ++i) {
                empty[i] = new AtomicInteger(1);
            }
        }

        public int acquire(int tourid, String name) {
            retry: while (true) {
                if (rests[tourid].get() == 0) return -1;
                int no = empty[tourid].get(); // get the possible empty seat no
                int next = no % nomax + 1;
                if (tours[no].or(tourid, no, name, collison[tourid])) {
                    empty[tourid].compareAndSet(no, next); // update it if no refund
                    // still empty
                    return no;
                } else {
                    // sold out
                    while (rests[tourid].get() > 0) {
                        // test the next seat
                        if (tours[next].empty(tourid)) {
                            if (tours[next].or(tourid, next, name, collison[tourid])) {
                                empty[tourid].compareAndSet(no, next); // update it if no refund
                                return next;
                            }
                        }
                        if (empty[tourid].get() != no) {
                            continue retry; // try to get the refunded seat
                        }
                        next = next % nomax + 1;
                    }
                    return -1;
                }
            }
        }

        public boolean release(int tourid, int no, String name) {
            final int hash = hashPass(tourid, no);
            if (tours[no].empty(tourid)
                    || passengers[hash] == null || !passengers[hash].equals(name)) {
                return false;
            } else {
                tours[no].xor(hash, no, collison[tourid]);
                return true;
            }
        }

        public int count(int tourid) {
            return rests[tourid].get();
        }
    }
    RouteInfo[] routes = null;

    private void initRoutes() {
        routes = new RouteInfo[routemax + 1];
        for (int i = 1; i <= routemax; ++i) {
            routes[i] = new RouteInfo(i);
        }
    }

    private int hashTour(int departure, int arrival) {
        return ((((stationmax << 1) - departure) * (departure - 1)) >> 1) + arrival - departure;
    }

    private int hashNo(int coach, int seat) {
        return (coach - 1) * Math.max(coachmax, seatmax) + seat;
    }

    private int hashPass(int tour, int sel) {
        return (tour - 1) * Math.max(nomax, tourmax) + sel;
    }

    private int getCoach(int no) {
        int base = Math.max(coachmax, seatmax);
        if (no % base == 0) {
            return no / base;
        } else {
            return (no / base) + 1;
        }
    }

    private int getSeat(int no) {
        int seat = no % Math.max(coachmax, seatmax);
        if (seat == 0) {
            return seatmax;
        } else {
            return seat;
        }
    }
}
