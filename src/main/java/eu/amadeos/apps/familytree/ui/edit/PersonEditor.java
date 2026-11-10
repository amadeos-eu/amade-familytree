package eu.amadeos.apps.familytree.ui.edit;

import eu.amadeos.apps.familytree.domain.Citation;
import eu.amadeos.apps.familytree.domain.DateText;
import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.FactType;
import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.GenDate;
import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.domain.SourceRecord;
import eu.amadeos.apps.familytree.gedcom.GedcomConverter;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.TreeStore;
import eu.amadeos.apps.familytree.ui.text.Texts;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The person editor: names, sex, deceased and private at the top; facts, family, sources, photos and notes in
 * tabs. Changes go straight into the tree; the caller takes them back (undo step) when the user cancels.
 */
public final class PersonEditor extends JDialog {
    private final Texts t;
    private final Person p;
    private final FamilyTree tree;
    private final StoredTree stored;
    private final JTextField given = new JTextField(18);
    private final JTextField surname = new JTextField(14);
    private final JTextField called = new JTextField(10);
    private final JTextField birth = new JTextField(14);
    private final JComboBox<Choice<Sex>> sex = new JComboBox<>();
    private final JCheckBox deceased = new JCheckBox();
    private final JCheckBox privacy = new JCheckBox();
    private final FactModel facts = new FactModel();
    private final JTable factTable;
    private final JComboBox<Choice<FactType>> factType = new JComboBox<>();
    private final JTextField factDate = new JTextField();
    private final JLabel factDateHint = new JLabel(" ");
    private final JTextField factPlace = new JTextField();
    private final JTextField factDetails = new JTextField();
    private final DefaultListModel<Choice<Citation>> sources = new DefaultListModel<>();
    private final DefaultListModel<Choice<String>> media = new DefaultListModel<>();
    private final JTextArea notes = new JTextArea(10, 40);
    private boolean loading;
    private boolean saved;

    /** A value with its text, for lists and combo boxes. */
    public record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private PersonEditor(Window owner, Texts texts, Person person, StoredTree stored, boolean isNew) {
        super(owner, isNew ? texts.get("editor.title.new") : texts.format("editor.title", person.fullName()),
                ModalityType.APPLICATION_MODAL);
        this.t = texts;
        this.p = person;
        this.tree = person.tree();
        this.stored = stored;
        // The table asks the model for its column names at once, so it is made after the texts are there.
        this.factTable = new JTable(facts);
        setName("personEditor");
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        body.add(header(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("editorTabs");
        tabs.addTab(Mnemonics.strip(t.get("editor.tab.facts")), factsTab());
        tabs.addTab(Mnemonics.strip(t.get("editor.tab.family")), familyTab());
        tabs.addTab(Mnemonics.strip(t.get("editor.tab.sources")), sourcesTab());
        tabs.addTab(Mnemonics.strip(t.get("editor.tab.media")), mediaTab());
        tabs.addTab(Mnemonics.strip(t.get("editor.tab.notes")), notesTab());
        body.add(tabs, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        load();
        setSize(Math.round(720 * AmadeUI.fontScale()), Math.round(600 * AmadeUI.fontScale()));
        setLocationRelativeTo(owner);
    }

    /** Shows the editor; true when the user pressed OK. */
    public static boolean edit(Window owner, Texts texts, Person person, StoredTree stored, boolean isNew) {
        PersonEditor e = new PersonEditor(owner, texts, person, stored, isNew);
        e.setVisible(true);
        return e.saved;
    }

    // ---- header ----------------------------------------------------------------------------------------------

    private JComponent header() {
        JPanel h = new JPanel(new GridBagLayout());
        field(h, 0, 0, "editor.given", given, 3);
        field(h, 0, 1, "editor.surname", surname, 1);
        field(h, 2, 1, "editor.birth", birth, 1);
        field(h, 0, 2, "editor.called", called, 1);
        for (Sex s : Sex.values()) sex.addItem(new Choice<>(s, t.sex(s)));
        field(h, 2, 2, "editor.sex", sex, 1);
        given.setName("given");
        surname.setName("surname");
        birth.setName("birthSurname");
        called.setName("called");
        sex.setName("sex");
        Mnemonics.apply(deceased, t.get("editor.deceased"));
        deceased.setName("deceased");
        Mnemonics.apply(privacy, t.get("editor.private"));
        privacy.setName("private");
        privacy.setToolTipText(t.get("editor.private.tip"));
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checks.add(deceased);
        checks.add(javax.swing.Box.createHorizontalStrut(16));
        checks.add(privacy);
        GridBagConstraints c = constraints(1, 3);
        c.gridwidth = 3;
        h.add(checks, c);
        return h;
    }

    private void field(JPanel panel, int x, int y, String key, JComponent field, int width) {
        JLabel label = Mnemonics.apply(new JLabel(), t.get(key));
        label.setLabelFor(field);
        GridBagConstraints c = constraints(x, y);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(label, c);
        c = constraints(x + 1, y);
        c.gridwidth = width;
        panel.add(field, c);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(3, 6, 3, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        return c;
    }

    // ---- facts -----------------------------------------------------------------------------------------------

    private final class FactModel extends AbstractTableModel {
        private List<Fact> rows = List.of();

        void reload() {
            rows = p.facts();
            fireTableDataChanged();
        }

        Fact at(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return t.get(switch (column) {
                case 0 -> "editor.col.fact";
                case 1 -> "editor.col.date";
                case 2 -> "editor.col.place";
                default -> "editor.col.details";
            });
        }

        @Override
        public Object getValueAt(int row, int column) {
            Fact f = rows.get(row);
            return switch (column) {
                case 0 -> t.factName(f);
                case 1 -> t.date(f.date());
                case 2 -> f.place();
                default -> f.tag().equals("EVEN") || f.tag().equals("FACT") ? f.node().text() : f.details();
            };
        }
    }

    private JComponent factsTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        factTable.setName("facts");
        factTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        factTable.setFillsViewportHeight(true);
        factTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        factTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        factTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        factTable.getColumnModel().getColumn(3).setPreferredWidth(160);
        factTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showFact();
        });
        tab.add(new JScrollPane(factTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        for (FactType type : FactType.forPeopleInOrder()) factType.addItem(new Choice<>(type, t.factName(type)));
        factType.setName("factType");
        factDate.setName("factDate");
        factPlace.setName("factPlace");
        factDetails.setName("factDetails");
        factDateHint.setName("factDateHint");
        AmadeUI.style(factDateHint, (l, th) -> l.setForeground(th.textInactive()));
        field(form, 0, 0, "editor.fact.type", factType, 1);
        field(form, 0, 1, "editor.fact.date", factDate, 1);
        GridBagConstraints c = constraints(1, 2);
        c.insets = new Insets(0, 6, 3, 6);
        form.add(factDateHint, c);
        field(form, 0, 3, "editor.fact.place", factPlace, 1);
        field(form, 0, 4, "editor.fact.details", factDetails, 1);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(button("editor.fact.add", "addFact", this::addFact));
        buttons.add(button("editor.fact.remove", "removeFact", this::removeFact));
        c = constraints(1, 5);
        form.add(buttons, c);
        tab.add(form, BorderLayout.SOUTH);

        factType.addActionListener(e -> {
            Fact f = currentFact();
            @SuppressWarnings("unchecked")
            Choice<FactType> choice = (Choice<FactType>) factType.getSelectedItem();
            if (loading || f == null || choice == null || f.tag().equals(choice.value().tag())) return;
            String details = factDetails.getText();
            f.node().setTag(choice.value().tag());
            new Fact(f.node()).setDetails(details);
            factChanged();
        });
        factDate.getDocument().addDocumentListener(changes(() -> {
            GenDate d = DateText.parse(factDate.getText());
            hint(d);
            Fact f = currentFact();
            if (f == null) return;
            f.setDate(d);
            factChanged();
        }));
        factPlace.getDocument().addDocumentListener(changes(() -> {
            Fact f = currentFact();
            if (f == null) return;
            f.setPlace(factPlace.getText());
            factChanged();
        }));
        factDetails.getDocument().addDocumentListener(changes(() -> {
            Fact f = currentFact();
            if (f == null) return;
            f.setDetails(factDetails.getText());
            factChanged();
        }));
        return tab;
    }

    private void hint(GenDate d) {
        if (d.isEmpty()) factDateHint.setText(t.get("editor.date.hint"));
        else if (d.isValid()) factDateHint.setText(t.format("editor.date.understood", t.date(d)));
        else factDateHint.setText(t.get("editor.date.text"));
    }

    private Fact currentFact() {
        int row = factTable.getSelectedRow();
        return row < 0 || row >= facts.getRowCount() ? null : facts.at(row);
    }

    private void showFact() {
        Fact f = currentFact();
        loading = true;
        try {
            boolean on = f != null;
            factDate.setEnabled(on);
            factPlace.setEnabled(on);
            factDetails.setEnabled(on);
            factDate.setText(on ? t.date(f.date()) : "");
            factPlace.setText(on ? f.place() : "");
            factDetails.setText(on ? f.details() : "");
            if (on) {
                for (int i = 0; i < factType.getItemCount(); i++) {
                    if (factType.getItemAt(i).value().tag().equals(f.tag())) factType.setSelectedIndex(i);
                }
                hint(f.date());
            } else {
                factDateHint.setText(" ");
            }
        } finally {
            loading = false;
        }
    }

    private void factChanged() {
        int row = factTable.getSelectedRow();
        if (row >= 0) facts.fireTableRowsUpdated(row, row);
    }

    private void addFact() {
        @SuppressWarnings("unchecked")
        Choice<FactType> choice = (Choice<FactType>) factType.getSelectedItem();
        FactType type = choice == null ? FactType.BIRT : choice.value();
        // Birth and death are only there once; adding one again selects it.
        if (type == FactType.BIRT || type == FactType.DEAT) {
            Optional<Fact> existing = p.fact(type.tag());
            if (existing.isPresent()) {
                select(existing.get().node());
                return;
            }
        }
        GedcomNode node = new GedcomNode(type.tag());
        int at = insertIndex(type);
        p.node().add(at, node);
        facts.reload();
        select(node);
        factDate.requestFocusInWindow();
    }

    /** Birth near the top, death and burial after the other facts, the rest before death. */
    private int insertIndex(FactType type) {
        List<GedcomNode> children = p.node().children();
        int lastFact = -1;
        int firstDeath = -1;
        for (int i = 0; i < children.size(); i++) {
            if (FactType.of(children.get(i).tag()).isEmpty()) continue;
            lastFact = i;
            if (firstDeath < 0 && Person.DEATH_TAGS.contains(children.get(i).tag())) firstDeath = i;
        }
        if (type == FactType.BIRT) {
            for (int i = 0; i < children.size(); i++) {
                if (FactType.of(children.get(i).tag()).isPresent()) return i;
            }
        }
        if (!Person.DEATH_TAGS.contains(type.tag()) && firstDeath >= 0) return firstDeath;
        if (lastFact >= 0) return lastFact + 1;
        int i = 0;
        while (i < children.size() && List.of("NAME", "SEX", "RESN").contains(children.get(i).tag())) i++;
        return i;
    }

    private void select(GedcomNode node) {
        for (int i = 0; i < facts.getRowCount(); i++) {
            if (facts.at(i).node() == node) {
                factTable.setRowSelectionInterval(i, i);
                factTable.scrollRectToVisible(factTable.getCellRect(i, 0, true));
            }
        }
    }

    private void removeFact() {
        Fact f = currentFact();
        if (f == null) return;
        int row = factTable.getSelectedRow();
        p.node().remove(f.node());
        facts.reload();
        if (facts.getRowCount() > 0) {
            int r = Math.min(row, facts.getRowCount() - 1);
            factTable.setRowSelectionInterval(r, r);
        } else {
            showFact();
        }
    }

    // ---- family ----------------------------------------------------------------------------------------------

    private JComponent familyTab() {
        JPanel list = new JPanel(new GridBagLayout());
        list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        int y = 0;
        GridBagConstraints c;
        for (Family f : p.parentFamilies()) {
            String parents = String.join(" & ", f.partners().stream().map(Person::fullName).toList());
            c = constraints(0, y++);
            c.gridwidth = 2;
            list.add(new JLabel(t.format("editor.family.parents", parents.isEmpty() ? "?" : parents)), c);
        }
        List<Family> families = p.families();
        if (families.isEmpty()) {
            c = constraints(0, y++);
            c.gridwidth = 2;
            list.add(new JLabel(t.get("editor.family.none")), c);
        }
        for (Family f : families) {
            String partner = f.partnerOf(p).map(Person::fullName).orElse(t.get("family.unknown-partner"));
            JLabel title = new JLabel(t.format("editor.family.with", partner));
            title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
            c = constraints(0, y++);
            c.gridwidth = 2;
            c.insets = new Insets(10, 6, 3, 6);
            list.add(title, c);
            Fact marriage = f.marriage().orElse(null);
            JTextField date = new JTextField(marriage == null ? "" : t.date(marriage.date()));
            JTextField place = new JTextField(marriage == null ? "" : marriage.place());
            date.setName("marriageDate-" + f.id());
            place.setName("marriagePlace-" + f.id());
            field(list, 0, y++, "editor.family.marriage-date", date, 1);
            field(list, 0, y++, "editor.family.marriage-place", place, 1);
            date.getDocument().addDocumentListener(changes(() -> marriage(f).setDate(DateText.parse(date.getText()))));
            place.getDocument().addDocumentListener(changes(() -> marriage(f).setPlace(place.getText())));
            String children = String.join(", ", f.children().stream().map(Person::shortName).toList());
            c = constraints(1, y++);
            list.add(new JLabel(t.format("editor.family.children", children.isEmpty() ? "–" : children)), c);
        }
        c = constraints(0, y);
        c.weighty = 1;
        list.add(new JLabel(), c);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private static Fact marriage(Family f) {
        return f.marriage().orElseGet(() -> {
            GedcomNode m = new GedcomNode("MARR");
            int at = 0;
            List<GedcomNode> children = f.node().children();
            while (at < children.size() && List.of("HUSB", "WIFE", "RESN").contains(children.get(at).tag())) at++;
            f.node().add(at, m);
            return new Fact(m);
        });
    }

    // ---- sources ---------------------------------------------------------------------------------------------

    private JComponent sourcesTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JList<Choice<Citation>> list = new JList<>(sources);
        list.setName("sources");
        tab.add(new JLabel(t.get("editor.sources.about")), BorderLayout.NORTH);
        tab.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(button("editor.source.add", "addSource", this::addSource));
        buttons.add(button("editor.source.remove", "removeSource", () -> {
            Choice<Citation> c = list.getSelectedValue();
            if (c == null) return;
            p.node().remove(c.value().node());
            loadSources();
        }));
        tab.add(buttons, BorderLayout.SOUTH);
        return tab;
    }

    private void loadSources() {
        sources.clear();
        for (Citation c : p.citations()) sources.addElement(new Choice<>(c, citationText(c)));
    }

    private String citationText(Citation c) {
        String title = c.sourceId() == null ? c.inlineText()
                : tree.source(c.sourceId()).map(SourceRecord::title).orElse(c.sourceId());
        return c.page().isEmpty() ? title : title + " – " + c.page();
    }

    private void addSource() {
        JComboBox<Object> which = new JComboBox<>();
        String fresh = t.get("editor.source.new");
        which.addItem(fresh);
        tree.sources().stream().sorted(java.util.Comparator.comparing(s -> s.title().toLowerCase(Locale.ROOT)))
                .forEach(which::addItem);
        JTextField title = new JTextField(24);
        JTextField page = new JTextField(24);
        JPanel form = new JPanel(new GridBagLayout());
        field(form, 0, 0, "editor.source.which", which, 1);
        field(form, 0, 1, "editor.source.title", title, 1);
        field(form, 0, 2, "editor.source.page", page, 1);
        which.addActionListener(e -> title.setEnabled(which.getSelectedIndex() == 0));
        int answer = JOptionPane.showConfirmDialog(this, form, Mnemonics.strip(t.get("editor.source.add")),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        String sourceId;
        if (which.getSelectedIndex() == 0) {
            if (title.getText().isBlank()) return;
            sourceId = tree.newSource(title.getText().strip()).id();
        } else {
            sourceId = ((SourceRecord) which.getSelectedItem()).id();
        }
        GedcomNode citation = Citation.to(sourceId, page.getText());
        Fact f = currentFact();
        // A source given while a fact is chosen is for that fact; that is how the reader of the tree finds it.
        p.node().add(citation);
        if (f != null && JOptionPane.showConfirmDialog(this, t.format("editor.source.for-fact", t.factName(f)),
                Mnemonics.strip(t.get("editor.source.add")), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            p.node().remove(citation);
            f.node().add(citation);
        }
        loadSources();
    }

    // ---- media -----------------------------------------------------------------------------------------------

    private JComponent mediaTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JList<Choice<String>> list = new JList<>(media);
        list.setName("media");
        tab.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(button("editor.media.add", "addMedia", this::addMedia));
        buttons.add(button("editor.media.portrait", "setPortrait", () -> {
            Choice<String> c = list.getSelectedValue();
            if (c == null) return;
            p.setPortrait(c.value());
            loadMedia();
        }));
        buttons.add(button("editor.media.remove", "removeMedia", () -> {
            Choice<String> c = list.getSelectedValue();
            if (c == null) return;
            for (GedcomNode o : p.node().children("OBJE")) if (c.value().equals(o.pointer())) p.node().remove(o);
            loadMedia();
        }));
        tab.add(buttons, BorderLayout.SOUTH);
        return tab;
    }

    private void loadMedia() {
        media.clear();
        String portrait = p.portrait().map(MediaRecord::id).orElse("");
        for (String id : p.mediaIds()) {
            String label = tree.media(id).map(MediaRecord::label).orElse(id);
            if (id.equals(portrait)) label = t.format("editor.media.is-portrait", label);
            media.addElement(new Choice<>(id, label));
        }
    }

    private void addMedia() {
        if (stored == null) return;
        JFileChooser chooser = new JFileChooser(eu.amadeos.commons.config.XdgDirectories.system().pictures().toFile());
        chooser.setDialogTitle(Mnemonics.strip(t.get("editor.media.add")));
        chooser.setMultiSelectionEnabled(true);
        FileNameExtensionFilter pictures = new FileNameExtensionFilter(t.get("editor.media.filter"),
                "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "pdf");
        chooser.addChoosableFileFilter(pictures);
        chooser.setFileFilter(pictures);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        for (java.io.File file : chooser.getSelectedFiles()) {
            try {
                Path source = file.toPath();
                String value = TreeStore.copyIntoMedia(stored, source);
                String name = source.getFileName().toString();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
                MediaRecord m = tree.newMedia(value, GedcomConverter.mime(ext), stem(name));
                p.node().add(new GedcomNode("OBJE", GedcomNode.pointerTo(m.id())));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, t.format("error.save", e.getMessage()),
                        t.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            }
        }
        loadMedia();
    }

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---- notes, buttons, loading and saving ------------------------------------------------------------------

    private JComponent notesTab() {
        notes.setName("notes");
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(notes);
        JPanel tab = new JPanel(new BorderLayout());
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tab.add(scroll, BorderLayout.CENTER);
        return tab;
    }

    private JComponent buttons() {
        JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton ok = button("button.ok", "ok", this::save);
        JButton cancel = button("button.cancel", "cancel", this::dispose);
        b.add(ok);
        b.add(cancel);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JRootPane.WHEN_IN_FOCUSED_WINDOW);
        return b;
    }

    private JButton button(String key, String name, Runnable action) {
        JButton b = Mnemonics.apply(new JButton(), t.get(key));
        b.setName(name);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void load() {
        loading = true;
        try {
            given.setText(p.given());
            surname.setText(p.surname());
            birth.setText(p.birthSurname());
            String nick = p.primaryName().map(n -> n.called()).orElse("");
            called.setText(nick);
            for (int i = 0; i < sex.getItemCount(); i++) if (sex.getItemAt(i).value() == p.sex()) sex.setSelectedIndex(i);
            deceased.setSelected(p.isDeceased());
            privacy.setSelected(p.isPrivate());
            notes.setText(p.ownNotes());
        } finally {
            loading = false;
        }
        facts.reload();
        if (facts.getRowCount() > 0) factTable.setRowSelectionInterval(0, 0);
        else showFact();
        loadSources();
        loadMedia();
    }

    private void save() {
        p.setNames(given.getText(), surname.getText(), called.getText(), birth.getText());
        @SuppressWarnings("unchecked")
        Choice<Sex> s = (Choice<Sex>) sex.getSelectedItem();
        p.setSex(s == null ? Sex.UNKNOWN : s.value());
        p.setDeceased(deceased.isSelected());
        p.setPrivate(privacy.isSelected());
        p.setNotes(notes.getText());
        // Facts nobody filled in are not kept.
        for (Fact f : new ArrayList<>(p.facts())) {
            if (f.node().children().isEmpty() && f.node().text().isEmpty() && !f.tag().equals("DEAT")) {
                p.node().remove(f.node());
            }
        }
        if (deceased.isSelected()) {
            p.fact("DEAT").filter(d -> d.node().children().isEmpty() && d.node().text().isEmpty())
                    .ifPresent(d -> d.node().setValue("Y"));
        }
        for (Family f : p.families()) {
            f.marriage().filter(m -> m.node().children().isEmpty() && m.node().text().isEmpty())
                    .ifPresent(m -> f.node().remove(m.node()));
        }
        saved = true;
        dispose();
    }

    private DocumentListener changes(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!loading) action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!loading) action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        };
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(560, 460);
    }
}
