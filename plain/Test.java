package plain;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

//*
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
}
//*/

//* // No debug info

class Runner implements Runnable {
	private final static int retpc = 30; // return ticket operation is 10% percent
	private final static int buypc = 60; // buy ticket operation is 30% percent
	private final static int inqpc = 100; //inquiry ticket operation is 60% percent

	private final long startTime;
	private final TicketingDS tds;
	private static int testnum; // 10,000 - 1,000,000
	private final int routenum; // route is designed from 1 to 3
	private final int coachnum; // coach is arranged from 1 to 5
	private final int seatnum ; // seat is allocated from 1 to 20
	private final int stationnum; // station is designed from 1 to 5

	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}

	public Runner(long start, TicketingDS ins, int test, int route, int coach, int seat, int station) {
		startTime = start;
		tds = ins;
		testnum = test;
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
					tds.refundTicket(ticket);
				}
			} else if (retpc <= sel && sel < buypc) { // buy ticket
				String passenger = passengerName();
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
				long preTime = System.nanoTime() - startTime;
				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
					soldTicket.add(ticket);
				}
			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
				tds.inquiry(route, departure, arrival);
			}
		}
	}
}

public class Test {
	final static String testfile = "test.txt";
	static Integer repeat = null;
	static Vector<Integer> testnums = null;
	static Vector<Integer> threadnums = null;
	static Vector<Integer> routenums = null;
	static Vector<Integer> coachnums = null;
	static Vector<Integer> seatnums = null;
	static Vector<Integer> stationnums = null;

	static Vector<List<Long>> costs = null;

	/**
	 * import params in test file "test.txt"
	 * @return be true when imported correctly
	 */
	private static boolean importTestFile() {
		testnums = new Vector<>();
		threadnums = new Vector<>();
		routenums = new Vector<>();
		coachnums = new Vector<>();
		seatnums = new Vector<>();
		stationnums = new Vector<>();

		File file = new File(testfile);
		if (file.isFile() && file.exists()) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line = null;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("repeat=")) {
						String sub = line.substring("repeat=".length());
						repeat = Integer.valueOf(line.substring("repeat=".length()));
					} else if (line.startsWith("testnum=")) {
						String[] params = line.split(" ");
						if (params != null) {
							Integer testnum = null;
							Integer threadnum = null;
							Integer routenum = null;
							Integer coachnum = null;
							Integer seatnum = null;
							Integer stationnum = null;
							for (String p : params) {
								if (p.startsWith("testnum=")) {
									testnum = Integer.valueOf(p.substring("testnum=".length()));
								} else if (p.startsWith("threadnum=")) {
									threadnum = Integer.valueOf(p.substring("threadnum=".length()));
								} else if (p.startsWith("routenum=")) {
									routenum = Integer.valueOf(p.substring("routenum=".length()));
								} else if (p.startsWith("coachnum=")) {
									coachnum = Integer.valueOf(p.substring("coachnum=".length()));
								} else if (p.startsWith("seatnum=")) {
									seatnum = Integer.valueOf(p.substring("seatnum=".length()));
								} else if (p.startsWith("stationnum=")) {
									stationnum = Integer.valueOf(p.substring("stationnum=".length()));
								}
							}
							if (testnum == null || threadnum == null || routenum == null || coachnum == null
									|| seatnum == null || stationnum == null) {
								continue;
							}
							testnums.add(testnum);
							threadnums.add(threadnum);
							routenums.add(routenum);
							coachnums.add(coachnum);
							seatnums.add(seatnum);
							stationnums.add(stationnum);
						}
					}
				}
				reader.close();
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		} else {
			System.out.println(testfile + " not found");
			return false;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		if (!importTestFile()) return;

		costs = new Vector<>();
		int casenum = testnums.size();
		for (int c = 0; c < casenum; ++c) {
			costs.add(new ArrayList<>());
			for (int r = 0; r < repeat; ++r) {
				Thread[] threads = new Thread[threadnums.get(c)];
				TicketingDS tds = new TicketingDS(
						routenums.get(c),
						coachnums.get(c),
						seatnums.get(c),
						stationnums.get(c),
						threadnums.get(c)
				);

				long startTime = System.nanoTime();
				Runner[] runners = new Runner[threadnums.get(c)];
				for (int t = 0; t < threadnums.get(c); ++t) {
					runners[t] = new Runner(startTime,
							tds,
							testnums.get(c),
							routenums.get(c),
							coachnums.get(c),
							seatnums.get(c),
							stationnums.get(c)
					);
					threads[t] = new Thread(runners[t]);
				}

				for (int t = 0; t < threadnums.get(c); ++t) {
					threads[t].start();
				}
				for (int t = 0; t < threadnums.get(c); ++t) {
					threads[t].join();
				}
				costs.get(c).add(System.nanoTime() - startTime);
			}

			System.out.println("statistics for"
					+ " testnum=" + testnums.get(c)
					+ " threadnum=" + threadnums.get(c)
					+ " routenum=" + routenums.get(c)
					+ " coachnum=" + coachnums.get(c)
					+ " seatnum=" + seatnums.get(c)
					+ " stationnum=" + stationnums.get(c)
					+ " repeat=" + repeat);
			double cost = 1.0;
			for (int r = 0; r < repeat; ++r) {
				System.out.print("repeat" + r + ": ");
				Long each = costs.get(c).get(r);
				System.out.println(each / 1000000 + "ms");
				cost *= Math.pow(costs.get(c).get(r), 1.0 / (double)repeat);
			}
			System.out.println("average: " + new Double(cost / 1000000.0).longValue() + "ms");
			System.out.println();
			System.out.flush();
		}
	}
}
//*/
