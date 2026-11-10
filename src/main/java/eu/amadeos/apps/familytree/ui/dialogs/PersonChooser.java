package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Search;
import eu.amadeos.commons.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

/** Picks one person from a list, with a search field (sound-alike names are found too). */
public final class PersonChooser extends JDialog {
    private final List<Person> people;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Person> model = new DefaultListModel<>();
    private final JList<Person> list = new JList<>(model);
    private Person chosen;

    private PersonChooser(Window owner, Messages msg, String title, List<Person> people) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.people = people;
        setName("personChooser");
        search.setName("search");
        list.setName("people");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                Person p = (Person) value;
                String life = p.lifespan();
                return super.getListCellRendererComponent(l, life.isEmpty() ? p.sortName() : p.sortName() + "  (" + life + ")",
                        index, selected, focus);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) accept();
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) list.requestFocusInWindow();
            }
        });
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        body.add(search, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 320));
        body.add(scroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        JButton ok = DialogParts.button(msg, "button.ok", "ok", this::accept);
        add(DialogParts.buttonRow(ok, DialogParts.button(msg, "button.cancel", "cancel", this::dispose)),
                BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        DialogParts.escapeCloses(this);
        fill();
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<Person> ask(Window owner, Messages msg, String title, List<Person> people) {
        PersonChooser d = new PersonChooser(owner, msg, title, people);
        d.setVisible(true);
        return Optional.ofNullable(d.chosen);
    }

    private void fill() {
        String q = search.getText().strip();
        model.clear();
        for (Person p : people) if (q.isEmpty() || Search.matches(p, q)) model.addElement(p);
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void accept() {
        chosen = list.getSelectedValue();
        if (chosen != null) dispose();
    }
}
