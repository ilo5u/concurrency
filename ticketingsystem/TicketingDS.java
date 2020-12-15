package ticketingsystem;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TicketingDS implements TicketingSystem {
    private final AtomicLong tid;
    private final int routemax;
    private final int coachmax;
    private final int seatmax;
    private final int stationmax;

    private final int maxCoachAndSeat;
    private final int maxTourAndNo;

    private final int tourmax;
    private final int nomax;
    private final Semaphore windows;
    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        tid = new AtomicLong(0);
        routemax = routenum;
        coachmax = coachnum;
        seatmax = seatnum;
        stationmax = stationnum;

        maxCoachAndSeat = Math.max(coachmax, seatmax);

        /// how many tours in total (only single direction)
        tourmax = (stationmax * (stationmax - 1)) >> 1;
        // how many No. in total
        nomax = (coachmax - 1) * maxCoachAndSeat + seatmax;
        maxTourAndNo = Math.max(tourmax, nomax);

        windows = new Semaphore(threadnum);

        initRoutes();
    }

    static private class NoAndTicket {
        public int no;
        public long ticket;
        public NoAndTicket(int no, long ticket) {
            this.no = no;
            this.ticket = ticket;
        }
    }

    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        try {
            windows.acquire();
            // buy one ticket
            NoAndTicket res = routes[route].acquire(hashTour(departure, arrival), passenger);
            if (res == null) {
                return null;
            } else {
                Ticket ticket = new Ticket();
                ticket.tid = res.ticket;
                ticket.passenger = passenger;
                ticket.route = route;
                ticket.coach = getCoach(res.no);
                ticket.seat = getSeat(res.no);
                ticket.departure = departure;
                ticket.arrival = arrival;
                return ticket;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        } finally {
            windows.release();
        }
    }

    public int inquiry(int route, int departure, int arrival) {
        try {
            windows.acquire();
            // inquiry amount of the rest tickets
            return routes[route].count(hashTour(departure, arrival));
        } catch (InterruptedException e) {
            e.printStackTrace();
            return -1;
        } finally {
            windows.release();
        }
    }

    public boolean refundTicket(Ticket ticket) {
        try {
            windows.acquire();
            // refund one ticket
            return routes[ticket.route].release(
                    ticket.tid,
                    hashTour(ticket.departure, ticket.arrival),
                    hashNo(ticket.coach, ticket.seat),
                    ticket.passenger);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            windows.release();
        }
    }

    public static class TidAndPassenger {
        public long tid;
        public String name;
        public TidAndPassenger(long tid, String name) {
            this.tid = tid;
            this.name = name;
        }
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
        public RouteInfo() {
            // initial tour index table
            initTours();
        }

        public class SeatInfo {
            public SeatInfo() {
                vector = new short[tourmax + 1];
                lock = new ReentrantLock();
            }

            /**
             * try to buy one ticket (mark the relative cell)
             * @param tourid hashed code of this tour's departure and arrival
             * @param no numero of wanted seat
             * @param name passenger's name
             * @param mask other seats relative with this seats would also be marked
             * @return bought ticket if not null
             */
            public NoAndTicket or(int tourid, int no, String name, Set<Integer> mask) {
                // atomic buy method
                lock.lock(); // locked only at seat
                if (vector[tourid] > 0) {
                    // some thread competes with current thread successfully
                    // this thread would go back and try again
                    lock.unlock();
                    return null;
                }
                // current thread has hold this seat successfully
                for (int tour : mask) {
                    // mark all seats in the collision filed
                    if (vector[tour] == 0) {
                        rests[tour].decrementAndGet();
                    }
                    ++vector[tour];
                }
                // fetch the unique tid
                long ticket = tid.incrementAndGet();

                // record passenger and tid at current seat
                passengers[hashPass(tourid, no)] = new TidAndPassenger(ticket, name);

                lock.unlock(); // linearizable position for each succeed ticket bought
                return new NoAndTicket(no, ticket);
            }

            /**
             * refund bought ticket
             * @param ticket
             * @param pass
             * @param no
             * @param name
             * @param mask
             * @return true for bought ticket, otherwise false
             */
            public boolean xor(long ticket, int pass, int no, String name, Set<Integer> mask) {
                lock.lock();
                // ticket info dose not match
                if (passengers[pass] == null) {
                    lock.unlock();
                    return false;
                }
                if (passengers[pass].tid != ticket || !Objects.equals(passengers[pass].name, name)) {
                    lock.unlock();
                    return false;
                }

                for (int tour : mask) {
                    --vector[tour];
                    if (vector[tour] == 0) {
                        rests[tour].incrementAndGet();
                        empty[tour].set(no);
                    }
                }
                passengers[pass] = null;

                lock.unlock();
                return true;
            }

            // @key hashed index of which tour e.g. <1,2> = 1 <1,3> = 2
            public boolean empty(int key) {
                return vector[key] == 0;
            }

            // each element referred to a cell
            // true: sold
            // false: empty
            private volatile short[] vector = null;
            private Lock lock = null;
        }
        // dynamic records
        private SeatInfo[] tours = null;
        private TidAndPassenger[] passengers = null;

        // initialize only once
        // speed up the masking
        private Set<Integer>[] collison = null;

        // speed up the inquiry
        private AtomicInteger[] rests = null;
        private AtomicInteger[] empty = null;

        private void initTours() {
            tours = new SeatInfo[nomax + 1];
            for (int i = 1; i <= nomax; ++i) {
                tours[i] = new SeatInfo();
            }
            passengers = new TidAndPassenger[tourmax * maxTourAndNo + 1];

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

        public NoAndTicket acquire(int tourid, String name) {
            retry: while (true) {
                if (rests[tourid].get() == 0) {
                    return null;
                }
                int no = empty[tourid].get(); // get the possible empty seat no
                int next = no % nomax + 1;
                NoAndTicket res = tours[no].or(tourid, no, name, collison[tourid]);
                if (res != null) {
                    empty[tourid].compareAndSet(no, next); // update it if no refund
                    // still empty
                    return res;
                } else {
                    // sold out
                    while (rests[tourid].get() > 0) {
                        // test the next seat
                        if (tours[next].empty(tourid)) {
                            res = tours[next].or(tourid, next, name, collison[tourid]);
                            if (res != null) {
                                empty[tourid].compareAndSet(no, next); // update it if no refund
                                return res;
                            }
                        }
                        if (empty[tourid].get() != no) {
                            continue retry; // try to get the refunded seat
                        }
                        next = next % nomax + 1;
                    }
                    return null;
                }
            }
        }

        public boolean release(long ticket, int tourid, int no, String name) {
            return tours[no].xor(ticket, hashPass(tourid, no), no, name, collison[tourid]);
        }

        public int count(int tourid) {
            return rests[tourid].get();
        }
    }
    private RouteInfo[] routes = null;

    private void initRoutes() {
        routes = new RouteInfo[routemax + 1];
        for (int i = 1; i <= routemax; ++i) {
            routes[i] = new RouteInfo();
        }
    }

    private int hashTour(int departure, int arrival) {
        return ((((stationmax << 1) - departure) * (departure - 1)) >> 1) + arrival - departure;
    }

    private int hashNo(int coach, int seat) {
        return (coach - 1) * maxCoachAndSeat + seat;
    }

    private int hashPass(int tour, int sel) {
        return (tour - 1) * maxTourAndNo + sel;
    }

    private int getCoach(int no) {
        int base = maxCoachAndSeat;
        if (no % base == 0) {
            return no / base;
        } else {
            return (no / base) + 1;
        }
    }

    private int getSeat(int no) {
        int seat = no % maxCoachAndSeat;
        if (seat == 0) {
            return seatmax;
        } else {
            return seat;
        }
    }


    /**
     * only for linearizability verification
     * @param ticket wanted ticket info
     * @return not null when this bought is legal
     */
    public Ticket buy(Ticket ticket) {
        RouteInfo route = routes[ticket.route];
        int no = hashNo(ticket.coach, ticket.seat);
        int tour = hashTour(ticket.departure, ticket.arrival);
        int pass = hashPass(tour, no);

        if (route.rests[tour].get() == 0) return null;
        if (route.tours[no].vector[tour] > 0) return null;
        if (route.passengers[pass] != null) return null;

        for (int t : route.collison[tour]) {
            if (route.tours[no].vector[t] == 0) {
                route.rests[t].decrementAndGet();
            }
            ++route.tours[no].vector[t];
        }

        route.passengers[pass] = new TidAndPassenger(ticket.tid, ticket.passenger);
        return ticket;
    }

    /**
     * only for linearizability verification
     * @param ticket wanted ticket info
     * @return true when this refund is legal
     */
    public boolean refund(Ticket ticket) {
        RouteInfo route = routes[ticket.route];
        int no = hashNo(ticket.coach, ticket.seat);
        int tour = hashTour(ticket.departure, ticket.arrival);
        int pass = hashPass(tour, no);

        if (route.tours[no].vector[tour] == 0) return false;
        if (route.passengers[pass] == null) return false;
        if (route.passengers[pass].tid != ticket.tid) return false;
        if (!Objects.equals(route.passengers[pass].name, ticket.passenger)) return false;

        for (int t : route.collison[tour]) {
            --route.tours[no].vector[t];
            if (route.tours[no].vector[t] == 0) {
                route.rests[t].incrementAndGet();
                route.empty[t].set(no);
            }
        }

        route.passengers[pass] = null;
        return true;
    }
}
