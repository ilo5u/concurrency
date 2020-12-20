package ticketingsystem.verify;

import java.util.Vector;
import java.util.concurrent.Callable;

public class VerifyTask implements Callable<Vector<Execution>> {
    Verifier verifier;
    public VerifyTask(Verifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Vector<Execution> call() {
        return verifier.verify();
    }
}