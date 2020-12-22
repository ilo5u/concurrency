package ticketingsystem.verify;

import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class VerifyTask implements Callable<Vector<Execution>> {
    private final Verifier verifier;
    public final AtomicBoolean finished;
    public VerifyTask(Verifier verifier) {
        this.verifier = verifier;
        this.finished = new AtomicBoolean(false);
    }

    @Override
    public Vector<Execution> call() {
        finished.set(false);
        Vector<Execution> linearized = verifier.verify();
        finished.set(true);
        return linearized;
    }
}