package ticketingsystem;

import ticketingsystem.verify.Execution;
import ticketingsystem.verify.Verifier;
import ticketingsystem.verify.VerifyTask;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class MyThreadId {
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

//* // with debug info
class Debugger {
	// These params are configured manually
	// true: need to test the performance
	public static final boolean EnablePerformanceTest = false;

	// true: need to verify correctness of linearizability
	private static final boolean NeedLinearizabilityVerification = true;
	public static final boolean EnableLinearizabilityVerification // configured automatically
			= !EnablePerformanceTest & NeedLinearizabilityVerification;
	// if performance test was enabled, can not verify linearizability
	// due to the cost of recording dbg info for verification

	// true: if you want to store dbg info at local, turn on it
	public static final boolean EnableStorage = true;
	// dbg info would save to /dump/caseNrepeatM.txt
	// where N represents the Nth case described in config.txt
	// M represents the Mth repeating test of case N

	// true: if you want to show the trace on console
	public static final boolean EnableConsolePrint = false;

	// true: if you want to debug or verify something
	public static final boolean NeedDbg
			= NeedLinearizabilityVerification | EnableStorage | EnableConsolePrint;

	private Vector<String> trace;
	public Debugger() {
		trace = new Vector<>();
	}

	static public Verifier verifier = new Verifier();

	public void add(String rec) {
		// add each dbg info exclusively
		synchronized (trace) {
			trace.add(rec);
		}
	}

	public String get(int index) {
		return trace.get(index);
	}

	public Vector<String> get() {
		return trace;
	}

	public int size() {
		return trace.size();
	}

	public void reset() {
		trace = new Vector<>();
	}

	public void dumpLocal(int c, int r) {
		File dir = new File("dump");
		try {
			dir.mkdir();
			File dumpfile = new File("dump/case" + c + "repeat" + r + ".txt");
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(dumpfile));
				for (String s : trace) {
					writer.write(s);
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

	public void dumpLinearized(int c, int r, Vector<Execution> linearized) {
		File dir = new File("dump/linearized/");
		try {
			dir.mkdir();
			File dumpfile = new File("dump/linearized/case" + c + "repeat" + r + ".txt");
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(dumpfile));
				for (Execution execution : linearized) {
					writer.write(execution.toString());
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
		for (String each : trace) {
			System.out.println(each);
		}
	}
}

class TestTask implements Runnable {
	private final static int retpc = 20; // return ticket operation is 10% percent
	private final static int buypc = 50; // buy ticket operation is 20% percent
	private final static int inqpc = 100; //inquiry ticket operation is 70% percent

	private final long startTime;
	private final TicketingDS tds;
	private static int testnum; // 10,000 - 1,000,000
	private final int routenum; // route is designed from 1 to 3
	private final int coachnum; // coach is arranged from 1 to 5
	private final int seatnum ; // seat is allocated from 1 to 20
	private final int stationnum; // station is designed from 1 to 5

	public static Debugger dbg = new Debugger();

	public static void reset() {
		dbg.reset();
	}

	/**
	 * build passenger's name randomly
	 * @return name
	 */
	static String passengerName() {
		Random rand = new Random();
		long uid = rand.nextInt(testnum);
		return "passenger" + uid;
	}

	public TestTask(long start, TicketingDS ins, int test, int route, int coach, int seat, int station) {
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
									MyThreadId.get() + " " +
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
									MyThreadId.get() + " " +
									"ErrOfRefund";
							dbg.add(info);
						}
					}
				} else {
					long preTime = System.nanoTime() - startTime;
					if (Debugger.NeedDbg) {
						String info = preTime + " " + (System.nanoTime() - startTime) + " " +
								MyThreadId.get() + " " +
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
								MyThreadId.get() + " " +
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
								MyThreadId.get() + " " +
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
							MyThreadId.get() + " " +
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

			if (Debugger.EnableLinearizabilityVerification) {
				System.out.println("statistics for"
						+ " testnum=" + testnums.get(c)
						+ " eachnum = " + testnums.get(c) / threadnums.get(c)
						+ " threadnum=" + threadnums.get(c)
						+ " routenum=" + routenums.get(c)
						+ " coachnum=" + coachnums.get(c)
						+ " seatnum=" + seatnums.get(c)
						+ " stationnum=" + stationnums.get(c)
						+ " repeat=" + repeat);
				Debugger.verifier.importConfig(
						routenums.get(c),
						coachnums.get(c),
						seatnums.get(c),
						stationnums.get(c)
				);
			}

			for (int r = 0; r < repeat; ++r) {
				TestTask.dbg.reset();
				MyThreadId.reset(); // set the thread id counting from 0
				if (Debugger.EnableLinearizabilityVerification) {
					Debugger.verifier.reset();
				}

				Thread[] threads = new Thread[threadnums.get(c)];
				TicketingDS tds = new TicketingDS(
						routenums.get(c),
						coachnums.get(c),
						seatnums.get(c),
						stationnums.get(c),
						threadnums.get(c)
				);

				long startTime = System.nanoTime();
				TestTask[] tasks = new TestTask[threadnums.get(c)];
				for (int t = 0; t < threadnums.get(c); ++t) {
					tasks[t] = new TestTask(startTime,
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
					TestTask.dbg.dumpLocal(c, r);
				}

				if (Debugger.EnableConsolePrint) {
					TestTask.dbg.dumpConsole();
				}

				if (Debugger.EnableLinearizabilityVerification) {
					Debugger.verifier.importTrace(TestTask.dbg.get());

					ExecutorService executor = Executors.newCachedThreadPool();
					VerifyTask task = new VerifyTask(Debugger.verifier);

					startTime = System.nanoTime();
					Future<Vector<Execution>> future = executor.submit(task);
					try {
						Vector<Execution> linearized = future.get(60, TimeUnit.SECONDS);
						if (linearized != null) {
							System.out.println("Verification Passed " +
									(System.nanoTime() - startTime) / 1000 + "us");
							if (Debugger.EnableStorage) {
								TestTask.dbg.dumpLinearized(c, r, linearized);
							}
						} else {
							System.out.println("Verification Failed " +
									(System.nanoTime() - startTime) / 1000 + "us");
						}
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						Debugger.verifier.stop();
						System.out.println("Verification Time Limit Exceed 60s");
					} finally {
						executor.shutdownNow();
					}
				}
			}

			if (Debugger.EnablePerformanceTest) {
				System.out.println("statistics for"
						+ " testnum=" + testnums.get(c)
						+ " eachnum = " + testnums.get(c) / threadnums.get(c)
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
