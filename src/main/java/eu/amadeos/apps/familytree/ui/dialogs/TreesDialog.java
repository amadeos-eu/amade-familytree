package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.ui.main.MainView;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

/** The family trees on this computer: open one, start one, import one, rename or delete one. */
public final class TreesDialog extends JDialog {
    private final DefaultListModel<StoredTree> model = new DefaultListModel<>();
    private final JList<StoredTree> list = new JList<>(model);
    private StoredTree chosen;

    private TreesDialog(Window owner, Messages msg, List<StoredTree> trees, MainView.TreeActions actions) {
        super(owner, msg.get("trees.title"), ModalityType.APPLICATION_MODAL);
        setName("treesDialog");
        trees.forEach(model::addElement);
        list.setName("trees");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!model.isEmpty()) list.setSelectedIndex(0);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) open();
            }
        });
        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(300, 240));
        body.add(scroll, BorderLayout.CENTER);
        JPanel side = new JPanel(new GridLayout(0, 1, 0, 6));
        JButton open = DialogParts.button(msg, "trees.open", "open", this::open);
        side.add(open);
        side.add(DialogParts.button(msg, "trees.new", "new", () -> {
            dispose();
            actions.newTree();
        }));
        side.add(DialogParts.button(msg, "trees.import", "import", () -> {
            dispose();
            actions.importFile();
        }));
        side.add(DialogParts.button(msg, "trees.rename", "rename", () -> {
            StoredTree t = list.getSelectedValue();
            if (t == null) return;
            actions.rename(t).ifPresent(r -> model.set(list.getSelectedIndex(), r));
        }));
        side.add(DialogParts.button(msg, "trees.delete", "delete", () -> {
            StoredTree t = list.getSelectedValue();
            if (t != null && actions.delete(t)) model.removeElement(t);
        }));
        JPanel top = new JPanel(new BorderLayout());
        top.add(side, BorderLayout.NORTH);
        body.add(top, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        add(DialogParts.buttonRow(DialogParts.button(msg, "button.close", "close", this::dispose)), BorderLayout.SOUTH);
        getRootPane().setDefaultButton(open);
        DialogParts.escapeCloses(this);
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<StoredTree> ask(Window owner, Messages msg, List<StoredTree> trees,
                                           MainView.TreeActions actions) {
        TreesDialog d = new TreesDialog(owner, msg, trees, actions);
        d.setVisible(true);
        return Optional.ofNullable(d.chosen);
    }

    private void open() {
        chosen = list.getSelectedValue();
        if (chosen != null) dispose();
    }
}
