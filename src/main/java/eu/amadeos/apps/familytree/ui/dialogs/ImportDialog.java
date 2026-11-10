package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.apps.familytree.service.ImportReport;
import eu.amadeos.apps.familytree.ui.main.MainView;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows what a GEDCOM file holds before it is imported: tiles with the numbers, the notes on what had to be
 * repaired or kept as text, and the choice between a new tree and adding to the open one.
 */
public final class ImportDialog extends JDialog {
    private final Messages msg;
    private final JRadioButton asNew = new JRadioButton();
    private final JRadioButton merge = new JRadioButton();
    private final JTextField name = new JTextField(24);
    private final JCheckBox photos = new JCheckBox();
    private MainView.ImportChoice choice;

    private ImportDialog(Window owner, Messages msg, ImportReport r, String suggestedName, boolean treeOpen) {
        super(owner, msg.get("import.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        setName("importDialog");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        JLabel file = new JLabel(msg.format("import.file", r.fileName(), r.version(),
                r.sourceProgram().isEmpty() ? "–" : r.sourceProgram(), r.charset()));
        file.setName("importFile");
        body.add(file, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.add(tiles(r), BorderLayout.NORTH);
        middle.add(notes(r), BorderLayout.CENTER);
        body.add(middle, BorderLayout.CENTER);
        body.add(options(r, suggestedName, treeOpen), BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        JButton ok = DialogParts.button(msg, "import.ok", "ok", this::accept);
        add(DialogParts.buttonRow(ok, DialogParts.button(msg, "button.cancel", "cancel", this::dispose)),
                BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        DialogParts.escapeCloses(this);
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<MainView.ImportChoice> ask(Window owner, Messages msg, ImportReport report,
                                                     String suggestedName, boolean treeOpen) {
        ImportDialog d = new ImportDialog(owner, msg, report, suggestedName, treeOpen);
        d.setVisible(true);
        return Optional.ofNullable(d.choice);
    }

    private JComponent tiles(ImportReport r) {
        JPanel tiles = new JPanel(new GridLayout(1, 0, 8, 0));
        tiles.add(tile(r.people(), "import.people"));
        tiles.add(tile(r.families(), "import.families"));
        tiles.add(tile(r.sources(), "import.sources"));
        tiles.add(tile(r.places(), "import.places"));
        tiles.add(tile(r.media(), "import.media"));
        return tiles;
    }

    private JComponent tile(int n, String key) {
        JPanel t = new JPanel(new BorderLayout());
        t.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        JLabel number = new JLabel(NumberFormat.getIntegerInstance(msg.locale()).format(n));
        number.setFont(number.getFont().deriveFont(Font.BOLD, number.getFont().getSize2D() * 1.6f));
        number.setName(key);
        JLabel label = new JLabel(msg.get(key));
        AmadeUI.style(label, (l, th) -> l.setForeground(th.textInactive()));
        t.add(number, BorderLayout.CENTER);
        t.add(label, BorderLayout.SOUTH);
        return t;
    }

    /** What the user should know: photos only as links, dates kept as text, living people, repairs. */
    static List<String> notesOf(Messages msg, ImportReport r) {
        List<String> notes = new ArrayList<>();
        if (r.webPhotos() > 0) notes.add(msg.format("import.note.web-photos", r.webPhotos()));
        if (r.missingFiles() > 0) notes.add(msg.format("import.note.missing", r.missingFiles()));
        if (r.unreadableDateCount() > 0) {
            notes.add(msg.format("import.note.dates", r.unreadableDateCount(),
                    String.join("; ", r.unreadableDates())));
        }
        if (r.living() > 0) notes.add(msg.format("import.note.living", r.living()));
        if (r.extensionTags() > 0) notes.add(msg.format("import.note.extensions", r.extensionTags()));
        if (!r.repairs().isEmpty()) notes.add(msg.format("import.note.repairs", r.repairs().size()));
        if (!r.warnings().isEmpty()) notes.add(msg.format("import.note.warnings", r.warnings().size()));
        if (notes.isEmpty()) notes.add(msg.get("import.note.fine"));
        return notes;
    }

    private JComponent notes(ImportReport r) {
        StringBuilder html = new StringBuilder("<html><body style='width:440px'><ul style='margin-left:14px'>");
        for (String n : notesOf(msg, r)) html.append("<li>").append(eu.amadeos.apps.familytree.ui.text.Html.esc(n))
                .append("</li>");
        html.append("</ul></body></html>");
        JLabel l = new JLabel(html.toString());
        l.setName("importNotes");
        return l;
    }

    private JComponent options(ImportReport r, String suggestedName, boolean treeOpen) {
        JPanel p = new JPanel(new java.awt.GridBagLayout());
        Mnemonics.apply(asNew, msg.get("import.as-new"));
        asNew.setName("asNew");
        Mnemonics.apply(merge, msg.get("import.merge"));
        merge.setName("merge");
        merge.setEnabled(treeOpen);
        ButtonGroup g = new ButtonGroup();
        g.add(asNew);
        g.add(merge);
        asNew.setSelected(true);
        name.setText(suggestedName);
        name.setName("treeName");
        asNew.addActionListener(e -> name.setEnabled(true));
        merge.addActionListener(e -> name.setEnabled(false));
        Mnemonics.apply(photos, msg.format("import.photos", r.webPhotos()));
        photos.setName("downloadPhotos");
        photos.setEnabled(r.webPhotos() > 0);
        photos.setSelected(r.webPhotos() > 0);
        p.add(asNew, DialogParts.at(0, 0));
        p.add(name, DialogParts.at(1, 0));
        java.awt.GridBagConstraints c = DialogParts.at(0, 1);
        c.gridwidth = 2;
        p.add(merge, c);
        c = DialogParts.at(0, 2);
        c.gridwidth = 2;
        p.add(photos, c);
        return p;
    }

    private void accept() {
        boolean m = merge.isSelected();
        if (!m && name.getText().isBlank()) {
            name.requestFocusInWindow();
            return;
        }
        choice = new MainView.ImportChoice(m, name.getText().strip(), photos.isSelected());
        dispose();
    }
}
