package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.apps.familytree.ui.main.MainView;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Runs a task in the background with a progress bar and a Cancel button; the dialog appears only when the task
 * takes longer than a moment, so a small file does not make the window flicker.
 */
public final class ProgressDialog extends JDialog implements MainView.Progress {
    private final JProgressBar bar = new JProgressBar();
    private final JLabel text = new JLabel(" ");
    private volatile boolean cancelled;
    private volatile boolean finished;

    private ProgressDialog(Window owner, Messages msg, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setName("progressDialog");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 4, 14));
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(380, bar.getPreferredSize().height));
        text.setName("progressText");
        body.add(text, BorderLayout.NORTH);
        body.add(bar, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(DialogParts.buttonRow(DialogParts.button(msg, "button.cancel", "cancel", () -> {
            cancelled = true;
            text.setText(msg.get("progress.cancelling"));
        })), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void update(int done, int total, String what) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                bar.setIndeterminate(false);
                bar.setMaximum(total);
                bar.setValue(done);
            }
            if (what != null && !cancelled) text.setText(what);
        });
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    /** Runs {@code task} on {@code background}; {@code done} or {@code failed} then run on the event thread. */
    public static <T> void run(Window owner, Messages msg, String title, Executor background, MainView.Task<T> task,
                               Consumer<T> done, Consumer<Exception> failed) {
        ProgressDialog d = new ProgressDialog(owner, msg, title);
        background.execute(() -> {
            T result;
            try {
                result = task.run(d);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    d.finish();
                    failed.accept(e);
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                d.finish();
                done.accept(result);
            });
        });
        javax.swing.Timer later = new javax.swing.Timer(400, e -> {
            if (!d.finished) d.setVisible(true);
        });
        later.setRepeats(false);
        later.start();
    }

    private void finish() {
        finished = true;
        dispose();
    }
}
