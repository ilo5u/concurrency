package ticketingsystem;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

//*
class ThreadId {
	// Atomic integer containing the next thread ID to be assigned
	private static AtomicInteger nextId = new AtomicInteger(0);

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

	// Reset the thread counter as 0
	public static void reset() { nextId = new AtomicInteger(0); }
}
//*/

class Record {
	public final long preTime;
	public final long postTime;
	public enum Opcode { NULL, BUY, REFUND, INQUIRY, SOLD, ERROR };
	public final Opcode opcode;
	public final int threadId;
	public final long ticketId;
	public final String name;
	public final int route;
	public final int coach;
	public final int seat;
	public final int departure;
	public final int arrival;
	public final int left;

	static public Opcode parse(String trace) {
		if (trace.contains("TicketBought")) {
			return Opcode.BUY;
		} else if (trace.contains("TicketRefund")) {
			return Opcode.REFUND;
		} else if (trace.contains("TicketSoldOut")) {
			return Opcode.SOLD;
		} else if (trace.contains("ErrorOfRefund")) {
			return Opcode.ERROR;
		} else if (trace.contains("RemainTicket")) {
			return Opcode.INQUIRY;
		} else {
			return Opcode.NULL;
		}
	}

	public Record(long preTime, long postTime, Opcode opcode, int threadId) {
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

	public Record(long preTime, long postTime, Opcode opcode, int threadId, long ticketId, String name, int route, int coach, int seat, int departure, int arrival) {
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

	public Record(long preTime, long postTime, Opcode opcode, int threadId, int route, int departure, int arrival) {
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

	public Record(long preTime, long postTime, Opcode opcode, int threadId, int route, int departure, int arrival, int left) {
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

	static public Record build(String trace) {
		String[] sub = trace.split(" ");
		Opcode op = parse(trace);
		switch (op) {
			case BUY:
			case REFUND: return new Record(
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
			case INQUIRY: return new Record(
					Long.parseLong(sub[0]),
					Long.parseLong(sub[1]),
					op,
					Integer.parseInt(sub[2]),
					Integer.parseInt(sub[5]),
					Integer.parseInt(sub[6]),
					Integer.parseInt(sub[7]),
					Integer.parseInt(sub[4]));
			case SOLD: return new Record(
					Long.parseLong(sub[0]),
					Long.parseLong(sub[1]),
					op,
					Integer.parseInt(sub[2]),
					Integer.parseInt(sub[4]),
					Integer.parseInt(sub[5]),
					Integer.parseInt(sub[6]));
			case ERROR: return new Record(
					Long.parseLong(sub[0]),
					Long.parseLong(sub[1]),
					op,
					Integer.parseInt(sub[2]));
			default: return null;
		}
	}

	static public boolean overlapped(Record r1, Record r2) {
		if ((r2.preTime - r1.postTime) * (r2.postTime - r1.preTime) < 0) {
			if (r1.opcode != Opcode.ERROR && r2.opcode != Opcode.ERROR) {
				return r1.route == r2.route
						&& ((r2.departure - r1.arrival) * (r2.arrival - r1.departure)) < 0;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	static public boolean interleave(Record r1, Record r2) {
		if (r1.opcode != Opcode.ERROR && r2.opcode != Opcode.ERROR) {
			return r1.route == r2.route
					&& ((r2.departure - r1.arrival) * (r2.arrival - r1.departure)) < 0;
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
}

//* // with debug info
class Debugger {
	// These params are configured manually
	// true: need to test the performance
	public static final boolean EnablePerformanceTest = true;

	// true: need to verify correctness of linearizability
	private static final boolean NeedLinearizabilityVerification = false;
	public static final boolean EnableLinearizabilityVerification // configured automatically
			= !EnablePerformanceTest & NeedLinearizabilityVerification;
	// if performance test was enabled, can not verify linearizability
	// due to the cost of recording dbg info for verification

	// true: if you want to store dbg info at local, turn on it
	public static final boolean EnableStorage = false;
	// dbg info would save to /dump/caseNrepeatM.txt
	// where N represents the Nth case described in test.txt
	// M represents the Mth repeating test of case N

	// true: if you want to show the trace on console
	public static final boolean EnableConsolePrint = false;

	// true: if you want to debug or verify something
	public static final boolean NeedDbg
			= NeedLinearizabilityVerification | EnableStorage | EnableConsolePrint;

	public static final boolean IsDebugMode = true;

	private final List<String> info;
	public Debugger() {
		info = new ArrayList<>();
	}

	public void add(String rec) {
		// add each dbg info exclusively
		synchronized (info) {
			info.add(rec);
		}
	}

	public String get(int index) {
		return info.get(index);
	}

	public int size() {
		return info.size();
	}

	public void dumpLocal(int c, int r) {
		File dir = new File("dump");
		try {
			dir.mkdir();
			File dumpfile = new File("dump/case" + c + "repeat" + r + ".txt");
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(dumpfile));
				int total = info.size();
				for (int i = 0; i < total; ++i) {
					writer.write(info.get(i));
					writer.newLine();
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	public void dumpConsole() {
		for (String each : info) {
			System.out.println(each);
		}
	}

	private static Set<Record> take(TicketingDS tds, List<Record> trace, Set<Integer> threads, Record latest, List<Record> passed) {
		Set<Record> res = new HashSet<>();
		if (latest != null && !threads.contains(latest.threadId)) {
			passed.add(latest);
			res.addAll(emit(tds, trace, trace.indexOf(latest), passed));
			passed.remove(latest);
		}

		int pos = 0;
		for (Record each : trace) {
			assert (each != null);
			if (threads.isEmpty()) {
				break;
			} else if (threads.contains(each.threadId)) {
				if (latest != null) {
					// can not emit before latest
					if (each.preTime < latest.postTime) {
						passed.add(each);
						res.addAll(emit(tds, trace, pos, passed));
						passed.remove(each);
					} else {
						break;
					}
				} else {
					passed.add(each);
					res.addAll(emit(tds, trace, pos, passed));
					passed.remove(each);
				}
				threads.remove(each.threadId);
			}
			pos++;
		}
		return res;
	}

	private static Set<Record> emit(TicketingDS tds, List<Record> trace, int start, List<Record> passed) {
		// figure out all possible record at the next point
		Record pseudo = trace.get(start);
		if (pseudo != null) {
			passed.add(pseudo);
			// find one possible record can emit before pseudo
			List<Record> refunds = new ArrayList<>();
			List<Record> boughts = new ArrayList<>();
			// other threads that may emit before which contains pseudo
			Set<Integer> threads = new HashSet<>();
			// the first event un-overlapped with pseudo
			Record latest = null;
			long minPost = Long.MAX_VALUE;
			for (Record each : trace) {
				// may overlapped with pseudo
				if (each.preTime < pseudo.postTime && each.preTime < passed.get(0).postTime) {
					// do not exceed the upper bound
					// thread's sequence has been handled in passed list
					if (passed.stream().anyMatch(record -> record.threadId == each.threadId)) continue;

					if (Record.overlapped(each, pseudo)) {
						threads.add(each.threadId);
						switch (each.opcode) {
							case BUY: boughts.add(each); break;
							case REFUND: refunds.add(each); break;
							default: break;
						}
					} else if (each.postTime < minPost) {
						minPost = each.postTime;
						latest = each;
					}
				} else {
					break;
				}
			}

			Set<Record> pres = new HashSet<>();
			switch (pseudo.opcode) {
				case BUY: {
					int left = tds.inquiry(pseudo.route, pseudo.departure, pseudo.arrival);
					if (left == 0) {
						// must refund before
						Set<Integer> thread = new HashSet<>();
						refunds.forEach(refund -> thread.add(refund.threadId));
						pres.addAll(take(tds, trace, thread, latest, passed));
					} else {
						pres.add(pseudo);
						pres.addAll(take(tds, trace, threads, latest, passed));
					}
				} break;
				case REFUND: {
					pres.add(pseudo);
					pres.addAll(take(tds, trace, threads, latest, passed));
				} break;
				case INQUIRY: {
					int left = tds.inquiry(pseudo.route, pseudo.departure, pseudo.arrival);
					if ((left > pseudo.left && pseudo.left + boughts.size() >= left)
							|| (left < pseudo.left && left + refunds.size() >= pseudo.left)) {
						// must buy or refund some tickets before
						pres.addAll(take(tds, trace, threads, latest, passed));
					} else if (left == pseudo.left) {
						pres.add(pseudo);
					}
				} break;
				case SOLD: {
					int left = tds.inquiry(pseudo.route, pseudo.departure, pseudo.arrival);
					if (left > 0 && boughts.size() >= left) {
						// must buy some tickets before
						Set<Integer> thread = new HashSet<>();
						boughts.forEach(bought -> thread.add(bought.threadId));
						pres.addAll(take(tds, trace, thread, latest, passed));
					} else if (left == 0) {
						pres.add(pseudo);
					}
				} break;
				default: break;
			}
			return pres;
		}
		return null;
	}

	private static boolean march(TicketingDS tds, List<Record> trace, Vector<Record> linearized, Record step) {
		int index = trace.indexOf(step);
		trace.remove(index);
		linearized.add(step);

		if (verify(tds, trace, linearized)) {
			return true;
		}

		trace.add(index, step);
		linearized.remove(step);
		return false;
	}

	private static boolean verify(TicketingDS tds, List<Record> trace, Vector<Record> linearized) {
		if (trace.isEmpty()) {
			return true;
		} else {
			Set<Record> next = emit(tds, trace, 0, new ArrayList<>());
			assert next != null;
			for (Record step : next) {
				switch (step.opcode) {
					case BUY: {
						if (tds.buy(step.toTicket()) != null) {
							if (march(tds, trace, linearized, step)) {
								return true;
							} else {
								tds.refund(step.toTicket());
							}
						}
					} break;
					case REFUND: {
						if (tds.refund(step.toTicket())) {
							if (march(tds, trace, linearized, step)) {
								return true;
							} else {
								tds.buy(step.toTicket());
							}
						}
					} break;
					case INQUIRY: {
						if (tds.inquiry(step.route, step.departure, step.arrival) == step.left
								&& march(tds, trace, linearized, step)) {
							return true;
						}
					} break;
					case SOLD: {
						if (tds.inquiry(step.route, step.departure, step.arrival) == 0
								&& march(tds, trace, linearized, step)) {
							return true;
						}
					} break;
					default: break;
				}
			}
		}
		return false;
	}

	public boolean verifyLinearizability(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
		TicketingDS tds = new TicketingDS(
				routenum,
				coachnum,
				seatnum,
				stationnum,
				threadnum
		);

		info.sort((o1, o2) -> {
			String[] sub1 = o1.split(" ");
			String[] sub2 = o2.split(" ");
			long pre1 = Long.parseLong(sub1[0]);
			long post1 = Long.parseLong(sub1[1]);
			long pre2 = Long.parseLong(sub2[0]);
			long post2 = Long.parseLong(sub2[1]);
			if (pre1 < pre2 || (pre1 == pre2 && post1 < post2)) {
				return -1;
			} else if (pre1 == pre2 || post1 == post2) {
				return 0;
			} else {
				return 1;
			}
		});

		List<Record> trace = new ArrayList<>();
		for (String s : info) {
			trace.add(Record.build(s));
		}
		Vector<Record> linearized = new Vector<>();
		return verify(tds, trace, linearized);
	}
}

class Task implements Runnable {
	private final static int retpc = 10; // return ticket operation is 10% percent
	private final static int buypc = 30; // buy ticket operation is 20% percent
	private final static int inqpc = 100; //inquiry ticket operation is 70% percent

	private final long startTime;
	private final TicketingDS tds;
	private static int testnum; // 10,000 - 1,000,000
	private final int routenum; // route is designed from 1 to 3
	private final int coachnum; // coach is arranged from 1 to 5
	private final int seatnum ; // seat is allocated from 1 to 20
	private final int stationnum; // station is designed from 1 to 5

	public static Debugger dbg = new Debugger();

	/**
	 * build passenger's name randomly
	 * @return name
	 */
	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}

	public Task(long start, TicketingDS ins, int test, int route, int coach, int seat, int station) {
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
					long preTime = System.nanoTime() - startTime;
					if (tds.refundTicket(ticket)) {
						if (Debugger.NeedDbg) {
							long postTime = System.nanoTime() - startTime;
							String info = preTime + " " + postTime + " " +
									ThreadId.get() + " " +
									"TicketRefund" + " " +
									ticket.tid + " " +
									ticket.passenger + " " +
									ticket.route + " " +
									ticket.coach  + " " +
									ticket.departure + " " +
									ticket.arrival + " " +
									ticket.seat;
							dbg.add(info);
						}
					} else {
						if (Debugger.NeedDbg) {
							String info = preTime + " " + (System.nanoTime() - startTime) + " " +
									ThreadId.get() + " " +
									"ErrOfRefund";
							dbg.add(info);
						}
					}
				} else {
					long preTime = System.nanoTime() - startTime;
					if (Debugger.NeedDbg) {
						String info = preTime + " " + (System.nanoTime() - startTime) + " " +
								ThreadId.get() + " " +
								"ErrOfRefund";
						dbg.add(info);
					}
				}
			} else if (retpc <= sel && sel < buypc) { // buy ticket
				String passenger = passengerName();
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure

				long preTime = System.nanoTime() - startTime;
				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
					if (Debugger.NeedDbg) {
						long postTime = System.nanoTime() - startTime;
						String info = preTime + " " + postTime + " " +
								ThreadId.get() + " " +
								"TicketBought" + " " +
								ticket.tid + " " +
								ticket.passenger + " " +
								ticket.route + " " +
								ticket.coach + " " +
								ticket.departure + " " +
								ticket.arrival + " " +
								ticket.seat;
						dbg.add(info);
					}
					soldTicket.add(ticket);
				} else {
					if (Debugger.NeedDbg) {
						String info = preTime + " " + (System.nanoTime() - startTime) + " " +
								ThreadId.get() + " " +
								"TicketSoldOut" + " " +
								route + " " +
								departure+ " " +
								arrival;
						dbg.add(info);
					}
				}
			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure

				long preTime = System.nanoTime() - startTime;
				int leftTicket = tds.inquiry(route, departure, arrival);
				if (Debugger.NeedDbg) {
					long postTime = System.nanoTime() - startTime;
					String info = preTime + " " + postTime + " " +
							ThreadId.get() + " " +
							"RemainTicket" + " " +
							leftTicket + " " +
							route+ " " +
							departure+ " " +
							arrival;
					dbg.add(info);
				}
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
				ThreadId.reset(); // set the thread id counting from 0

				Thread[] threads = new Thread[threadnums.get(c)];
				TicketingDS tds = new TicketingDS(
						routenums.get(c),
						coachnums.get(c),
						seatnums.get(c),
						stationnums.get(c),
						threadnums.get(c)
				);

				long startTime = System.nanoTime();
				Task[] tasks = new Task[threadnums.get(c)];
				for (int t = 0; t < threadnums.get(c); ++t) {
					tasks[t] = new Task(startTime,
							tds,
							testnums.get(c) / threadnums.get(c),
							routenums.get(c),
							coachnums.get(c),
							seatnums.get(c),
							stationnums.get(c)
					);
					threads[t] = new Thread(tasks[t]);
				}

				for (int t = 0; t < threadnums.get(c); ++t) {
					threads[t].start();
				}
				for (int t = 0; t < threadnums.get(c); ++t) {
					threads[t].join();
				}
				// record past time for one repeat
				costs.get(c).add(System.nanoTime() - startTime);

				if (Debugger.EnableStorage) {
					Task.dbg.dumpLocal(c, r);
				}

				if (Debugger.EnableConsolePrint) {
					Task.dbg.dumpConsole();
				}

				if (Debugger.EnableLinearizabilityVerification) {
					if (Task.dbg.verifyLinearizability(
							routenums.get(c),
							coachnums.get(c),
							seatnums.get(c),
							stationnums.get(c),
							threadnums.get(c)
					)) {
						System.out.println("Verification Passed");
					} else {
						System.out.println("Verification Failed");
					}
				}
			}

			if (Debugger.EnablePerformanceTest) {
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
}
//*/
