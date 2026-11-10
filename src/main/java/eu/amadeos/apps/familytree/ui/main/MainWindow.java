package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.ImportReport;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.Undo;
import eu.amadeos.apps.familytree.ui.chart.Chart;
import eu.amadeos.apps.familytree.ui.chart.ChartColors;
import eu.amadeos.apps.familytree.ui.chart.ChartListener;
import eu.amadeos.apps.familytree.ui.chart.ChartPanel;
import eu.amadeos.apps.familytree.ui.chart.FanChart;
import eu.amadeos.apps.familytree.ui.chart.Portraits;
import eu.amadeos.apps.familytree.ui.chart.TreeLayout;
import eu.amadeos.apps.familytree.ui.dialogs.ExportDialog;
import eu.amadeos.apps.familytree.ui.dialogs.ImportDialog;
import eu.amadeos.apps.familytree.ui.dialogs.PersonChooser;
import eu.amadeos.apps.familytree.ui.dialogs.ProgressDialog;
import eu.amadeos.apps.familytree.ui.dialogs.TreesDialog;
import eu.amadeos.apps.familytree.ui.edit.PersonEditor;
import eu.amadeos.apps.familytree.ui.text.Html;
import eu.amadeos.apps.familytree.ui.text.Pages;
import eu.amadeos.apps.familytree.ui.text.RelationshipText;
import eu.amadeos.apps.familytree.ui.text.Texts;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.print.PaperSize;
import eu.amadeos.commons.print.PrintJobs;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The main window: the people of the tree on the left (search and filter), the chart or page in the middle with
 * the view bar above it, the chosen person on the right, and the status bar. Without a tree it shows the welcome
 * page.
 */
public final class MainWindow extends JFrame implements MainView {
    static final String WELCOME = "welcome";
    static final String TREE = "tree";
    static final String CHART = "chart";
    static final String FAN = "fan";
    static final String PAGE = "page";

    /** A value with its text, for combo boxes. */
    record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Messages msg;
    private final Texts texts;
    private final Pages pages;
    private final LocaleSettings locales;
    private final Executor background;
    private final String version;
    private final Portraits portraits;
    private final CardLayout outerCards = new CardLayout();
    private final JPanel outer = new JPanel(outerCards);
    private final CardLayout viewCards = new CardLayout();
    private final JPanel viewArea = new JPanel(viewCards);
    private final JTextField search = new JTextField();
    private final JComboBox<Choice<ListFilter>> filter = new JComboBox<>();
    private final DefaultListModel<Person> listModel = new DefaultListModel<>();
    private final JList<Person> list = new JList<>(listModel);
    private final JLabel count = new JLabel(" ");
    private final ChartPanel chart;
    private final JScrollPane chartScroll;
    private final FanChart fan;
    private final JEditorPane page = new JEditorPane();
    private final JComboBox<Choice<Integer>> sheetFamily = new JComboBox<>();
    private final JLabel portrait = new JLabel();
    private final JLabel detailsName = new JLabel(" ");
    private final JEditorPane details = new JEditorPane();
    private final Map<ViewKind, JToggleButton> viewButtons = new EnumMap<>(ViewKind.class);
    private final JLabel generationsLabel = new JLabel(" ");
    private final JButton fewer = new JButton("−");
    private final JButton more = new JButton("+");
    private final JButton backButton = new JButton("◀");
    private final JButton forwardButton = new JButton("▶");
    private final JButton homeButton = new JButton("⌂");
    private final JButton editButton = new JButton();
    private final JButton addButton = new JButton();
    private final JLabel status = new JLabel(" ");
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private StoredTree stored;
    private FamilyTree tree;
    private Person root;
    private Person selected;
    private ViewKind view = ViewKind.HOURGLASS;
    private int generations = 2;
    /** What the chart was last built for, so that a new selection does not rebuild (and scroll) it. */
    private String builtFor = "";
    private boolean updating;
    /** The id of the home person, marked in the list; null when there is none. */
    private String homeId;

    public MainWindow(Messages msg, LocaleSettings locales, Executor background, String version) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.texts = new Texts(msg);
        this.pages = new Pages(texts);
        this.locales = locales;
        this.background = background;
        this.version = version;
        this.portraits = new Portraits(background);
        this.chart = new ChartPanel(portraits, texts::nee);
        this.chartScroll = new JScrollPane(chart);
        this.fan = new FanChart(msg.get("fan.empty"));
        setName("mainWindow");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-familytree.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales, new MainMenuBar.WindowActions() {
            @Override
            public void find() {
                search.requestFocusInWindow();
                search.selectAll();
            }

            @Override
            public void zoom(int direction) {
                zoomChart(direction);
            }
        });
        setJMenuBar(menuBar);

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centre(), detailsSide());
        right.setResizeWeight(1);
        right.setBorder(null);
        JComponent people = listSide();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, people, right);
        split.setBorder(null);
        split.setDividerLocation(people.getPreferredSize().width);
        JPanel treePage = new JPanel(new BorderLayout());
        treePage.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        treePage.add(split, BorderLayout.CENTER);
        outer.add(welcome(), WELCOME);
        outer.add(treePage, TREE);
        add(outer, BorderLayout.CENTER);
        status.setName("status");
        status.setBorder(BorderFactory.createEmptyBorder(3, 12, 5, 12));
        add(status, BorderLayout.SOUTH);

        ChartListener listener = new ChartListener() {
            @Override
            public void select(Person p) {
                presenter.select(p);
            }

            @Override
            public void centre(Person p) {
                presenter.centre(p);
            }

            @Override
            public void edit(Person p) {
                presenter.select(p);
                presenter.editSelected();
            }

            @Override
            public void popup(Person p, Component on, int x, int y) {
                personMenu().show(on, x, y);
            }
        };
        chart.setListener(listener);
        fan.setListener(listener);
        portraits.onLoaded(() -> {
            chart.repaint();
            showDetails();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        setSize(Math.round(1240 * AmadeUI.fontScale()), Math.round(780 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
    }

    // ---- building the window ---------------------------------------------------------------------------------

    private JComponent listSide() {
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        search.setName("search");
        search.setToolTipText(msg.get("list.search.tip"));
        search.getDocument().addDocumentListener(changes(this::fillList));
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) list.requestFocusInWindow();
                if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
                    presenter.centre(list.getSelectedValue());
                }
            }
        });
        filter.setName("filter");
        for (ListFilter f : ListFilter.values()) filter.addItem(new Choice<>(f, msg.get(f.textKey())));
        filter.addActionListener(e -> fillList());
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 6));
        top.add(search);
        top.add(filter);
        side.add(top, BorderLayout.NORTH);

        list.setName("people");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel,
                                                          boolean focus) {
                Person p = (Person) value;
                String life = p.lifespan();
                super.getListCellRendererComponent(l, life.isEmpty() ? p.sortName() : p.sortName() + "  " + life,
                        index, sel, focus);
                setIcon(AmadeUI.colorDot(ChartColors.current().sex(p.sex()), 9));
                if (homeId != null && homeId.equals(p.id())) {
                    // The home person ("me") has a frame, so the start of the relationship view is easy to find.
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ChartColors.current().accent(), 2, true),
                            BorderFactory.createEmptyBorder(0, 2, 0, 2)));
                    setFont(getFont().deriveFont(Font.BOLD));
                    setToolTipText(msg.get("list.home"));
                } else {
                    setToolTipText(null);
                }
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                presenter.select(list.getSelectedValue());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) presenter.centre(list.getSelectedValue());
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
                    presenter.centre(list.getSelectedValue());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        side.add(scroll, BorderLayout.CENTER);
        // The split pane gives the list the width it asks for; the field and combo box alone would ask for little.
        side.setPreferredSize(new Dimension(Math.round(260 * AmadeUI.fontScale()), 400));
        side.setMinimumSize(new Dimension(160, 200));
        count.setName("count");
        AmadeUI.style(count, (l, t) -> l.setForeground(t.textInactive()));
        side.add(count, BorderLayout.SOUTH);
        return side;
    }

    private JComponent centre() {
        JPanel centre = new JPanel(new BorderLayout(0, 6));
        centre.add(viewBar(), BorderLayout.NORTH);
        chartScroll.setName("chartScroll");
        chartScroll.getVerticalScrollBar().setUnitIncrement(24);
        chartScroll.getHorizontalScrollBar().setUnitIncrement(24);
        chart.setName("chart");
        fan.setName("fan");
        page.setName("page");
        page.setEditable(false);
        page.setContentType("text/html");
        page.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) link(e.getDescription(), true);
        });
        JPanel pageArea = new JPanel(new BorderLayout(0, 4));
        sheetFamily.setName("sheetFamily");
        sheetFamily.addActionListener(e -> {
            if (!updating) showPage();
        });
        JPanel sheetBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sheetBar.add(sheetFamily);
        pageArea.add(sheetBar, BorderLayout.NORTH);
        pageArea.add(new JScrollPane(page), BorderLayout.CENTER);
        viewArea.add(chartScroll, CHART);
        viewArea.add(fan, FAN);
        viewArea.add(pageArea, PAGE);
        centre.add(viewArea, BorderLayout.CENTER);
        return centre;
    }

    private JComponent viewBar() {
        JPanel bar = new JPanel(new BorderLayout());
        JPanel views = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (ViewKind kind : ViewKind.values()) {
            JToggleButton b = new JToggleButton(Mnemonics.strip(msg.get(kind.textKey())));
            b.setName("view-" + kind.name());
            b.setFocusable(false);
            b.setMargin(new Insets(2, 8, 2, 8));
            b.addActionListener(e -> presenter.showView(kind));
            group.add(b);
            viewButtons.put(kind, b);
            views.add(b);
        }
        bar.add(views, BorderLayout.CENTER);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        fewer.setName("fewer");
        fewer.setToolTipText(Mnemonics.strip(msg.get("menu.view.fewer")));
        fewer.addActionListener(e -> presenter.fewerGenerations());
        more.setName("more");
        more.setToolTipText(Mnemonics.strip(msg.get("menu.view.more")));
        more.addActionListener(e -> presenter.moreGenerations());
        generationsLabel.setName("generations");
        backButton.setName("back");
        backButton.setToolTipText(Mnemonics.strip(msg.get("menu.view.back")));
        backButton.addActionListener(e -> presenter.back());
        forwardButton.setName("forward");
        forwardButton.setToolTipText(Mnemonics.strip(msg.get("menu.view.forward")));
        forwardButton.addActionListener(e -> presenter.forward());
        homeButton.setName("home");
        homeButton.setToolTipText(Mnemonics.strip(msg.get("menu.view.home")));
        homeButton.addActionListener(e -> presenter.goHome());
        for (JButton b : List.of(fewer, more, backButton, forwardButton, homeButton)) {
            b.setFocusable(false);
            b.setMargin(new Insets(2, 8, 2, 8));
        }
        nav.add(fewer);
        nav.add(generationsLabel);
        nav.add(more);
        nav.add(javax.swing.Box.createHorizontalStrut(12));
        nav.add(backButton);
        nav.add(forwardButton);
        nav.add(homeButton);
        bar.add(nav, BorderLayout.EAST);
        return bar;
    }

    private JComponent detailsSide() {
        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        side.setPreferredSize(new Dimension(Math.round(290 * AmadeUI.fontScale()), 400));
        JPanel head = new JPanel(new BorderLayout(10, 0));
        portrait.setName("portrait");
        portrait.setPreferredSize(new Dimension(Portraits.SIZE, Portraits.SIZE));
        portrait.setHorizontalAlignment(SwingConstants.CENTER);
        detailsName.setName("detailsName");
        detailsName.setFont(detailsName.getFont().deriveFont(Font.BOLD, detailsName.getFont().getSize2D() * 1.3f));
        detailsName.setVerticalAlignment(SwingConstants.TOP);
        head.add(portrait, BorderLayout.WEST);
        head.add(detailsName, BorderLayout.CENTER);
        side.add(head, BorderLayout.NORTH);
        details.setName("details");
        details.setEditable(false);
        details.setContentType("text/html");
        details.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) link(e.getDescription(), false);
        });
        side.add(new JScrollPane(details), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        Mnemonics.apply(editButton, msg.get("details.edit"));
        editButton.setName("editButton");
        editButton.addActionListener(e -> presenter.editSelected());
        Mnemonics.apply(addButton, msg.get("details.add"));
        addButton.setName("addButton");
        addButton.addActionListener(e -> addMenu().show(addButton, 0, addButton.getHeight()));
        buttons.add(editButton);
        buttons.add(addButton);
        side.add(buttons, BorderLayout.SOUTH);
        return side;
    }

    private JComponent welcome() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setName("welcome");
        JPanel box = new JPanel(new BorderLayout(0, 14));
        JLabel title = new JLabel(msg.get("welcome.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * 1.8f));
        box.add(title, BorderLayout.NORTH);
        JLabel text = new JLabel("<html><body style='width:460px'>" + Html.esc(msg.get("welcome.text"))
                + "<br><br>" + Html.esc(msg.get("welcome.gedcom")) + "</body></html>");
        box.add(text, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton imp = Mnemonics.apply(new JButton(), msg.get("welcome.import"));
        imp.setName("welcomeImport");
        imp.addActionListener(e -> presenter.importGedcom());
        JButton fresh = Mnemonics.apply(new JButton(), msg.get("welcome.new"));
        fresh.setName("welcomeNew");
        fresh.addActionListener(e -> presenter.newTree());
        JButton open = Mnemonics.apply(new JButton(), msg.get("welcome.open"));
        open.setName("welcomeOpen");
        open.addActionListener(e -> presenter.openTreeDialog());
        buttons.add(imp);
        buttons.add(javax.swing.Box.createHorizontalStrut(8));
        buttons.add(fresh);
        buttons.add(javax.swing.Box.createHorizontalStrut(8));
        buttons.add(open);
        box.add(buttons, BorderLayout.SOUTH);
        page.add(box, new GridBagConstraints());
        return page;
    }

    private JPopupMenu addMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (Relative r : Relative.values()) {
            JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(r.textKey()));
            item.addActionListener(e -> presenter.addRelative(r));
            menu.add(item);
            if (r == Relative.MOTHER || r == Relative.PARTNER || r == Relative.DAUGHTER) menu.addSeparator();
        }
        JMenuItem connect = Mnemonics.apply(new JMenuItem(), msg.get("menu.person.connect"));
        connect.addActionListener(e -> presenter.connectExisting());
        menu.add(connect);
        return menu;
    }

    private JPopupMenu personMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(popupItem("menu.edit.person", presenter::editSelected));
        menu.add(popupItem("menu.view.centre", presenter::centreSelected));
        menu.addSeparator();
        for (Relative r : Relative.values()) menu.add(popupItem(r.textKey(), () -> presenter.addRelative(r)));
        menu.add(popupItem("menu.person.connect", presenter::connectExisting));
        menu.addSeparator();
        menu.add(popupItem("menu.person.home", presenter::setHome));
        menu.add(popupItem("menu.person.relationship", presenter::relationship));
        menu.addSeparator();
        menu.add(popupItem("menu.edit.delete", presenter::deleteSelected));
        return menu;
    }

    private JMenuItem popupItem(String key, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.addActionListener(e -> action.run());
        return item;
    }

    // ---- showing ---------------------------------------------------------------------------------------------

    @Override
    public void showWelcome() {
        stored = null;
        tree = null;
        root = null;
        selected = null;
        builtFor = "";
        setTitle(msg.get("window.title"));
        outerCards.show(outer, WELCOME);
    }

    @Override
    public void showTree(StoredTree t, FamilyTree loaded) {
        stored = t;
        tree = loaded;
        builtFor = "";
        portraits.setTree(t);
        setTitle(msg.format("window.title.tree", t.name()));
        outerCards.show(outer, TREE);
        fillList();
    }

    @Override
    public void refresh() {
        builtFor = "";
        fillList();
        rebuild();
        showDetails();
    }

    @Override
    public void showPerson(Person newRoot, Person newSelected) {
        root = newRoot;
        selected = newSelected;
        rebuild();
        chart.setSelected(selected);
        fan.setSelected(selected);
        updating = true;
        try {
            if (filterValue() != ListFilter.ALL && filterValue() != ListFilter.WITHOUT_PARENTS
                    && filterValue() != ListFilter.LIVING && filterValue() != ListFilter.UNREADABLE_DATES) {
                fillList();
            }
            if (selected != null) {
                list.setSelectedValue(selected, true);
            } else {
                list.clearSelection();
            }
        } finally {
            updating = false;
        }
        showDetails();
    }

    @Override
    public void showHome(String personId) {
        homeId = personId;
        list.repaint();
    }

    @Override
    public void showView(ViewKind kind, int gens) {
        view = kind;
        generations = gens;
        JToggleButton b = viewButtons.get(kind);
        if (b != null) b.setSelected(true);
        if (menuBar != null) menuBar.showView(kind);
        boolean hasGens = kind.hasGenerations();
        fewer.setVisible(hasGens);
        more.setVisible(hasGens);
        generationsLabel.setVisible(hasGens);
        generationsLabel.setText(msg.format("view.generations", gens));
        builtFor = "";
        rebuild();
    }

    /** Builds the chart or page again when the person in the middle, the view or the tree changed. */
    private void rebuild() {
        if (tree == null || root == null) {
            chart.setChart(null);
            fan.setRoot(null, generations);
            page.setText("");
            return;
        }
        String key = view + "/" + generations + "/" + root.id() + "/" + tree.revision()
                + (view == ViewKind.RELATIONSHIP && selected != null ? "/" + selected.id() : "");
        if (key.equals(builtFor)) return;
        builtFor = key;
        switch (view) {
            case HOURGLASS, ANCESTORS, DESCENDANTS -> {
                Chart c = switch (view) {
                    case ANCESTORS -> TreeLayout.ancestors(root, generations);
                    case DESCENDANTS -> TreeLayout.descendants(root, generations);
                    default -> TreeLayout.hourglass(root, generations, generations, this::rowTitle);
                };
                chart.setChart(c);
                viewCards.show(viewArea, CHART);
                javax.swing.SwingUtilities.invokeLater(() -> chart.reveal(root));
            }
            case RELATIONSHIP -> {
                Person target = selected != null ? selected : root;
                RelationshipText words = new RelationshipText(msg);
                chart.setChart(TreeLayout.relationship(root, target,
                        p -> words.of(Relationship.between(root, p)),
                        msg.format("rel.view.title", root.fullName(), target.fullName(),
                                words.of(Relationship.between(root, target)))));
                viewCards.show(viewArea, CHART);
                // The whole way, from the start to the destination, is on screen.
                javax.swing.SwingUtilities.invokeLater(chart::showAll);
            }
            case FAN -> {
                fan.setRoot(root, generations);
                viewCards.show(viewArea, FAN);
            }
            case FAMILY, TIMELINE -> {
                updating = true;
                try {
                    sheetFamily.removeAllItems();
                    if (view == ViewKind.FAMILY) {
                        List<Family> families = Pages.sheetFamilies(root);
                        for (int i = 0; i < families.size(); i++) {
                            Family f = families.get(i);
                            String label = root.families().contains(f)
                                    ? msg.format("family.with", f.partnerOf(root).map(Person::fullName)
                                    .orElse(msg.get("family.unknown-partner")))
                                    : msg.get("family.as-child");
                            sheetFamily.addItem(new Choice<>(i, label));
                        }
                    }
                    sheetFamily.setVisible(sheetFamily.getItemCount() > 1);
                } finally {
                    updating = false;
                }
                showPage();
                viewCards.show(viewArea, PAGE);
            }
        }
    }

    private String rowTitle(int generation) {
        if (generation == 0) return null;
        int g = Math.abs(generation);
        String key = generation < 0 ? "row.up." : "row.down.";
        return g <= 3 ? msg.get(key + g) : msg.format(key + "many", g);
    }

    private void showPage() {
        if (root == null) return;
        ChartColors c = ChartColors.current();
        @SuppressWarnings("unchecked")
        Choice<Integer> fam = (Choice<Integer>) sheetFamily.getSelectedItem();
        String html = view == ViewKind.TIMELINE ? pages.timeline(root, c)
                : pages.familySheet(root, fam == null ? 0 : fam.value(), c);
        page.setText(html);
        page.setCaretPosition(0);
    }

    private void showDetails() {
        Person p = selected;
        if (p == null || tree == null) {
            detailsName.setText(" ");
            details.setText("");
            portrait.setIcon(null);
            return;
        }
        String nee = p.birthSurname();
        detailsName.setText("<html>" + Html.esc(p.fullName()) + (nee.isEmpty() ? ""
                : "<br><span style='font-weight:normal'>" + Html.esc(texts.nee(nee)) + "</span>") + "</html>");
        details.setText(pages.summary(p, ChartColors.current()));
        details.setCaretPosition(0);
        Optional<Image> img = portraits.of(p);
        portrait.setIcon(img.map(i -> new ImageIcon(i.getScaledInstance(Portraits.SIZE, Portraits.SIZE,
                Image.SCALE_SMOOTH))).orElse(null));
        portrait.setVisible(img.isPresent());
    }

    private void link(String href, boolean centre) {
        if (href == null || tree == null || !href.startsWith(Html.PERSON)) return;
        tree.person(href.substring(Html.PERSON.length())).ifPresent(p -> {
            if (centre) presenter.centre(p);
            else presenter.select(p);
        });
    }

    private ListFilter filterValue() {
        @SuppressWarnings("unchecked")
        Choice<ListFilter> c = (Choice<ListFilter>) filter.getSelectedItem();
        return c == null ? ListFilter.ALL : c.value();
    }

    private void fillList() {
        boolean was = updating;
        updating = true;
        try {
            List<Person> people = PersonList.of(tree, filterValue(), search.getText(), root, Living.now());
            listModel.clear();
            listModel.addAll(people);
            if (selected != null && people.contains(selected)) list.setSelectedValue(selected, true);
            count.setText(tree == null ? " " : msg.format("list.count", people.size(), tree.count("INDI")));
        } finally {
            updating = was;
        }
    }

    private void zoomChart(int direction) {
        if (direction == 0) chart.setZoom(1);
        else chart.setZoom(chart.zoom() * (direction > 0 ? 1.25 : 0.8));
    }

    @Override
    public void updateActions(boolean hasTree, boolean hasSelection, String undoLabel, String redoLabel,
                              boolean back, boolean forward) {
        if (menuBar != null) menuBar.update(hasTree, hasSelection, undoLabel, redoLabel, back, forward, view);
        backButton.setEnabled(back);
        forwardButton.setEnabled(forward);
        homeButton.setEnabled(hasTree);
        editButton.setEnabled(hasSelection);
        addButton.setEnabled(hasTree);
        fewer.setEnabled(hasTree);
        more.setEnabled(hasTree);
        for (JToggleButton b : viewButtons.values()) b.setEnabled(hasTree);
    }

    @Override
    public void showStatus(String text) {
        status.setText(text == null || text.isEmpty() ? " " : text);
    }

    @Override
    public void showError(String text) {
        JOptionPane.showMessageDialog(this, text, msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean confirm(String text) {
        Object[] options = {Mnemonics.strip(msg.get("button.yes")), Mnemonics.strip(msg.get("button.cancel"))};
        return JOptionPane.showOptionDialog(this, text, msg.get("window.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    // ---- files -----------------------------------------------------------------------------------------------

    private static File existing(Path p) {
        return p != null && Files.isDirectory(p) ? p.toFile() : null;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        JFileChooser chooser = new JFileChooser(existing(XdgDirectories.system().downloads()));
        chooser.setDialogTitle(Mnemonics.strip(msg.get("menu.file.import")));
        FileNameExtensionFilter gedcom = new FileNameExtensionFilter(msg.get("chooser.gedcom"), "ged", "gdz", "zip");
        chooser.addChoosableFileFilter(gedcom);
        chooser.setFileFilter(gedcom);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.ofNullable(chooser.getSelectedFile()).map(File::toPath);
    }

    @Override
    public Optional<Path> chooseExportFile(String suggestedName, GedcomExport.Format format) {
        return chooseSaveFile(XdgDirectories.system().documents(), suggestedName, format.extension(),
                msg.get(format.textKey()));
    }

    @Override
    public Optional<Path> choosePictureFile(String suggestedName) {
        String name = suggestedName.endsWith(".png") ? suggestedName.substring(0, suggestedName.length() - 4)
                : suggestedName;
        return chooseSaveFile(XdgDirectories.system().pictures(), name, "png", msg.get("chooser.png"));
    }

    private Optional<Path> chooseSaveFile(Path folder, String name, String extension, String description) {
        JFileChooser chooser = new JFileChooser(existing(folder));
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), safe(name) + "." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        File f = chooser.getSelectedFile();
        if (f == null) return Optional.empty();
        if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            f = new File(f.getParentFile(), f.getName() + "." + extension);
        }
        if (f.exists() && !confirm(msg.format("chooser.replace", f.getName()))) return Optional.empty();
        return Optional.of(f.toPath());
    }

    private static String safe(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public Optional<Path> chooseFolder(String title) {
        JFileChooser chooser = new JFileChooser(existing(XdgDirectories.system().pictures()));
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.ofNullable(chooser.getSelectedFile()).map(File::toPath);
    }

    @Override
    public Optional<String> askName(String title, String label, String initial) {
        Object answer = JOptionPane.showInputDialog(this, label, title, JOptionPane.QUESTION_MESSAGE, null, null,
                initial);
        return Optional.ofNullable(answer).map(Object::toString).map(String::strip);
    }

    @Override
    public Optional<ImportChoice> askImport(ImportReport report, String suggestedName, boolean treeOpen) {
        return ImportDialog.ask(this, msg, report, suggestedName, treeOpen);
    }

    @Override
    public Optional<GedcomExport.Options> askExport(int privatePeople, int livingPeople) {
        return ExportDialog.ask(this, msg, privatePeople, livingPeople);
    }

    @Override
    public Optional<StoredTree> chooseTree(List<StoredTree> trees, TreeActions actions) {
        return TreesDialog.ask(this, msg, trees, actions);
    }

    // ---- people ----------------------------------------------------------------------------------------------

    @Override
    public boolean editPerson(Person p, Undo.Step step, boolean isNew) {
        return PersonEditor.edit(this, texts, p, stored, isNew);
    }

    @Override
    public Optional<Person> choosePerson(String title, List<Person> people) {
        return PersonChooser.ask(this, msg, title, people);
    }

    @Override
    public Optional<Connection> chooseConnection(Person p) {
        Connection[] values = Connection.values();
        Object[] options = new Object[values.length + 1];
        for (int i = 0; i < values.length; i++) options[i] = msg.get("connect.button." + values[i].name().toLowerCase(java.util.Locale.ROOT));
        options[values.length] = Mnemonics.strip(msg.get("button.cancel"));
        int answer = JOptionPane.showOptionDialog(this, msg.format("connect.question", p.fullName()),
                Mnemonics.strip(msg.get("menu.person.connect")), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return answer >= 0 && answer < values.length ? Optional.of(values[answer]) : Optional.empty();
    }

    @Override
    public void showRelationship(Person to, Person other, Relationship r) {
        String word = new RelationshipText(msg).of(r);
        StringBuilder html = new StringBuilder("<html><body style='width:380px'>");
        html.append("<p>").append(Html.esc(msg.format("rel.question", other.fullName(), to.fullName()))).append("</p>");
        html.append("<h2>").append(Html.esc(word)).append("</h2>");
        if (r.path() != null && r.path().size() > 1) {
            html.append("<p>").append(Html.esc(msg.get("rel.path"))).append("<br>");
            html.append(Html.esc(String.join(" → ", r.path().stream().map(Person::shortName).toList())));
            html.append("</p>");
        }
        html.append("</body></html>");
        JOptionPane.showMessageDialog(this, html.toString(), Mnemonics.strip(msg.get("menu.person.relationship")),
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public <T> void runWithProgress(String title, Task<T> task, Consumer<T> done, Consumer<Exception> failed) {
        ProgressDialog.run(this, msg, title, background, task, done, failed);
    }

    // ---- printing and pictures -------------------------------------------------------------------------------

    @Override
    public void print() {
        try {
            String job = stored == null ? msg.get("window.title") : stored.name();
            if (view == ViewKind.FAMILY || view == ViewKind.TIMELINE) {
                page.print();
                return;
            }
            PaperSize paper = PaperSize.forLocale(msg.locale());
            Book book = new Book();
            book.append(view == ViewKind.FAN ? fan : chart, PrintJobs.pageFormat(paper, true, 28));
            PrintJobs.printWithDialog(book, paper, job);
        } catch (PrinterException e) {
            showError(msg.format("error.print", e.getMessage()));
        }
    }

    @Override
    public void saveChartPicture(Path file) throws IOException {
        BufferedImage img = switch (view) {
            case FAN -> fan.image(2);
            case FAMILY, TIMELINE -> pageImage();
            default -> chart.image(2);
        };
        if (!ImageIO.write(img, "png", file.toFile())) throw new IOException("PNG");
    }

    private BufferedImage pageImage() {
        Dimension d = page.getPreferredSize();
        int w = Math.max(400, page.getWidth());
        int h = Math.max(200, d.height);
        BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(2, 2);
        page.paint(g);
        g.dispose();
        return img;
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-familytree " + Html.esc(version) + "</h2>"
                        + "<p>" + Html.esc(msg.get("dialog.about.text")) + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        dispose();
    }

    // ---- for tests -------------------------------------------------------------------------------------------

    ChartPanel chartPanel() {
        return chart;
    }

    FanChart fanChart() {
        return fan;
    }

    JEditorPane pagePane() {
        return page;
    }

    JList<Person> personList() {
        return list;
    }

    MainMenuBar menus() {
        return menuBar;
    }

    String statusText() {
        return status.getText();
    }

    private DocumentListener changes(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        };
    }
}
