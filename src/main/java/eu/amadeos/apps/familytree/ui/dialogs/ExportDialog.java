package eu.amadeos.apps.familytree.ui.dialogs;

import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.Window;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The export choices: format, and what to do with people who are probably alive. */
public final class ExportDialog extends JDialog {
    /** A format with its text. */
    record FormatChoice(GedcomExport.Format format, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final JComboBox<FormatChoice> format = new JComboBox<>();
    private final Map<GedcomExport.LivingPeople, JRadioButton> living = new EnumMap<>(GedcomExport.LivingPeople.class);
    private final JTextField livingName = new JTextField(14);
    private GedcomExport.Options options;

    private ExportDialog(Window owner, Messages msg, int privatePeople, int livingPeople) {
        super(owner, msg.get("export.title"), ModalityType.APPLICATION_MODAL);
        setName("exportDialog");
        JPanel form = DialogParts.form();
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        format.setName("format");
        for (GedcomExport.Format f : GedcomExport.Format.values()) format.addItem(new FormatChoice(f, msg.get(f.textKey())));
        DialogParts.row(form, 0, msg, "export.format", format);
        JLabel livingLabel = new JLabel(msg.format("export.living", livingPeople));
        GridBagConstraints c = DialogParts.at(0, 1);
        c.gridwidth = 2;
        c.insets.top = 12;
        form.add(livingLabel, c);
        ButtonGroup g = new ButtonGroup();
        int y = 2;
        for (GedcomExport.LivingPeople l : GedcomExport.LivingPeople.values()) {
            JRadioButton b = Mnemonics.apply(new JRadioButton(), msg.get(l.textKey()));
            b.setName("living-" + l.name());
            g.add(b);
            living.put(l, b);
            c = DialogParts.at(0, y++);
            c.gridwidth = 2;
            c.insets.left = 18;
            form.add(b, c);
        }
        living.get(GedcomExport.LivingPeople.NAME_ONLY).setSelected(true);
        livingName.setText(msg.get("export.living-name.default"));
        livingName.setName("livingName");
        DialogParts.row(form, y++, msg, "export.living-name", livingName);
        if (privatePeople > 0) {
            c = DialogParts.at(0, y);
            c.gridwidth = 2;
            c.insets.top = 12;
            form.add(new JLabel(msg.format("export.private", privatePeople)), c);
        }
        add(form, BorderLayout.CENTER);
        JButton ok = DialogParts.button(msg, "export.ok", "ok", this::accept);
        add(DialogParts.buttonRow(ok, DialogParts.button(msg, "button.cancel", "cancel", this::dispose)),
                BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        DialogParts.escapeCloses(this);
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<GedcomExport.Options> ask(Window owner, Messages msg, int privatePeople, int livingPeople) {
        ExportDialog d = new ExportDialog(owner, msg, privatePeople, livingPeople);
        d.setVisible(true);
        return Optional.ofNullable(d.options);
    }

    private void accept() {
        GedcomExport.LivingPeople l = living.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(GedcomExport.LivingPeople.NAME_ONLY);
        FormatChoice f = (FormatChoice) format.getSelectedItem();
        options = new GedcomExport.Options(f == null ? GedcomExport.Format.GEDCOM_7 : f.format(), l,
                livingName.getText().strip());
        dispose();
    }
}
