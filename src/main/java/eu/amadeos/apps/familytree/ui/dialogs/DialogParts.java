package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;

/** What the dialogs share: buttons with mnemonics, form rows, Escape to close. */
final class DialogParts {
    private DialogParts() {
    }

    static JButton button(Messages msg, String key, String name, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), msg.get(key));
        b.setName(name);
        b.addActionListener(e -> action.run());
        return b;
    }

    static JPanel buttonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        for (JButton b : buttons) row.add(b);
        return row;
    }

    static void escapeCloses(JDialog dialog) {
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JRootPane.WHEN_IN_FOCUSED_WINDOW);
    }

    static GridBagConstraints at(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(3, 6, 3, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = x == 0 ? 0 : 1;
        c.anchor = GridBagConstraints.LINE_START;
        return c;
    }

    /** A label (with mnemonic) and its field in one row of a {@link GridBagLayout}. */
    static void row(JPanel form, int y, Messages msg, String key, JComponent field) {
        JLabel label = Mnemonics.apply(new JLabel(), msg.get(key));
        label.setLabelFor(field);
        form.add(label, at(0, y));
        form.add(field, at(1, y));
    }

    static JPanel form() {
        return new JPanel(new GridBagLayout());
    }
}
