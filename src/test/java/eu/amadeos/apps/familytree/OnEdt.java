package eu.amadeos.apps.familytree;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/** Runs test code on the event dispatch thread and rethrows its failures in the test thread. */
public final class OnEdt {
    private OnEdt() {
    }

    public interface Code {
        void run() throws Exception;
    }

    public static void run(Code test) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    test.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException r && r.getCause() instanceof Exception inner) throw inner;
            if (cause instanceof Error error) throw error;
            if (cause instanceof Exception exception) throw exception;
            throw e;
        }
    }
}
