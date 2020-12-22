package ticketingsystem.verify;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Verifier {
    public AtomicBoolean timeout = new AtomicBoolean(false);
    private Vector<Execution> trace;
    public Verifier() {
        trace = new Vector<>();
    }
    public void stop() {
        timeout.set(true);
    }

    private Set<Execution> take(LinearizableTicketingDS tds, Set<Integer> threads, Execution isolate, Set<Integer> visited) {
        if (timeout.get()) {
            return new HashSet<>();
        }
        Set<Execution> res = new HashSet<>();
        List<Execution> temp = new ArrayList<>();
        if (isolate != null) {
            temp.add(isolate);
        }

        for (Execution each : trace) {
            assert (each != null);
            if (threads.isEmpty()) {
                break;
            } else if (threads.contains(each.threadId)) {
                temp.add(each);
                threads.remove(each.threadId);
            }
        }
        // sort temp by ascending post-time and ascending pre-time
        sortByAscendingPost(temp);
        if (temp.size() > 0) {
            Execution first = temp.get(0);
            for (Execution each : temp) {
                if (Execution.overlapped(first, each)) {
                    if (isolate == each && visited.contains(isolate.threadId))
                        continue;
                    res.addAll(emit(tds, trace.indexOf(each), visited, isolate));
                }
            }
        }

        return res;
    }

    private Set<Execution> emit(LinearizableTicketingDS tds, int position, Set<Integer> visited, Execution isolate) {
        if (timeout.get()) {
            return new HashSet<>();
        }
        // figure out all possible record at the next point
        Execution beginner = trace.get(position);
        visited.add(beginner.threadId);
        // find one possible record can emit before pseudo
        List<Execution> refunds = new ArrayList<>();
        List<Execution> boughts = new ArrayList<>();
        List<Execution> inquiries = new ArrayList<>();
        List<Execution> soldouts = new ArrayList<>();
        // other threads that may emit before which contains pseudo
        Set<Integer> threads = new HashSet<>();
        // the first event un-overlapped with pseudo
        Execution earliest = trace.get(0);
        for (Execution each : trace) {
            // may overlapped with pseudo
            if (each.preTime < beginner.postTime && each.preTime < earliest.postTime) {
                // do not exceed the upper bound
                // thread's sequence has been handled in passed list
                if (visited.contains(each.threadId))
                    continue;
                if (Execution.overlappedAndConflict(each, beginner)) {
                    threads.add(each.threadId);
                    switch (each.opcode) {
                        case BUY: boughts.add(each); break;
                        case REFUND: refunds.add(each); break;
                        case INQUIRY: inquiries.add(each); break;
                        case SOLD: soldouts.add(each); break;
                        default: break;
                    }
                }
            } else {
                break;
            }
        }

        Set<Execution> execs = new HashSet<>();
        switch (beginner.opcode) {
            case BUY: {
                int left = tds.inquiry(beginner.route, beginner.departure, beginner.arrival);
                if (left == 0) {
                    // must refund before
                    Set<Integer> thread = new HashSet<>();
                    refunds.forEach(refund -> thread.add(refund.threadId));
                    inquiries.forEach(inquiry -> thread.add(inquiry.threadId));
                    soldouts.forEach(soldout -> thread.add(soldout.threadId));
                    execs.addAll(take(tds, thread, isolate, visited));
                } else {
                    execs.add(beginner);
                    execs.addAll(take(tds, threads, isolate, visited));
                }
            } break;
            case REFUND: {
                execs.add(beginner);
                execs.addAll(take(tds, threads, isolate, visited));
            } break;
            case INQUIRY: {
                int left = tds.inquiry(beginner.route, beginner.departure, beginner.arrival);
                if ((left > beginner.left && beginner.left + boughts.size() >= left)
                        || (left < beginner.left && left + refunds.size() >= beginner.left)) {
                    // must buy or refund some tickets before
                    execs.addAll(take(tds, threads, isolate, visited));
                } else if (left == beginner.left) {
                    execs.add(beginner);
                }
            } break;
            case SOLD: {
                int left = tds.inquiry(beginner.route, beginner.departure, beginner.arrival);
                if (left > 0 && boughts.size() >= left) {
                    // must buy some tickets before
                    Set<Integer> thread = new HashSet<>();
                    boughts.forEach(bought -> thread.add(bought.threadId));
                    inquiries.forEach(inquiry -> thread.add(inquiry.threadId));
                    soldouts.forEach(soldout -> thread.add(soldout.threadId));
                    execs.addAll(take(tds, thread, isolate, visited));
                } else if (left == 0) {
                    execs.add(beginner);
                }
            } break;
            default: break;
        }
        return execs;
    }

    private boolean simulate(LinearizableTicketingDS tds, Vector<Execution> linearized, Execution execution) {
        int index = trace.indexOf(execution);
        trace.remove(index);
        linearized.add(execution);

        if (march(tds, linearized)) {
            return true;
        }

        trace.add(index, execution);
        linearized.remove(execution);
        return false;
    }

    private boolean march(LinearizableTicketingDS tds, Vector<Execution> linearized) {
        if (timeout.get()) {
            return false;
        }

        if (trace.isEmpty()) {
            return true;
        } else {
            // calculate the isolate execution
            Execution earliest = trace.get(0);
            Execution isolate = null;
            long minPost = Long.MAX_VALUE;
            for (Execution each : trace) {
                if (each.preTime < earliest.postTime) {
                    if (!Execution.overlappedAndConflict(each, earliest)
                            && each.postTime < minPost) {
                        isolate = each;
                        minPost = each.postTime;
                    }
                } else {
                    break;
                }
            }
            // find the next possible executions
            Set<Execution> next = emit(tds, 0, new HashSet<>(), isolate);
            List<Execution> sorted = new ArrayList<>(next);
            // sort by ascending post-time to speed up finding the legal path
            sortByAscendingPost(sorted);
            for (Execution step : sorted) {
                switch (step.opcode) {
                    case BUY: {
                        if (tds.buy(step.toTicket()) != null) {
                            if (simulate(tds, linearized, step)) {
                                return true;
                            } else {
                                tds.refund(step.toTicket());
                            }
                        }
                    } break;
                    case REFUND: {
                        if (tds.refund(step.toTicket())) {
                            if (simulate(tds, linearized, step)) {
                                return true;
                            } else {
                                tds.buy(step.toTicket());
                            }
                        }
                    } break;
                    case INQUIRY: {
                        if (tds.inquiry(step.route, step.departure, step.arrival) == step.left
                                && simulate(tds, linearized, step)) {
                            return true;
                        }
                    } break;
                    case SOLD: {
                        if (tds.inquiry(step.route, step.departure, step.arrival) == 0
                                && simulate(tds, linearized, step)) {
                            return true;
                        }
                    } break;
                    default: break;
                }
                if (timeout.get()) {
                    return false;
                }
            }
        }
        return false;
    }

    private void sortByAscendingPost(List<Execution> sorted) {
        sorted.sort((e1, e2) -> {
            if (e1.postTime < e2.postTime
                    || (e1.postTime == e2.postTime && e1.preTime < e2.preTime)) {
                return -1;
            } else if (e1.preTime == e2.preTime && e1.postTime == e2.postTime) {
                return 0;
            } else {
                return 1;
            }
        });
    }

    public Vector<Execution> verify() {
        LinearizableTicketingDS tds = new LinearizableTicketingDS(
                routenum,
                coachnum,
                seatnum,
                stationnum
        );
        // sort Trace by ascending pre-time and ascending post-time
        trace.sort((t1, t2)-> {
            if (t1.preTime < t2.preTime
            || (t1.preTime == t2.preTime && t1.postTime < t2.postTime)) {
                return -1;
            } else if (t1.preTime == t2.preTime && t1.postTime == t2.postTime) {
                return 0;
            } else {
                return 1;
            }
        });
        Vector<Execution> linearized = new Vector<>();
        if (march(tds, linearized)) {
            return linearized;
        }
        return null;
    }

    final static String config = "config.txt";
    public static void main(String[] args) {
        if (args.length == 1) {
            Verifier verifier = new Verifier();
            if (!verifier.importConfig()) return;
            if (!verifier.importTrace(args[0])) return;

            ExecutorService executor = Executors.newCachedThreadPool();
            VerifyTask task = new VerifyTask(verifier);

            long start = System.nanoTime();
            Future<Vector<Execution>> future = executor.submit(task);
            try {
                Vector<Execution> linearized = future.get(60, TimeUnit.SECONDS);
                if (linearized != null) {
                    System.out.println("Verification Passed " +
                            (System.nanoTime() - start) / 1000000 + "ms");
                    System.out.println("Linearized Trace as below");
                    linearized.forEach(System.out::println);
                } else {
                    System.out.println("Verification Failed " +
                            (System.nanoTime() - start) / 1000000 + "ms");
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                verifier.stop();
                System.out.println("Verification Time Limit Exceed 60s");
            } finally {
                executor.shutdownNow();
            }
        }
    }
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;
    /**
     * import params in test file "config.txt"
     * @return be true when imported correctly
     */
    public boolean importConfig() {
        File file = new File(config);
        if (file.isFile() && file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("routenum=")) {
                        String[] params = line.split(" ");
                        if (params != null) {
                            for (String p : params) {
                                if (p.startsWith("routenum=")) {
                                    routenum = Integer.parseInt(p.substring("routenum=".length()));
                                } else if (p.startsWith("coachnum=")) {
                                    coachnum = Integer.parseInt(p.substring("coachnum=".length()));
                                } else if (p.startsWith("seatnum=")) {
                                    seatnum = Integer.parseInt(p.substring("seatnum=".length()));
                                } else if (p.startsWith("stationnum=")) {
                                    stationnum = Integer.parseInt(p.substring("stationnum=".length()));
                                }
                            }
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
            System.out.println(config + " not found");
            return false;
        }
    }

    public void importConfig(int routenum, int coachnum, int seatnum, int stationnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
    }

    public boolean importTrace(String filename) {
        File file = new File(filename);
        if (file.isFile() && file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = null;
                Execution execution = null;
                while ((line = reader.readLine()) != null) {
                    execution = Execution.build(line);
                    if (execution == null) {
                        System.out.println("Invalid " + line);
                        return false;
                    } else {
                        trace.add(execution);
                    }
                }
                reader.close();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public void importTrace(Vector<String> info) {
        info.forEach(i -> trace.add(Execution.build(i)));
    }

    public void reset() {
        timeout.set(false);
        trace = new Vector<>();
    }
}
