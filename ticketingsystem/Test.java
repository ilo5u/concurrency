package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/*
public class Test {
	final static int threadnum = 4;
	final static int routenum = 3; // route is designed from 1 to 3
	final static int coachnum = 5; // coach is arranged from 1 to 5
	final static int seatnum = 10; // seat is allocated from 1 to 20
	final static int stationnum = 8; // station is designed from 1 to 5

	public static void main(String[] args) throws InterruptedException {
		final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
		Ticket ticket;
		try {
			for (int r = 1; r <= routenum; ++r) {
				for (int s = 1; s <= coachnum * seatnum; ++s) {
					String name = "passenger" + r + ":" + s + "[" + 1 + "," + stationnum / 2 + "]";
					ticket = tds.buyTicket(name, r , 1, stationnum / 2);
					System.out.println(name + "get coach: " + ticket.coach + " seat: " + ticket.seat);
				tds.refundTicket(ticket);
				}
				for (int s = 1; s <= coachnum * seatnum; ++s) {
					String name = "passenger" + r + ":" + s + "[" + stationnum / 2 + "," + stationnum + "]";
					ticket = tds.buyTicket(name, r , stationnum / 2, stationnum);
					System.out.println(name + "get coach: " + ticket.coach + " seat: " + ticket.seat);
				tds.refundTicket(ticket);
				}
			}
			for (int r = 1; r <= routenum; ++r) {
				int rest = tds.inquiry(r, 1, stationnum);
				System.out.println(r + ":[" + 1 + "," + stationnum + "] " + rest);
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}
}
//*/
/*
package ticketingsystem;

		import java.util.*;

		import java.util.concurrent.atomic.AtomicInteger;

class ThreadId {
	// Atomic integer containing the next thread ID to be assigned
	private static final AtomicInteger nextId = new AtomicInteger(0);

	// Thread local variable containing each thread's ID
	private static final ThreadLocal<Integer> threadId =
			new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() {
					return nextId.getAndIncrement();
				}
			};

	// Returns the current thread's unique ID, assigning it if necessary
	public static int get() {
		return threadId.get();
	}
}*/
//*
class Runner implements Runnable {
	private final TicketingDS tds;

	private final static int testnum = 10000;
	private final static int retpc = 30; // return ticket operation is 10% percent
	private final static int buypc = 60; // buy ticket operation is 30% percent
	private final static int inqpc = 100; //inquiry ticket operation is 60% percent

	final int routenum; // route is designed from 1 to 3
	final int coachnum; // coach is arranged from 1 to 5
	final int seatnum ; // seat is allocated from 1 to 20
	final int stationnum; // station is designed from 1 to 5

	public long rec = 0;
	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}

	public Runner(TicketingDS ins, int route, int coach, int seat, int station) {
		tds = ins;
		routenum = route;
		coachnum = coach;
		seatnum = seat;
		stationnum = station;
	}

	public void run() {
		Random rand = new Random();
		Ticket ticket = new Ticket();
		ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();

		for (int i = 0; i < testnum; i++) {
			int sel = rand.nextInt(inqpc);
			if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
				int select = rand.nextInt(soldTicket.size());
				if ((ticket = soldTicket.remove(select)) != null) {
//								long preTime = System.nanoTime() - startTime;
					long pre = System.nanoTime();
					if (tds.refundTicket(ticket)) {
//									long postTime = System.nanoTime() - startTime;
						rec += System.nanoTime() - pre;
//									System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketRefund" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach  + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
//									System.out.flush();
					} else {
						rec += System.nanoTime() - pre;
//									System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
//									System.out.flush();
					}
				} else {
//								long preTime = System.nanoTime() - startTime;
//								System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "ErrOfRefund");
//								System.out.flush();
				}
			} else if (retpc <= sel && sel < buypc) { // buy ticket
				String passenger = passengerName();
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//							long preTime = System.nanoTime() - startTime;
				long pre = System.nanoTime();
				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
					rec += System.nanoTime() - pre;
//								long postTime = System.nanoTime() - startTime;
//								System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "TicketBought" + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat);
					soldTicket.add(ticket);
//								System.out.flush();
				} else {
					rec += System.nanoTime() - pre;
//								System.out.println(preTime + " " + String.valueOf(System.nanoTime()-startTime) + " " + ThreadId.get() + " " + "TicketSoldOut" + " " + route + " " + departure+ " " + arrival);
//								System.out.flush();
				}
			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket

				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//							long preTime = System.nanoTime() - startTime;
				long pre = System.nanoTime();
				int leftTicket = tds.inquiry(route, departure, arrival);
				rec += System.nanoTime() - pre;
//							long postTime = System.nanoTime() - startTime;
//							System.out.println(preTime + " " + postTime + " " + ThreadId.get() + " " + "RemainTicket" + " " + leftTicket + " " + route+ " " + departure+ " " + arrival);
//							System.out.flush();
			}
		}

	}
}

public class Test {
	final static int threadnum = 16;
	final static int routenum = 5; // route is designed from 1 to 3
	final static int coachnum = 8; // coach is arranged from 1 to 5
	final static int seatnum = 100; // seat is allocated from 1 to 20
	final static int stationnum = 10; // station is designed from 1 to 5

	public static void main(String[] args) throws InterruptedException {


		Thread[] threads = new Thread[threadnum];

		final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);

		final long startTime = System.nanoTime();

		Runner[] runners = new Runner[threadnum];
		for (int i = 0; i< threadnum; i++) {
			runners[i] = new Runner(tds, routenum, coachnum, seatnum, stationnum);
			threads[i] = new Thread(runners[i]);
			threads[i].start();
		}

		long total = 0;
		for (int i = 0; i< threadnum; i++) {
			threads[i].join();
			total += runners[i].rec;
		}
		System.out.println(total / 1000000 + "ms");
	}
}
//*/