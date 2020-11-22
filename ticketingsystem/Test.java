package ticketingsystem;

import java.io.*;
import java.util.*;

//* // No debug info
class Debugger {
	public static final boolean EnableSample = true;
	public static final boolean EnableSampleOutput = false;
	public static final boolean EnableStorage = false;
	public static boolean EnableVerification = false;
	public static boolean EnableStatistics = false;
	private final List<String> info;
	public Debugger() {
		info = new ArrayList<>();
	}

	public void add(String rec) {
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
}

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
					long preTime = System.nanoTime() - startTime;
					if (tds.refundTicket(ticket)) {
						if (Debugger.EnableSample) {
							long postTime = System.nanoTime() - startTime;
							String dbg = preTime + " " + postTime + " "
									+ ThreadId.get() + " "
									+ "TicketRefund" + " "
									+ ticket.tid + " "
									+ ticket.passenger + " "
									+ ticket.route + " "
									+ ticket.coach  + " "
									+ ticket.departure + " "
									+ ticket.arrival + " "
									+ ticket.seat;
							if (Debugger.EnableSampleOutput) {
								System.out.println(dbg);
								System.out.flush();
							}

							tds.dbg.add(dbg);
						}
					} else {
						if (Debugger.EnableSample) {
							long postTime = System.nanoTime() - startTime;
							String dbg = preTime + " " + postTime + " " + ThreadId.get() + " " + "ErrOfRefund";
							if (Debugger.EnableSampleOutput) {
								System.out.println(dbg);
								System.out.flush();
							}

							tds.dbg.add(dbg);
						}
					}
				}
			} else if (retpc <= sel && sel < buypc) { // buy ticket
				String passenger = passengerName();
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
				long preTime = System.nanoTime() - startTime;
				if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
					soldTicket.add(ticket);
					if (Debugger.EnableSample) {
						long postTime = System.nanoTime() - startTime;
						String dbg = preTime + " " + postTime + " "
								+ ThreadId.get() + " "
								+ "TicketBought" + " "
								+ ticket.tid + " "
								+ ticket.passenger + " "
								+ ticket.route + " "
								+ ticket.coach + " "
								+ ticket.departure + " "
								+ ticket.arrival + " "
								+ ticket.seat;
						if (Debugger.EnableSampleOutput) {
							System.out.println(dbg);
							System.out.flush();
						}

						tds.dbg.add(dbg);
					}
				} else {
					if (Debugger.EnableSample) {
						long postTime = System.nanoTime() - startTime;
						String dbg = preTime + " " + postTime + " "
								+ ThreadId.get() + " "
								+ "TicketSoldOut" + " "
								+ route + " "
								+ departure+ " "
								+ arrival;
						if (Debugger.EnableSampleOutput) {
							System.out.println(dbg);
							System.out.flush();
						}

						tds.dbg.add(dbg);
					}
				}
			} else if (buypc <= sel && sel < inqpc) { // inquiry ticket
				int route = rand.nextInt(routenum) + 1;
				int departure = rand.nextInt(stationnum - 1) + 1;
				int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
				long preTime = System.nanoTime() - startTime;

				int leftTicket = tds.inquiry(route, departure, arrival);

				if (Debugger.EnableSample) {
					long postTime = System.nanoTime() - startTime;
					String dbg = preTime + " " + postTime + " "
							+ ThreadId.get() + " "
							+ "RemainTicket" + " "
							+ leftTicket + " "
							+ route+ " "
							+ departure+ " "
							+ arrival;
					if (Debugger.EnableSampleOutput) {
						System.out.println(dbg);
						System.out.flush();
					}

					tds.dbg.add(dbg);
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

	static class Verifier {
		private final Debugger dbg;
		public Verifier(Debugger dbg) {
			this.dbg = dbg;
		}
		public void dumpLocal(int c, int r) {
			File dir = new File("dump");
			try {
				dir.mkdir();
				File dumpfile = new File("dump/case" + c + "repeat" + r + ".txt");
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(dumpfile));
					int total = dbg.size();
					for (int i = 0; i < total; ++i) {
						writer.write(dbg.get(i));
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

		public boolean verifyHistory() {
			Map<Integer, Integer> buys = new HashMap<>();
			Map<Integer, Integer> refunds = new HashMap<>();
			Map<Integer, Integer> inquiries = new HashMap<>();
			int total = dbg.size();
			for (int i = 0; i < total; ++i) {
				String info = dbg.get(i);
				Integer threadid = Integer.valueOf(info.split(" ")[0]);
				if (!buys.containsKey(threadid)) {
					buys.put(threadid, 0);
				}
				if (!refunds.containsKey(threadid)) {
					refunds.put(threadid, 0);
				}
				if (!inquiries.containsKey(threadid)) {
					inquiries.put(threadid, 0);
				}

				if (info.contains("buyTicket")) {
					buys.replace(threadid, buys.get(threadid) + 1);
				} else if (info.contains(":Ticket")) {
					if (buys.get(threadid) > 0) {
						buys.replace(threadid, buys.get(threadid) - 1);
					} else {
						return false;
					}
				} else if (info.contains("refundTicket")) {
					refunds.replace(threadid, refunds.get(threadid) + 1);
				} else if (info.contains("boolean")) {
					if (refunds.get(threadid) > 0) {
						refunds.replace(threadid, refunds.get(threadid) - 1);
					} else {
						return false;
					}
				} else if (info.contains("inquiry")) {
					inquiries.replace(threadid, inquiries.get(threadid) + 1);
				} else if (info.contains("int")) {
					if (inquiries.get(threadid) > 0) {
						inquiries.replace(threadid, inquiries.get(threadid) - 1);
					} else {
						return false;
					}
				}
			}
			return true;
		}

		private static class Retarget {
			long tid;
			String passenger;
			int route;
			int coach;
			int seat;

			public Retarget(long tid, String passenger, int route, int coach, int seat) {
				this.tid = tid;
				this.passenger = passenger;
				this.route = route;
				this.coach = coach;
				this.seat = seat;
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;
				Retarget retarget = (Retarget) o;
				return tid == retarget.tid &&
						route == retarget.route &&
						coach == retarget.coach &&
						seat == retarget.seat &&
						Objects.equals(passenger, retarget.passenger);
			}

			@Override
			public int hashCode() {
				return Objects.hash(tid, passenger, route, coach, seat);
			}
		}

		public boolean verifyLinearizability(int c) {
			Map<Retarget, Retarget> mapping = new HashMap<>();
			TicketingDS tds = new TicketingDS(
					routenums.get(c),
					coachnums.get(c),
					seatnums.get(c),
					stationnums.get(c),
					threadnums.get(c)
			);
			int total = dbg.size();
			for (int i = 0; i < total; ++i) {
				String info = dbg.get(i);
				if (info.contains("Bought")) {
					// build ticket
					String[] params = info.split(" ");
					Ticket ticket = new Ticket();
					ticket.tid = Integer.parseInt(params[2]);
					ticket.passenger = params[3];
					ticket.route = Integer.parseInt(params[4]);
					ticket.coach = Integer.parseInt(params[5]);
					ticket.seat = Integer.parseInt(params[6]);
					ticket.departure = Integer.parseInt(params[7]);
					ticket.arrival = Integer.parseInt(params[8]);
					if (tds.buy(ticket) == null) {
						System.out.println("line " + i + "error: " + info);
						return false;
					}
				} else if (info.contains("Refund")) {
					// build ticket
					String[] params = info.split(" ");
					Ticket ticket = new Ticket();
					ticket.tid = Integer.parseInt(params[2]);
					ticket.passenger = params[3];
					ticket.route = Integer.parseInt(params[4]);
					ticket.coach = Integer.parseInt(params[5]);
					ticket.seat = Integer.parseInt(params[6]);
					ticket.departure = Integer.parseInt(params[7]);
					ticket.arrival = Integer.parseInt(params[8]);
					if (!tds.refund(ticket)) {
						System.out.println("line " + i + "error: " + info);
						return false;
					}
				}
			}
			return true;
		}

		public void loadLocal() {
			File file = new File("static/test.txt");
			if (file.isFile() && file.exists()) {
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(new FileReader(file));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				String line = null;
				while (true) {
					try {
						if (!((line = reader.readLine()) != null)) break;
						dbg.add(line);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

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
					} else if (line.startsWith("ENABLE_VERIFICATION=")) {
						String option = line.substring("ENABLE_VERIFICATION=".length());
						if (option.equals("true")) {
							Debugger.EnableVerification = true;
						} else {
							Debugger.EnableVerification = false;
						}
					} else if (line.startsWith("ENABLE_STATISTICS=")) {
						String option = line.substring("ENABLE_STATISTICS=".length());
						if (option.equals("true")) {
							Debugger.EnableStatistics = true;
						} else {
							Debugger.EnableStatistics = false;
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

				if (!Debugger.EnableStatistics && Debugger.EnableVerification) {
					Verifier verifier = new Verifier(tds.dbg);
					if (Debugger.EnableStorage) {
						verifier.dumpLocal(c, r);
					}

					System.out.println("Verify correctness of finite history for"
							+ " testnum=" + testnums.get(c)
							+ " threadnum=" + threadnums.get(c)
							+ " routenum=" + routenums.get(c)
							+ " coachnum=" + coachnums.get(c)
							+ " seatnum=" + seatnums.get(c)
							+ " stationnum=" + stationnums.get(c)
							+ " repeat=" + r + " ...");
					if (verifier.verifyHistory()) {
						System.out.println("Passed");
					} else {
						System.out.println("Failed");
					}

					System.out.println("Verify linearizability of finite history for"
							+ " testnum=" + testnums.get(c)
							+ " threadnum=" + threadnums.get(c)
							+ " routenum=" + routenums.get(c)
							+ " coachnum=" + coachnums.get(c)
							+ " seatnum=" + seatnums.get(c)
							+ " stationnum=" + stationnums.get(c)
							+ " repeat=" + r + " ...");
					if (verifier.verifyLinearizability(c)) {
						System.out.println("Passed");
					} else {
						System.out.println("Failed");
					}
					System.out.println();
				}
			}

			if (Debugger.EnableStatistics || Debugger.EnableSample) {
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
